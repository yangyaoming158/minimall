package com.minimall.order.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.minimall.common.core.event.payment.PaymentSuccessEvent;
import com.minimall.order.client.inventory.InventoryClient;
import com.minimall.order.client.product.ProductValidationService;
import com.minimall.order.domain.Order;
import com.minimall.order.domain.OrderStateMachine;
import com.minimall.order.domain.OrderStatus;
import com.minimall.order.repository.OrderEventRepository;
import com.minimall.order.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:payment_success_consumer;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
class PaymentSuccessEventConsumerTest {

    @Autowired
    private PaymentSuccessEventConsumer consumer;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderEventRepository orderEventRepository;

    @MockBean
    private ProductValidationService productValidationService;

    @MockBean
    private InventoryClient inventoryClient;

    @MockBean
    private StringRedisTemplate redisTemplate;

    private final OrderStateMachine orderStateMachine = new OrderStateMachine();

    @BeforeEach
    void setUp() {
        orderEventRepository.deleteAll();
        orderRepository.deleteAll();
    }

    @Test
    void pendingPaymentOrderTransitionsToPaidAndRecordsProcessedEvent() {
        Order order = saveOrder("ORD-PAY-1001", OrderStatus.PENDING_PAYMENT);
        Instant paidAt = Instant.parse("2026-05-13T10:15:30Z");

        consumer.handlePaymentSuccess(newEvent("event-pay-1001", order.getOrderNo(), paidAt));

        assertThat(orderRepository.findByOrderNo(order.getOrderNo()))
                .isPresent()
                .get()
                .satisfies(updated -> {
                    assertThat(updated.getStatus()).isEqualTo(OrderStatus.PAID);
                    assertThat(updated.getPaidAt()).isNotNull();
                });
        assertThat(orderEventRepository.findByEventId("event-pay-1001"))
                .isPresent()
                .get()
                .satisfies(event -> {
                    assertThat(event.getFromStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
                    assertThat(event.getToStatus()).isEqualTo(OrderStatus.PAID);
                    assertThat(event.getPayload()).contains("\"handleResult\":\"processed\"");
                    assertThat(event.getPayload()).contains("\"paymentNo\":\"PAY-event-pay-1001\"");
                });
    }

    @Test
    void cancelledOrderIsIgnoredAndStateDoesNotChange() {
        Order order = saveOrder("ORD-PAY-1002", OrderStatus.CANCELLED);

        consumer.handlePaymentSuccess(newEvent("event-pay-1002", order.getOrderNo(), Instant.now()));

        assertThat(orderRepository.findByOrderNo(order.getOrderNo()))
                .isPresent()
                .get()
                .extracting(Order::getStatus)
                .isEqualTo(OrderStatus.CANCELLED);
        assertThat(orderEventRepository.findByEventId("event-pay-1002"))
                .isPresent()
                .get()
                .satisfies(event -> {
                    assertThat(event.getFromStatus()).isEqualTo(OrderStatus.CANCELLED);
                    assertThat(event.getToStatus()).isEqualTo(OrderStatus.CANCELLED);
                    assertThat(event.getPayload()).contains("\"handleResult\":\"ignored\"");
                    assertThat(event.getPayload()).contains("Order status is CANCELLED");
                });
    }

    @Test
    void duplicateEventIdDoesNotProcessAgain() {
        Order order = saveOrder("ORD-PAY-1003", OrderStatus.PENDING_PAYMENT);
        PaymentSuccessEvent event = newEvent("event-pay-1003", order.getOrderNo(), Instant.now());

        consumer.handlePaymentSuccess(event);
        consumer.handlePaymentSuccess(event);

        assertThat(orderEventRepository.findAll()).hasSize(1);
        assertThat(orderRepository.findByOrderNo(order.getOrderNo()))
                .isPresent()
                .get()
                .extracting(Order::getStatus)
                .isEqualTo(OrderStatus.PAID);
    }

    private Order saveOrder(String orderNo, OrderStatus status) {
        Order order = new Order(
                orderNo,
                301L,
                "buyer",
                "SKU-PAY",
                "Payment Product",
                1,
                new BigDecimal("19.90"),
                new BigDecimal("19.90"),
                "idem-" + orderNo,
                LocalDateTime.now().plusMinutes(30));
        Order saved = orderRepository.saveAndFlush(order);
        if (status != OrderStatus.PENDING_PAYMENT) {
            orderStateMachine.transition(saved, status, LocalDateTime.now());
            return orderRepository.saveAndFlush(saved);
        }
        return saved;
    }

    private PaymentSuccessEvent newEvent(String eventId, String orderNo, Instant paidAt) {
        return new PaymentSuccessEvent(
                eventId,
                orderNo,
                "PAY-" + eventId,
                new BigDecimal("19.90"),
                paidAt);
    }
}
