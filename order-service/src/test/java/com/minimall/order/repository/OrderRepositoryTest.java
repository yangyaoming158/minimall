package com.minimall.order.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.minimall.order.domain.Order;
import com.minimall.order.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
}
