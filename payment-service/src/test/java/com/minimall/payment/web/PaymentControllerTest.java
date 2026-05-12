package com.minimall.payment.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minimall.common.auth.constants.AuthHeaders;
import com.minimall.common.core.event.payment.PaymentEventNames;
import com.minimall.common.core.event.payment.PaymentSuccessEvent;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.payment.domain.Order;
import com.minimall.payment.domain.OrderStatus;
import com.minimall.payment.domain.Payment;
import com.minimall.payment.domain.PaymentChannel;
import com.minimall.payment.domain.PaymentStatus;
import com.minimall.payment.repository.OrderRepository;
import com.minimall.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:payment_controller;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.rabbitmq.host=127.0.0.1",
        "spring.rabbitmq.port=5672",
        "spring.rabbitmq.username=guest",
        "spring.rabbitmq.password=guest",
        "spring.rabbitmq.virtual-host=/"
})
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @MockBean
    private RabbitTemplate rabbitTemplate;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        reset(rabbitTemplate);
    }

    @Test
    void payPendingOrderReturnsApiResponseAndPersistsSuccessPayment() throws Exception {
        orderRepository.saveAndFlush(order("ORD-PAY-API-1001", 501L, OrderStatus.PENDING_PAYMENT, "39.80"));

        mockMvc.perform(post("/api/payments/ORD-PAY-API-1001/pay")
                        .header(AuthHeaders.USER_ID, "501")
                        .header(AuthHeaders.USERNAME, "alice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"channel":"MOCK","idempotencyKey":"pay-idem-1001"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.id").doesNotExist())
                .andExpect(jsonPath("$.data.userId").doesNotExist())
                .andExpect(jsonPath("$.data.idempotencyKey").doesNotExist())
                .andExpect(jsonPath("$.data.paymentNo", startsWith("PAY")))
                .andExpect(jsonPath("$.data.orderNo").value("ORD-PAY-API-1001"))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.amount").value(39.80))
                .andExpect(jsonPath("$.data.channel").value("MOCK"))
                .andExpect(jsonPath("$.data.paidAt").exists());

        assertThat(paymentRepository.findByOrderNo("ORD-PAY-API-1001"))
                .isPresent()
                .get()
                .satisfies(payment -> {
                    assertThat(payment.getPaymentNo()).startsWith("PAY");
                    assertThat(payment.getAmount()).isEqualByComparingTo("39.80");
                    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
                    assertThat(payment.getChannel()).isEqualTo(PaymentChannel.MOCK);
                    assertThat(payment.getIdempotencyKey()).isEqualTo("pay-idem-1001");
                    assertThat(payment.getPaidAt()).isNotNull();
                });
        Payment saved = paymentRepository.findByOrderNo("ORD-PAY-API-1001").orElseThrow();
        ArgumentCaptor<PaymentSuccessEvent> eventCaptor = ArgumentCaptor.forClass(PaymentSuccessEvent.class);
        verify(rabbitTemplate).convertAndSend(
                eq(PaymentEventNames.PAYMENT_EXCHANGE),
                eq(PaymentEventNames.PAYMENT_SUCCESS_ROUTING_KEY),
                eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventId()).isNotBlank();
        assertThat(eventCaptor.getValue().getOrderNo()).isEqualTo("ORD-PAY-API-1001");
        assertThat(eventCaptor.getValue().getPaymentNo()).isEqualTo(saved.getPaymentNo());
        assertThat(eventCaptor.getValue().getAmount()).isEqualByComparingTo("39.80");
        assertThat(eventCaptor.getValue().getPaidAt()).isNotNull();
        assertThat(eventCaptor.getValue().getVersion()).isEqualTo(PaymentSuccessEvent.CURRENT_VERSION);
    }

    @Test
    void payReplayReturnsAlreadySuccessWithoutCreatingAnotherRecordOrRepublishingEvent() throws Exception {
        orderRepository.saveAndFlush(order("ORD-PAY-API-1002", 502L, OrderStatus.PENDING_PAYMENT, "18.50"));

        mockMvc.perform(post("/api/payments/ORD-PAY-API-1002/pay")
                        .header(AuthHeaders.USER_ID, "502")
                        .header(AuthHeaders.USERNAME, "bob")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        Payment saved = paymentRepository.findByOrderNo("ORD-PAY-API-1002").orElseThrow();

        mockMvc.perform(post("/api/payments/ORD-PAY-API-1002/pay")
                        .header(AuthHeaders.USER_ID, "502")
                        .header(AuthHeaders.USERNAME, "bob")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.PAYMENT_ALREADY_SUCCESS.getCode()))
                .andExpect(jsonPath("$.message").value("Payment already successful"));

        assertThat(paymentRepository.findAll()).hasSize(1);
        assertThat(paymentRepository.findByOrderNo("ORD-PAY-API-1002"))
                .isPresent()
                .get()
                .extracting(Payment::getPaymentNo)
                .isEqualTo(saved.getPaymentNo());
        verify(rabbitTemplate, times(1)).convertAndSend(
                eq(PaymentEventNames.PAYMENT_EXCHANGE),
                eq(PaymentEventNames.PAYMENT_SUCCESS_ROUTING_KEY),
                org.mockito.ArgumentMatchers.any(PaymentSuccessEvent.class));
    }

    @Test
    void payExistingPendingPaymentMarksSuccessAndPublishesOneEvent() throws Exception {
        orderRepository.saveAndFlush(order("ORD-PAY-API-1007", 508L, OrderStatus.PENDING_PAYMENT, "27.50"));
        paymentRepository.saveAndFlush(new Payment(
                "PAY-PENDING-1007",
                "ORD-PAY-API-1007",
                new BigDecimal("27.50"),
                PaymentChannel.MOCK,
                "pending-idem-1007"));

        mockMvc.perform(post("/api/payments/ORD-PAY-API-1007/pay")
                        .header(AuthHeaders.USER_ID, "508")
                        .header(AuthHeaders.USERNAME, "grace")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.paymentNo").value("PAY-PENDING-1007"))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));

        assertThat(paymentRepository.findAll()).hasSize(1);
        assertThat(paymentRepository.findByOrderNo("ORD-PAY-API-1007"))
                .isPresent()
                .get()
                .satisfies(payment -> {
                    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
                    assertThat(payment.getPaidAt()).isNotNull();
                });
        verify(rabbitTemplate, times(1)).convertAndSend(
                eq(PaymentEventNames.PAYMENT_EXCHANGE),
                eq(PaymentEventNames.PAYMENT_SUCCESS_ROUTING_KEY),
                org.mockito.ArgumentMatchers.any(PaymentSuccessEvent.class));
    }

    @Test
    void queryPaymentByOrderNoReturnsStableDto() throws Exception {
        orderRepository.saveAndFlush(order("ORD-PAY-API-1003", 503L, OrderStatus.PENDING_PAYMENT, "9.99"));
        Payment payment = new Payment(
                "PAY-QUERY-1003",
                "ORD-PAY-API-1003",
                new BigDecimal("9.99"),
                PaymentChannel.MOCK,
                "query-idem-1003");
        payment.markSuccess(java.time.LocalDateTime.now());
        paymentRepository.saveAndFlush(payment);

        mockMvc.perform(get("/api/payments/ORD-PAY-API-1003")
                        .header(AuthHeaders.USER_ID, "503")
                        .header(AuthHeaders.USERNAME, "carol"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").doesNotExist())
                .andExpect(jsonPath("$.data.paymentNo").value("PAY-QUERY-1003"))
                .andExpect(jsonPath("$.data.orderNo").value("ORD-PAY-API-1003"))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.amount").value(9.99));
    }

    @Test
    void payMissingOrderReturnsNotFound() throws Exception {
        mockMvc.perform(post("/api/payments/ORD-MISSING/pay")
                        .header(AuthHeaders.USER_ID, "504")
                        .header(AuthHeaders.USERNAME, "dave")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message").value("Order not found"));

        assertThat(paymentRepository.findAll()).isEmpty();
    }

    @Test
    void payAnotherUsersOrderReturnsNotFound() throws Exception {
        orderRepository.saveAndFlush(order("ORD-PAY-API-1004", 505L, OrderStatus.PENDING_PAYMENT, "12.00"));

        mockMvc.perform(post("/api/payments/ORD-PAY-API-1004/pay")
                        .header(AuthHeaders.USER_ID, "999")
                        .header(AuthHeaders.USERNAME, "mallory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message").value("Order not found"));

        assertThat(paymentRepository.findAll()).isEmpty();
    }

    @Test
    void payCancelledOrderReturnsConflict() throws Exception {
        orderRepository.saveAndFlush(order("ORD-PAY-API-1005", 506L, OrderStatus.CANCELLED, "12.00"));

        mockMvc.perform(post("/api/payments/ORD-PAY-API-1005/pay")
                        .header(AuthHeaders.USER_ID, "506")
                        .header(AuthHeaders.USERNAME, "erin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.ORDER_CANCELLED.getCode()))
                .andExpect(jsonPath("$.message").value("Order has been cancelled"));

        assertThat(paymentRepository.findAll()).isEmpty();
    }

    @Test
    void payPaidOrderReturnsInvalidStateConflict() throws Exception {
        orderRepository.saveAndFlush(order("ORD-PAY-API-1008", 509L, OrderStatus.PAID, "12.00"));

        mockMvc.perform(post("/api/payments/ORD-PAY-API-1008/pay")
                        .header(AuthHeaders.USER_ID, "509")
                        .header(AuthHeaders.USERNAME, "heidi")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.ORDER_INVALID_STATE.getCode()))
                .andExpect(jsonPath("$.message").value("Order current status cannot be paid: PAID"));

        assertThat(paymentRepository.findAll()).isEmpty();
    }

    @Test
    void payMissingAuthenticationReturnsUnauthorized() throws Exception {
        orderRepository.saveAndFlush(order("ORD-PAY-API-1006", 507L, OrderStatus.PENDING_PAYMENT, "12.00"));

        mockMvc.perform(post("/api/payments/ORD-PAY-API-1006/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()))
                .andExpect(jsonPath("$.message").value("Unauthorized"));

        assertThat(paymentRepository.findAll()).isEmpty();
    }

    private Order order(String orderNo, Long userId, OrderStatus status, String totalAmount) {
        return new Order(orderNo, userId, status, new BigDecimal(totalAmount));
    }
}
