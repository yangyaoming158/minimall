package com.minimall.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.minimall.order.client.inventory.InventoryClient;
import com.minimall.order.client.inventory.InventoryDeductRequest;
import com.minimall.order.domain.Order;
import com.minimall.order.domain.OrderStatus;
import com.minimall.order.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

class OrderTimeoutCancellationServiceTest {

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final InventoryClient inventoryClient = mock(InventoryClient.class);
    private final OrderTimeoutCancellationService service =
            new OrderTimeoutCancellationService(orderRepository, inventoryClient);

    @Test
    void cancelsExpiredPendingOrderAndReleasesInventoryAfterConditionalUpdate() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 16, 11, 0);
        Order order = order(501L, "ORD-TIMEOUT-1001", now.minusMinutes(1));
        given(orderRepository.findExpiredOrders(
                eq(OrderStatus.PENDING_PAYMENT),
                eq(now),
                any(Pageable.class)))
                .willReturn(List.of(order));
        given(orderRepository.updateStatusIfCurrent(501L, OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED, now))
                .willReturn(1);

        int cancelledCount = service.cancelExpiredPendingOrders(now, 10);

        assertThat(cancelledCount).isEqualTo(1);
        ArgumentCaptor<InventoryDeductRequest> releaseRequest =
                ArgumentCaptor.forClass(InventoryDeductRequest.class);
        verify(inventoryClient).release(releaseRequest.capture());
        assertThat(releaseRequest.getValue().orderNo()).isEqualTo("ORD-TIMEOUT-1001");
        assertThat(releaseRequest.getValue().productId()).isEqualTo("SKU-TIMEOUT-1001");
        assertThat(releaseRequest.getValue().quantity()).isEqualTo(2);
    }

    @Test
    void skipsInventoryReleaseWhenConditionalUpdateDoesNotChangeState() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 16, 11, 5);
        Order order = order(502L, "ORD-TIMEOUT-1002", now.minusMinutes(2));
        given(orderRepository.findExpiredOrders(
                eq(OrderStatus.PENDING_PAYMENT),
                eq(now),
                any(Pageable.class)))
                .willReturn(List.of(order));
        given(orderRepository.updateStatusIfCurrent(502L, OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED, now))
                .willReturn(0);

        int cancelledCount = service.cancelExpiredPendingOrders(now, 10);

        assertThat(cancelledCount).isZero();
        verifyNoInteractions(inventoryClient);
    }

    @Test
    void repeatedExecutionDoesNotReleaseInventoryTwice() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 16, 11, 10);
        Order order = order(503L, "ORD-TIMEOUT-1003", now.minusMinutes(3));
        given(orderRepository.findExpiredOrders(
                eq(OrderStatus.PENDING_PAYMENT),
                eq(now),
                any(Pageable.class)))
                .willReturn(List.of(order), List.of(order));
        given(orderRepository.updateStatusIfCurrent(503L, OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED, now))
                .willReturn(1, 0);

        int firstRun = service.cancelExpiredPendingOrders(now, 10);
        int secondRun = service.cancelExpiredPendingOrders(now, 10);

        assertThat(firstRun).isEqualTo(1);
        assertThat(secondRun).isZero();
        verify(inventoryClient, times(1)).release(any(InventoryDeductRequest.class));
    }

    private Order order(Long id, String orderNo, LocalDateTime expireAt) {
        Order order = new Order(
                orderNo,
                501L,
                "timeout-user",
                "SKU-TIMEOUT-1001",
                "Timeout Product",
                2,
                new BigDecimal("10.00"),
                new BigDecimal("20.00"),
                "idem-" + orderNo,
                expireAt);
        ReflectionTestUtils.setField(order, "id", id);
        return order;
    }
}
