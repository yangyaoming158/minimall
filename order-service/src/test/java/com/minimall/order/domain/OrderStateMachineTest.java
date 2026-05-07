package com.minimall.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class OrderStateMachineTest {

    private final OrderStateMachine stateMachine = new OrderStateMachine();

    @Test
    void transitionsPendingPaymentToPaid() {
        Order order = newOrder("ORD-SM-1001");
        LocalDateTime paidAt = LocalDateTime.of(2026, 5, 7, 10, 0);

        stateMachine.transition(order, OrderStatus.PAID, paidAt);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getPaidAt()).isEqualTo(paidAt);
        assertThat(order.getUpdatedAt()).isEqualTo(paidAt);
        assertThat(order.getClosedAt()).isNull();
    }

    @Test
    void transitionsPendingPaymentToCancelled() {
        Order order = newOrder("ORD-SM-1002");
        LocalDateTime cancelledAt = LocalDateTime.of(2026, 5, 7, 10, 5);

        stateMachine.transition(order, OrderStatus.CANCELLED, cancelledAt);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getClosedAt()).isEqualTo(cancelledAt);
        assertThat(order.getUpdatedAt()).isEqualTo(cancelledAt);
        assertThat(order.getPaidAt()).isNull();
    }

    @Test
    void transitionsPaidToClosed() {
        Order order = paidOrder("ORD-SM-1003");
        LocalDateTime closedAt = LocalDateTime.of(2026, 5, 7, 10, 10);

        stateMachine.transition(order, OrderStatus.CLOSED, closedAt);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CLOSED);
        assertThat(order.getClosedAt()).isEqualTo(closedAt);
        assertThat(order.getUpdatedAt()).isEqualTo(closedAt);
    }

    @Test
    void transitionsPaidToRefunded() {
        Order order = paidOrder("ORD-SM-1004");
        LocalDateTime refundedAt = LocalDateTime.of(2026, 5, 7, 10, 15);

        stateMachine.transition(order, OrderStatus.REFUNDED, refundedAt);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(order.getUpdatedAt()).isEqualTo(refundedAt);
    }

    @Test
    void rejectsInvalidTransition() {
        Order order = newOrder("ORD-SM-1005");
        stateMachine.transition(order, OrderStatus.CANCELLED, LocalDateTime.of(2026, 5, 7, 10, 20));

        assertThatThrownBy(() -> stateMachine.transition(
                        order,
                        OrderStatus.PAID,
                        LocalDateTime.of(2026, 5, 7, 10, 25)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
                    assertThat(exception.getMessage()).contains("CANCELLED -> PAID");
                });
    }

    @Test
    void exposesTransitionChecks() {
        assertThat(stateMachine.canTransition(OrderStatus.PENDING_PAYMENT, OrderStatus.PAID)).isTrue();
        assertThat(stateMachine.canTransition(OrderStatus.PAID, OrderStatus.REFUNDED)).isTrue();
        assertThat(stateMachine.canTransition(OrderStatus.CANCELLED, OrderStatus.PAID)).isFalse();
    }

    private Order paidOrder(String orderNo) {
        Order order = newOrder(orderNo);
        stateMachine.transition(order, OrderStatus.PAID, LocalDateTime.of(2026, 5, 7, 9, 0));
        return order;
    }

    private Order newOrder(String orderNo) {
        return new Order(
                orderNo,
                101L,
                "alice",
                "SKU-SM-1001",
                "State Machine Product",
                1,
                new BigDecimal("12.50"),
                new BigDecimal("12.50"),
                "idem-" + orderNo,
                LocalDateTime.of(2026, 5, 7, 11, 0));
    }
}
