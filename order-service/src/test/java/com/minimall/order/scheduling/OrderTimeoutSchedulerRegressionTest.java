package com.minimall.order.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.minimall.order.client.inventory.InventoryClient;
import com.minimall.order.client.inventory.InventoryDeductRequest;
import com.minimall.order.config.OrderTimeoutProperties;
import com.minimall.order.domain.Order;
import com.minimall.order.domain.OrderStateMachine;
import com.minimall.order.domain.OrderStatus;
import com.minimall.order.repository.OrderRepository;
import com.minimall.order.service.OrderTimeoutCancellationService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@Import({
        OrderTimeoutCancellationService.class,
        OrderTimeoutProperties.class,
        OrderTimeoutScheduler.class
})
class OrderTimeoutSchedulerRegressionTest {

    @Autowired
    private OrderTimeoutScheduler scheduler;

    @Autowired
    private OrderTimeoutProperties properties;

    @Autowired
    private OrderRepository orderRepository;

    @MockBean
    private InventoryClient inventoryClient;

    private final OrderStateMachine orderStateMachine = new OrderStateMachine();

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        reset(inventoryClient);
        properties.setEnabled(true);
        properties.setBatchSize(10);
    }

    @Test
    void schedulerCancelsOnlyExpiredPendingOrdersAndReleasesInventory() {
        LocalDateTime now = LocalDateTime.now();
        Order expired = orderRepository.saveAndFlush(order("ORD-SCHED-EXPIRED", 601L, now.minusMinutes(5)));
        Order future = orderRepository.saveAndFlush(order("ORD-SCHED-FUTURE", 602L, now.plusMinutes(5)));
        Order paid = order("ORD-SCHED-PAID", 603L, now.minusMinutes(10));
        orderStateMachine.transition(paid, OrderStatus.PAID, now.minusMinutes(9));
        paid = orderRepository.saveAndFlush(paid);

        scheduler.cancelExpiredPendingOrders();

        assertThat(orderRepository.findByOrderNo(expired.getOrderNo()))
                .isPresent()
                .get()
                .satisfies(order -> {
                    assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
                    assertThat(order.getClosedAt()).isNotNull();
                });
        assertThat(orderRepository.findByOrderNo(future.getOrderNo()))
                .isPresent()
                .get()
                .extracting(Order::getStatus)
                .isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(orderRepository.findByOrderNo(paid.getOrderNo()))
                .isPresent()
                .get()
                .extracting(Order::getStatus)
                .isEqualTo(OrderStatus.PAID);

        ArgumentCaptor<InventoryDeductRequest> releaseCaptor =
                ArgumentCaptor.forClass(InventoryDeductRequest.class);
        verify(inventoryClient).release(releaseCaptor.capture());
        assertThat(releaseCaptor.getValue().orderNo()).isEqualTo(expired.getOrderNo());
        assertThat(releaseCaptor.getValue().productId()).isEqualTo("SKU-SCHED-EXPIRED");
        assertThat(releaseCaptor.getValue().quantity()).isEqualTo(2);
    }

    @Test
    void repeatedSchedulerScanDoesNotReleaseInventoryTwice() {
        LocalDateTime now = LocalDateTime.now();
        Order expired = orderRepository.saveAndFlush(order("ORD-SCHED-REPEAT", 604L, now.minusMinutes(5)));

        scheduler.cancelExpiredPendingOrders();
        scheduler.cancelExpiredPendingOrders();

        assertThat(orderRepository.findByOrderNo(expired.getOrderNo()))
                .isPresent()
                .get()
                .extracting(Order::getStatus)
                .isEqualTo(OrderStatus.CANCELLED);
        verify(inventoryClient, times(1)).release(any(InventoryDeductRequest.class));
    }

    private Order order(String orderNo, Long userId, LocalDateTime expireAt) {
        String suffix = orderNo.substring("ORD-SCHED-".length());
        return new Order(
                orderNo,
                userId,
                "scheduler-user",
                "SKU-SCHED-" + suffix,
                "Scheduler Product",
                2,
                new BigDecimal("10.00"),
                new BigDecimal("20.00"),
                "idem-" + orderNo,
                expireAt);
    }
}
