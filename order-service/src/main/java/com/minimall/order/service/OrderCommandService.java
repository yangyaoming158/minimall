package com.minimall.order.service;

import com.minimall.common.auth.context.UserContext;
import com.minimall.order.client.inventory.InventoryClient;
import com.minimall.order.client.inventory.InventoryDeductRequest;
import com.minimall.order.client.product.ProductSnapshot;
import com.minimall.order.client.product.ProductValidationService;
import com.minimall.order.domain.Order;
import com.minimall.order.dto.CreateOrderRequest;
import com.minimall.order.dto.CreateOrderResponse;
import com.minimall.order.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderCommandService {

    private static final DateTimeFormatter ORDER_NO_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final ProductValidationService productValidationService;
    private final InventoryClient inventoryClient;
    private final OrderRepository orderRepository;
    private final long paymentExpireSeconds;

    public OrderCommandService(
            ProductValidationService productValidationService,
            InventoryClient inventoryClient,
            OrderRepository orderRepository,
            @Value("${minimall.order.payment-expire-seconds:900}") long paymentExpireSeconds) {
        this.productValidationService = productValidationService;
        this.inventoryClient = inventoryClient;
        this.orderRepository = orderRepository;
        this.paymentExpireSeconds = paymentExpireSeconds;
    }

    @Transactional
    public CreateOrderResponse create(CreateOrderRequest request, UserContext userContext) {
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

        return new CreateOrderResponse(
                order.getOrderNo(),
                order.getStatus(),
                order.getExpireAt(),
                order.getTotalAmount(),
                order.getProductId(),
                order.getQuantity());
    }

    private String nextOrderNo() {
        return "ORD" + LocalDateTime.now().format(ORDER_NO_TIME_FORMATTER)
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }
}