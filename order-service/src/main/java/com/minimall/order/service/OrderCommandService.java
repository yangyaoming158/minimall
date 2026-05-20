package com.minimall.order.service;

import com.minimall.common.auth.context.UserContext;
import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.order.client.inventory.InventoryClient;
import com.minimall.order.client.inventory.InventoryDeductRequest;
import com.minimall.order.client.product.ProductSnapshot;
import com.minimall.order.client.product.ProductValidationService;
import com.minimall.order.domain.Order;
import com.minimall.order.domain.OrderStateMachine;
import com.minimall.order.domain.OrderStatus;
import com.minimall.order.dto.CancelOrderResponse;
import com.minimall.order.dto.CreateOrderRequest;
import com.minimall.order.dto.CreateOrderResponse;
import com.minimall.order.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderCommandService {

    private static final DateTimeFormatter ORDER_NO_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final String IDEMPOTENCY_LOCK_PREFIX = "order:create:";
    private static final String IDEMPOTENCY_LOCK_VALUE = "PROCESSING";
    private static final String ORDER_CREATION_IN_PROGRESS_MESSAGE = "Order creation is in progress, please retry";
    private static final String ORDER_IDEMPOTENCY_CHECK_FAILED_MESSAGE = "Order idempotency check failed";
    private static final String ORDER_NOT_FOUND_MESSAGE = "Order not found";
    private static final String ORDER_NOT_CANCELLABLE_MESSAGE_PREFIX = "Order current status cannot be cancelled: ";

    private final ProductValidationService productValidationService;
    private final InventoryClient inventoryClient;
    private final OrderRepository orderRepository;
    private final StringRedisTemplate redisTemplate;
    private final long paymentExpireSeconds;
    private final Duration idempotencyLockTtl;
    private final OrderStateMachine orderStateMachine = new OrderStateMachine();

    public OrderCommandService(
            ProductValidationService productValidationService,
            InventoryClient inventoryClient,
            OrderRepository orderRepository,
            StringRedisTemplate redisTemplate,
            @Value("${minimall.order.payment-expire-seconds:900}") long paymentExpireSeconds,
            @Value("${minimall.order.idempotency.lock-ttl-seconds:30}") long idempotencyLockTtlSeconds) {
        this.productValidationService = productValidationService;
        this.inventoryClient = inventoryClient;
        this.orderRepository = orderRepository;
        this.redisTemplate = redisTemplate;
        this.paymentExpireSeconds = paymentExpireSeconds;
        this.idempotencyLockTtl = Duration.ofSeconds(Math.max(1, idempotencyLockTtlSeconds));
    }

    @Transactional
    public CancelOrderResponse cancel(String orderNo, UserContext userContext) {
        Order order = orderRepository.findByOrderNoAndUserId(orderNo, userContext.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, ORDER_NOT_FOUND_MESSAGE));

        if (order.getStatus() == OrderStatus.CANCELLED) {
            return toCancelOrderResponse(order);
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    ORDER_NOT_CANCELLABLE_MESSAGE_PREFIX + order.getStatus());
        }

        inventoryClient.release(new InventoryDeductRequest(
                order.getOrderNo(), order.getProductId(), order.getQuantity()));
        orderStateMachine.transition(order, OrderStatus.CANCELLED, LocalDateTime.now());
        return toCancelOrderResponse(orderRepository.saveAndFlush(order));
    }

    @Transactional
    public CreateOrderResponse create(CreateOrderRequest request, UserContext userContext) {
        Optional<Order> existingOrder = findExistingOrder(userContext, request.idempotencyKey());
        if (existingOrder.isPresent()) {
            return toCreateOrderResponse(existingOrder.get());
        }

        String lockKey = idempotencyLockKey(userContext.getUserId(), request.idempotencyKey());
        boolean lockAcquired = acquireIdempotencyLock(lockKey);
        if (!lockAcquired) {
            return findExistingOrder(userContext, request.idempotencyKey())
                    .map(this::toCreateOrderResponse)
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.CONFLICT, ORDER_CREATION_IN_PROGRESS_MESSAGE));
        }

        boolean completed = false;
        try {
            CreateOrderResponse response = createNewOrder(request, userContext);
            completed = true;
            return response;
        } catch (DataIntegrityViolationException exception) {
            Optional<Order> replayOrder = findExistingOrder(userContext, request.idempotencyKey());
            if (replayOrder.isPresent()) {
                completed = true;
                return toCreateOrderResponse(replayOrder.get());
            }
            throw exception;
        } finally {
            if (!completed) {
                releaseIdempotencyLock(lockKey);
            }
        }
    }

    private CreateOrderResponse createNewOrder(CreateOrderRequest request, UserContext userContext) {
        ProductSnapshot product = productValidationService.validateSellable(request.productId());
        String orderNo = nextOrderNo();
        inventoryClient.deduct(new InventoryDeductRequest(orderNo, request.productId(), request.quantity()));

        BigDecimal totalAmount = product.price().multiply(BigDecimal.valueOf(request.quantity()));
        LocalDateTime expireAt = LocalDateTime.now().plusSeconds(paymentExpireSeconds);
        Order order = orderRepository.saveAndFlush(new Order(
                orderNo,
                userContext.getUserId(),
                userContext.getUsername(),
                product.productId(),
                product.name(),
                request.quantity(),
                product.price(),
                totalAmount,
                request.idempotencyKey(),
                expireAt));

        return toCreateOrderResponse(order);
    }

    private Optional<Order> findExistingOrder(UserContext userContext, String idempotencyKey) {
        return orderRepository.findByUserIdAndIdempotencyKey(userContext.getUserId(), idempotencyKey);
    }

    private boolean acquireIdempotencyLock(String lockKey) {
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(
                    lockKey, IDEMPOTENCY_LOCK_VALUE, idempotencyLockTtl));
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, ORDER_IDEMPOTENCY_CHECK_FAILED_MESSAGE, exception);
        }
    }

    private void releaseIdempotencyLock(String lockKey) {
        try {
            redisTemplate.delete(lockKey);
        } catch (RuntimeException ignored) {
            // The lock has a TTL; failure to delete should not mask the original business result.
        }
    }

    private CreateOrderResponse toCreateOrderResponse(Order order) {
        return new CreateOrderResponse(
                order.getOrderNo(),
                order.getUserId(),
                order.getStatus(),
                order.getExpireAt(),
                order.getTotalAmount(),
                order.getProductId(),
                order.getQuantity());
    }

    private CancelOrderResponse toCancelOrderResponse(Order order) {
        return new CancelOrderResponse(order.getOrderNo(), order.getUserId(), order.getProductId(), order.getStatus());
    }

    private String idempotencyLockKey(Long userId, String idempotencyKey) {
        return IDEMPOTENCY_LOCK_PREFIX + userId + ":" + idempotencyKey;
    }

    private String nextOrderNo() {
        return "ORD" + LocalDateTime.now().format(ORDER_NO_TIME_FORMATTER)
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }
}
