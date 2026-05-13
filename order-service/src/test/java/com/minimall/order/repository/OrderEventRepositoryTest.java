package com.minimall.order.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minimall.order.domain.OrderEvent;
import com.minimall.order.domain.OrderEventType;
import com.minimall.order.domain.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class OrderEventRepositoryTest {

    @Autowired
    private OrderEventRepository orderEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void savesOrderEventAndFindsByEventId() {
        OrderEvent saved = orderEventRepository.saveAndFlush(new OrderEvent(
                "pay-event-1001",
                "ORD-1001",
                OrderEventType.PAYMENT_SUCCESS,
                OrderStatus.PENDING_PAYMENT,
                OrderStatus.PAID,
                "{\"handleResult\":\"processed\"}"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(orderEventRepository.findByEventId("pay-event-1001"))
                .isPresent()
                .get()
                .satisfies(event -> {
                    assertThat(event.getOrderNo()).isEqualTo("ORD-1001");
                    assertThat(event.getEventType()).isEqualTo(OrderEventType.PAYMENT_SUCCESS);
                    assertThat(event.getFromStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
                    assertThat(event.getToStatus()).isEqualTo(OrderStatus.PAID);
                    assertThat(event.getPayload()).contains("processed");
                });
    }

    @Test
    void persistsEnumValuesAsStrings() {
        orderEventRepository.saveAndFlush(new OrderEvent(
                "pay-event-1002",
                "ORD-1002",
                OrderEventType.PAYMENT_SUCCESS,
                OrderStatus.CANCELLED,
                OrderStatus.CANCELLED,
                "{\"handleResult\":\"ignored\"}"));

        String eventType = jdbcTemplate.queryForObject(
                "select event_type from order_events where event_id = ?",
                String.class,
                "pay-event-1002");
        String fromStatus = jdbcTemplate.queryForObject(
                "select from_status from order_events where event_id = ?",
                String.class,
                "pay-event-1002");
        String toStatus = jdbcTemplate.queryForObject(
                "select to_status from order_events where event_id = ?",
                String.class,
                "pay-event-1002");

        assertThat(eventType).isEqualTo("PAYMENT_SUCCESS");
        assertThat(fromStatus).isEqualTo("CANCELLED");
        assertThat(toStatus).isEqualTo("CANCELLED");
    }

    @Test
    void rejectsDuplicateEventIdForIdempotency() {
        orderEventRepository.saveAndFlush(new OrderEvent(
                "pay-event-duplicate",
                "ORD-1003",
                OrderEventType.PAYMENT_SUCCESS,
                OrderStatus.PENDING_PAYMENT,
                OrderStatus.PAID,
                "{\"handleResult\":\"processed\"}"));

        OrderEvent duplicate = new OrderEvent(
                "pay-event-duplicate",
                "ORD-1003",
                OrderEventType.PAYMENT_SUCCESS,
                OrderStatus.PENDING_PAYMENT,
                OrderStatus.PAID,
                "{\"handleResult\":\"duplicate\"}");

        assertThatThrownBy(() -> orderEventRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
