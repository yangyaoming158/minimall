package com.minimall.order.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.minimall.order.domain.Order;
import com.minimall.order.domain.OrderStateMachine;
import com.minimall.order.domain.OrderStatus;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void savesOrderAndFindsByOrderNo() {
        LocalDateTime expireAt = LocalDateTime.now().plusMinutes(30);

        Order saved = orderRepository.saveAndFlush(new Order(
                "ORD-1001",
                101L,
                "alice",
                "SKU-ORDER-1001",
                "Test Product",
                2,
                new BigDecimal("19.90"),
                new BigDecimal("39.80"),
                "idem-1001",
                expireAt));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(orderRepository.findByOrderNo("ORD-1001"))
                .isPresent()
                .get()
                .satisfies(order -> {
                    assertThat(order.getOrderNo()).isEqualTo("ORD-1001");
                    assertThat(order.getUserId()).isEqualTo(101L);
                    assertThat(order.getUsername()).isEqualTo("alice");
                    assertThat(order.getProductId()).isEqualTo("SKU-ORDER-1001");
                    assertThat(order.getProductName()).isEqualTo("Test Product");
                    assertThat(order.getQuantity()).isEqualTo(2);
                    assertThat(order.getUnitPrice()).isEqualByComparingTo("19.90");
                    assertThat(order.getTotalAmount()).isEqualByComparingTo("39.80");
                    assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
                    assertThat(order.getExpireAt()).isNotNull();
                });
    }

    @Test
    void persistsStatusAsEnumValue() {
        orderRepository.saveAndFlush(new Order(
                "ORD-1002",
                102L,
                "bob",
                "SKU-ORDER-1002",
                "Another Product",
                1,
                new BigDecimal("9.99"),
                new BigDecimal("9.99"),
                "idem-1002",
                null));

        String status = jdbcTemplate.queryForObject(
                "select status from orders where order_no = ?",
                String.class,
                "ORD-1002");

        assertThat(status).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void findsOrderByOrderNoAndUserIdAndPagesByUserId() {
        orderRepository.saveAndFlush(new Order(
                "ORD-1003",
                103L,
                "carol",
                "SKU-ORDER-1003",
                "Paged Product",
                3,
                new BigDecimal("5.00"),
                new BigDecimal("15.00"),
                "idem-1003",
                null));

        assertThat(orderRepository.findByOrderNoAndUserId("ORD-1003", 103L)).isPresent();
        assertThat(orderRepository.findByOrderNoAndUserId("ORD-1003", 999L)).isEmpty();
        assertThat(orderRepository.findByUserId(103L, PageRequest.of(0, 10)).getContent())
                .extracting(Order::getOrderNo)
                .containsExactly("ORD-1003");
    }

    @Test
    void findsOnlyExpiredPendingPaymentOrdersForTimeoutCancellation() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 16, 10, 0);
        orderRepository.saveAndFlush(order("ORD-EXPIRED-1001", 201L, now.minusMinutes(1)));
        orderRepository.saveAndFlush(order("ORD-FUTURE-1001", 202L, now.plusMinutes(1)));
        Order paid = order("ORD-PAID-1001", 203L, now.minusMinutes(5));
        new OrderStateMachine().transition(paid, OrderStatus.PAID, now.minusMinutes(4));
        orderRepository.saveAndFlush(paid);

        List<Order> expiredOrders = orderRepository.findExpiredOrders(
                OrderStatus.PENDING_PAYMENT,
                now,
                PageRequest.of(0, 10));

        assertThat(expiredOrders)
                .extracting(Order::getOrderNo)
                .containsExactly("ORD-EXPIRED-1001");
    }

    @Test
    void conditionallyCancelsOnlyPendingPaymentOrder() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 16, 10, 10);
        Order pending = orderRepository.saveAndFlush(order("ORD-COND-CANCEL-1001", 204L, now.minusMinutes(1)));
        Order paid = order("ORD-COND-CANCEL-1002", 205L, now.minusMinutes(2));
        new OrderStateMachine().transition(paid, OrderStatus.PAID, now.minusMinutes(1));
        paid = orderRepository.saveAndFlush(paid);

        int updatedPending = orderRepository.updateStatusIfCurrent(
                pending.getId(),
                OrderStatus.PENDING_PAYMENT,
                OrderStatus.CANCELLED,
                now);
        int updatedPaid = orderRepository.updateStatusIfCurrent(
                paid.getId(),
                OrderStatus.PENDING_PAYMENT,
                OrderStatus.CANCELLED,
                now);

        assertThat(updatedPending).isEqualTo(1);
        assertThat(updatedPaid).isZero();
        assertThat(orderRepository.findByOrderNo("ORD-COND-CANCEL-1001"))
                .isPresent()
                .get()
                .satisfies(order -> {
                    assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
                    assertThat(order.getClosedAt()).isEqualTo(now);
                    assertThat(order.getUpdatedAt()).isEqualTo(now);
                });
        assertThat(orderRepository.findByOrderNo("ORD-COND-CANCEL-1002"))
                .isPresent()
                .get()
                .satisfies(order -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID));
    }

    @Test
    void aggregatesProductSalesWithFiltersAndStableOrdering() {
        LocalDateTime dayStart = LocalDateTime.of(2026, 5, 20, 0, 0);
        saveOrder(order(
                "ORD-SALES-1001",
                301L,
                "SKU-SALES-A",
                2,
                new BigDecimal("20.00"),
                OrderStatus.PAID),
                dayStart.plusHours(1));
        saveOrder(order(
                "ORD-SALES-1002",
                302L,
                "SKU-SALES-A",
                3,
                new BigDecimal("20.00"),
                OrderStatus.PAID),
                dayStart.plusHours(2));
        saveOrder(order(
                "ORD-SALES-1003",
                303L,
                "SKU-SALES-B",
                1,
                new BigDecimal("9.99"),
                OrderStatus.PAID),
                dayStart.plusHours(3));
        saveOrder(order(
                "ORD-SALES-1004",
                304L,
                "SKU-SALES-A",
                7,
                new BigDecimal("20.00"),
                OrderStatus.PENDING_PAYMENT),
                dayStart.plusHours(4));

        var page = orderRepository.aggregateProductSales(
                null,
                OrderStatus.PAID,
                dayStart,
                dayStart.plusDays(1),
                PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getContent().get(0)).satisfies(aggregation -> {
            assertThat(aggregation.getProductId()).isEqualTo("SKU-SALES-A");
            assertThat(aggregation.getQuantitySold()).isEqualTo(5L);
            assertThat(aggregation.getOrderCount()).isEqualTo(2L);
            assertThat(aggregation.getTotalAmount()).isEqualByComparingTo("100.00");
        });
        assertThat(page.getContent().get(1)).satisfies(aggregation -> {
            assertThat(aggregation.getProductId()).isEqualTo("SKU-SALES-B");
            assertThat(aggregation.getQuantitySold()).isEqualTo(1L);
            assertThat(aggregation.getOrderCount()).isEqualTo(1L);
            assertThat(aggregation.getTotalAmount()).isEqualByComparingTo("9.99");
        });
    }

    @Test
    void aggregatesProductSalesByProductId() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 5, 21, 12, 0);
        saveOrder(order(
                "ORD-SALES-1005",
                305L,
                "SKU-SALES-C",
                4,
                new BigDecimal("15.00"),
                OrderStatus.PAID),
                createdAt);
        saveOrder(order(
                "ORD-SALES-1006",
                306L,
                "SKU-SALES-D",
                2,
                new BigDecimal("30.00"),
                OrderStatus.PAID),
                createdAt);

        var page = orderRepository.aggregateProductSales(
                "SKU-SALES-C",
                null,
                null,
                null,
                PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent())
                .singleElement()
                .satisfies(aggregation -> {
                    assertThat(aggregation.getProductId()).isEqualTo("SKU-SALES-C");
                    assertThat(aggregation.getQuantitySold()).isEqualTo(4L);
                    assertThat(aggregation.getOrderCount()).isEqualTo(1L);
                    assertThat(aggregation.getTotalAmount()).isEqualByComparingTo("60.00");
                });
    }

    private Order order(String orderNo, Long userId, LocalDateTime expireAt) {
        return new Order(
                orderNo,
                userId,
                "timeout-user",
                "SKU-TIMEOUT-1001",
                "Timeout Product",
                2,
                new BigDecimal("10.00"),
                new BigDecimal("20.00"),
                "idem-" + orderNo,
                expireAt);
    }

    private Order order(
            String orderNo,
            Long userId,
            String productId,
            int quantity,
            BigDecimal unitPrice,
            OrderStatus status) {
        Order order = new Order(
                orderNo,
                userId,
                "sales-user",
                productId,
                "Sales Product",
                quantity,
                unitPrice,
                unitPrice.multiply(BigDecimal.valueOf(quantity)),
                "idem-" + orderNo,
                null);
        if (status != OrderStatus.PENDING_PAYMENT) {
            new OrderStateMachine().transition(order, status, LocalDateTime.of(2026, 5, 20, 10, 0));
        }
        return order;
    }

    private void saveOrder(Order order, LocalDateTime createdAt) {
        orderRepository.saveAndFlush(order);
        jdbcTemplate.update(
                "update orders set created_at = ?, updated_at = ? where order_no = ?",
                Timestamp.valueOf(createdAt),
                Timestamp.valueOf(createdAt),
                order.getOrderNo());
    }
}
