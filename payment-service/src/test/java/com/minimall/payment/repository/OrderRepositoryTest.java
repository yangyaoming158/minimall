package com.minimall.payment.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.minimall.payment.domain.Order;
import com.minimall.payment.domain.OrderStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
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
    void savesMinimalOrderReadModelAndFindsByOrderNo() {
        orderRepository.saveAndFlush(new Order(
                "ORD-READ-1001",
                101L,
                "SKU-READ-1001",
                OrderStatus.PENDING_PAYMENT,
                new BigDecimal("39.80")));

        assertThat(orderRepository.findByOrderNo("ORD-READ-1001"))
                .isPresent()
                .get()
                .satisfies(order -> {
                    assertThat(order.getUserId()).isEqualTo(101L);
                    assertThat(order.getProductId()).isEqualTo("SKU-READ-1001");
                    assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
                    assertThat(order.getTotalAmount()).isEqualByComparingTo("39.80");
                });
    }

    @Test
    void persistsOrderStatusAsStableName() {
        orderRepository.saveAndFlush(new Order(
                "ORD-READ-1002",
                102L,
                "SKU-READ-1002",
                OrderStatus.CANCELLED,
                new BigDecimal("9.99")));

        String status = jdbcTemplate.queryForObject(
                "select status from orders where order_no = ?",
                String.class,
                "ORD-READ-1002");

        assertThat(status).isEqualTo("CANCELLED");
    }
}
