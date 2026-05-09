package com.minimall.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.minimall.common.auth.context.UserContext;
import com.minimall.order.client.inventory.InventoryClient;
import com.minimall.order.client.inventory.InventoryDeductRequest;
import com.minimall.order.client.inventory.InventorySnapshot;
import com.minimall.order.client.inventory.InventoryStockState;
import com.minimall.order.client.product.ProductSnapshot;
import com.minimall.order.client.product.ProductStatus;
import com.minimall.order.client.product.ProductValidationService;
import com.minimall.order.domain.Order;
import com.minimall.order.domain.OrderStatus;
import com.minimall.order.dto.CreateOrderRequest;
import com.minimall.order.dto.CreateOrderResponse;
import com.minimall.order.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class OrderCommandServiceTest {

    private final ProductValidationService productValidationService = mock(ProductValidationService.class);
    private final InventoryClient inventoryClient = mock(InventoryClient.class);
    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> redisValueOperations = mock(ValueOperations.class);
    private final OrderCommandService orderCommandService = new OrderCommandService(
            productValidationService, inventoryClient, orderRepository, redisTemplate, 900, 30);

    @Test
    void createReplaysExistingOrderWhenDatabaseUniqueKeyWinsRace() {
        CreateOrderRequest request = new CreateOrderRequest("SKU-DB-RACE", 2, "idem-db-race");
        UserContext userContext = UserContext.of(301L, "kate");
        Order existingOrder = new Order(
                "ORD-EXISTING-DB-RACE",
                301L,
                "kate",
                "SKU-DB-RACE",
                "Race Product",
                2,
                new BigDecimal("7.25"),
                new BigDecimal("14.50"),
                "idem-db-race",
                LocalDateTime.of(2026, 5, 9, 20, 0));

        given(orderRepository.findByUserIdAndIdempotencyKey(301L, "idem-db-race"))
                .willReturn(Optional.empty(), Optional.of(existingOrder));
        given(redisTemplate.opsForValue()).willReturn(redisValueOperations);
        given(redisValueOperations.setIfAbsent(
                "order:create:301:idem-db-race", "PROCESSING", Duration.ofSeconds(30)))
                .willReturn(true);
        given(productValidationService.validateSellable("SKU-DB-RACE"))
                .willReturn(new ProductSnapshot(
                        "SKU-DB-RACE",
                        "Race Product",
                        new BigDecimal("7.25"),
                        ProductStatus.ON_SHELF));
        given(inventoryClient.deduct(any(InventoryDeductRequest.class)))
                .willReturn(new InventorySnapshot("SKU-DB-RACE", 5, 2, InventoryStockState.IN_STOCK));
        given(orderRepository.saveAndFlush(any(Order.class)))
                .willThrow(new DataIntegrityViolationException("duplicate idempotency key"));

        CreateOrderResponse response = orderCommandService.create(request, userContext);

        assertThat(response.orderNo()).isEqualTo("ORD-EXISTING-DB-RACE");
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(response.totalAmount()).isEqualByComparingTo("14.50");
        assertThat(response.productId()).isEqualTo("SKU-DB-RACE");
        assertThat(response.quantity()).isEqualTo(2);

        ArgumentCaptor<InventoryDeductRequest> deductRequest = ArgumentCaptor.forClass(InventoryDeductRequest.class);
        verify(inventoryClient).deduct(deductRequest.capture());
        assertThat(deductRequest.getValue().productId()).isEqualTo("SKU-DB-RACE");
        assertThat(deductRequest.getValue().quantity()).isEqualTo(2);
    }
}