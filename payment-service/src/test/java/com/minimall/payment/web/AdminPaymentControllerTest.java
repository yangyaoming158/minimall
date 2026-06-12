package com.minimall.payment.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minimall.common.auth.constants.AuthHeaders;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.payment.domain.Order;
import com.minimall.payment.domain.OrderStatus;
import com.minimall.payment.domain.Payment;
import com.minimall.payment.domain.PaymentChannel;
import com.minimall.payment.repository.OrderRepository;
import com.minimall.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.reset;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:admin_payment_controller;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
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
class AdminPaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private RabbitTemplate rabbitTemplate;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        reset(rabbitTemplate);
    }

    @Test
    void adminPaymentListRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/payments"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()));
    }

    @Test
    void adminPaymentListRejectsUserRole() throws Exception {
        mockMvc.perform(get("/api/admin/payments")
                        .header(AuthHeaders.USER_ID, "501")
                        .header(AuthHeaders.USERNAME, "operator")
                        .header(AuthHeaders.USER_ROLE, "USER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));
    }

    @Test
    void adminPaymentListReturnsNewestFirstWithEnrichmentForAdmin() throws Exception {
        saveOrder("ORD-ADM-PAY-1001", 601L, "39.80");
        saveOrder("ORD-ADM-PAY-1002", 602L, "18.50");
        saveOrder("ORD-ADM-PAY-1003", 603L, "9.99");
        savePaidPayment("PAY-ADM-1001", "ORD-ADM-PAY-1001", "39.80",
                LocalDateTime.of(2026, 5, 20, 10, 0), LocalDateTime.of(2026, 5, 20, 10, 5));
        savePaidPayment("PAY-ADM-1002", "ORD-ADM-PAY-1002", "18.50",
                LocalDateTime.of(2026, 5, 22, 10, 0), LocalDateTime.of(2026, 5, 22, 10, 5));
        savePendingPayment("PAY-ADM-1003", "ORD-ADM-PAY-1003", "9.99",
                LocalDateTime.of(2026, 5, 21, 10, 0));

        mockMvc.perform(get("/api/admin/payments")
                        .header(AuthHeaders.USER_ID, "900")
                        .header(AuthHeaders.USERNAME, "admin")
                        .header(AuthHeaders.USER_ROLE, "ADMIN")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.content[0].paymentNo").value("PAY-ADM-1002"))
                .andExpect(jsonPath("$.data.content[0].userId").value(602))
                .andExpect(jsonPath("$.data.content[0].productId").value("SKU-ORD-ADM-PAY-1002"))
                .andExpect(jsonPath("$.data.content[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.content[0].channel").value("MOCK"))
                .andExpect(jsonPath("$.data.content[1].paymentNo").value("PAY-ADM-1003"))
                .andExpect(jsonPath("$.data.content[1].status").value("PENDING"))
                .andExpect(jsonPath("$.data.content[1].paidAt").doesNotExist())
                .andExpect(jsonPath("$.data.content[2].paymentNo").value("PAY-ADM-1001"));
    }

    @Test
    void adminPaymentListFiltersByStatusAndOrderNo() throws Exception {
        saveOrder("ORD-ADM-FILT-1001", 611L, "39.80");
        saveOrder("ORD-ADM-FILT-1002", 612L, "18.50");
        savePaidPayment("PAY-ADM-FILT-1001", "ORD-ADM-FILT-1001", "39.80",
                LocalDateTime.of(2026, 5, 20, 10, 0), LocalDateTime.of(2026, 5, 20, 10, 5));
        savePendingPayment("PAY-ADM-FILT-1002", "ORD-ADM-FILT-1002", "18.50",
                LocalDateTime.of(2026, 5, 20, 11, 0));

        mockMvc.perform(get("/api/admin/payments")
                        .header(AuthHeaders.USER_ID, "900")
                        .header(AuthHeaders.USERNAME, "admin")
                        .header(AuthHeaders.USER_ROLE, "ADMIN")
                        .param("status", "SUCCESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].paymentNo").value("PAY-ADM-FILT-1001"));

        mockMvc.perform(get("/api/admin/payments")
                        .header(AuthHeaders.USER_ID, "900")
                        .header(AuthHeaders.USERNAME, "admin")
                        .header(AuthHeaders.USER_ROLE, "ADMIN")
                        .param("orderNo", "ORD-ADM-FILT-1002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].paymentNo").value("PAY-ADM-FILT-1002"));
    }

    @Test
    void adminPaymentListFiltersByPaidRange() throws Exception {
        saveOrder("ORD-ADM-RANGE-1001", 621L, "39.80");
        saveOrder("ORD-ADM-RANGE-1002", 622L, "18.50");
        savePaidPayment("PAY-ADM-RANGE-1001", "ORD-ADM-RANGE-1001", "39.80",
                LocalDateTime.of(2026, 5, 18, 10, 0), LocalDateTime.of(2026, 5, 18, 10, 5));
        savePaidPayment("PAY-ADM-RANGE-1002", "ORD-ADM-RANGE-1002", "18.50",
                LocalDateTime.of(2026, 5, 25, 10, 0), LocalDateTime.of(2026, 5, 25, 10, 5));

        mockMvc.perform(get("/api/admin/payments")
                        .header(AuthHeaders.USER_ID, "900")
                        .header(AuthHeaders.USERNAME, "admin")
                        .header(AuthHeaders.USER_ROLE, "ADMIN")
                        .param("paidFrom", "2026-05-24T00:00:00")
                        .param("paidTo", "2026-05-26T00:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].paymentNo").value("PAY-ADM-RANGE-1002"));
    }

    @Test
    void adminPaymentListRejectsInvalidStatus() throws Exception {
        mockMvc.perform(get("/api/admin/payments")
                        .header(AuthHeaders.USER_ID, "900")
                        .header(AuthHeaders.USERNAME, "admin")
                        .header(AuthHeaders.USER_ROLE, "ADMIN")
                        .param("status", "NOT_A_STATUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.getCode()))
                .andExpect(jsonPath("$.message").value("Invalid status"));
    }

    @Test
    void adminPaymentListRejectsInvalidPaidRange() throws Exception {
        mockMvc.perform(get("/api/admin/payments")
                        .header(AuthHeaders.USER_ID, "900")
                        .header(AuthHeaders.USERNAME, "admin")
                        .header(AuthHeaders.USER_ROLE, "ADMIN")
                        .param("paidFrom", "2026-05-22T00:00:00")
                        .param("paidTo", "2026-05-21T00:00:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.getCode()))
                .andExpect(jsonPath("$.message").value("paidFrom must be before or equal to paidTo"));
    }

    @Test
    void adminPaymentListBoundsPageSize() throws Exception {
        saveOrder("ORD-ADM-BOUND-1001", 631L, "39.80");
        savePaidPayment("PAY-ADM-BOUND-1001", "ORD-ADM-BOUND-1001", "39.80",
                LocalDateTime.of(2026, 5, 20, 10, 0), LocalDateTime.of(2026, 5, 20, 10, 5));

        mockMvc.perform(get("/api/admin/payments")
                        .header(AuthHeaders.USER_ID, "900")
                        .header(AuthHeaders.USERNAME, "admin")
                        .header(AuthHeaders.USER_ROLE, "ADMIN")
                        .param("size", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(100))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void adminPaymentDetailByPaymentNoReturnsForAdmin() throws Exception {
        saveOrder("ORD-ADM-DET-1001", 641L, "39.80");
        savePaidPayment("PAY-ADM-DET-1001", "ORD-ADM-DET-1001", "39.80",
                LocalDateTime.of(2026, 5, 20, 10, 0), LocalDateTime.of(2026, 5, 20, 10, 5));

        mockMvc.perform(get("/api/admin/payments/PAY-ADM-DET-1001")
                        .header(AuthHeaders.USER_ID, "900")
                        .header(AuthHeaders.USERNAME, "admin")
                        .header(AuthHeaders.USER_ROLE, "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentNo").value("PAY-ADM-DET-1001"))
                .andExpect(jsonPath("$.data.orderNo").value("ORD-ADM-DET-1001"))
                .andExpect(jsonPath("$.data.userId").value(641))
                .andExpect(jsonPath("$.data.productId").value("SKU-ORD-ADM-DET-1001"))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.paidAt").exists());
    }

    @Test
    void adminPaymentDetailByPaymentNoReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/admin/payments/PAY-NO-SUCH")
                        .header(AuthHeaders.USER_ID, "900")
                        .header(AuthHeaders.USERNAME, "admin")
                        .header(AuthHeaders.USER_ROLE, "ADMIN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message").value("Payment not found"));
    }

    @Test
    void adminPaymentLookupByOrderNoReturnsForAdmin() throws Exception {
        saveOrder("ORD-ADM-LOOK-1001", 651L, "18.50");
        savePaidPayment("PAY-ADM-LOOK-1001", "ORD-ADM-LOOK-1001", "18.50",
                LocalDateTime.of(2026, 5, 20, 10, 0), LocalDateTime.of(2026, 5, 20, 10, 5));

        mockMvc.perform(get("/api/admin/payments/order/ORD-ADM-LOOK-1001")
                        .header(AuthHeaders.USER_ID, "900")
                        .header(AuthHeaders.USERNAME, "admin")
                        .header(AuthHeaders.USER_ROLE, "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentNo").value("PAY-ADM-LOOK-1001"))
                .andExpect(jsonPath("$.data.orderNo").value("ORD-ADM-LOOK-1001"))
                .andExpect(jsonPath("$.data.userId").value(651));
    }

    @Test
    void adminPaymentLookupByOrderNoReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/admin/payments/order/ORD-NO-SUCH")
                        .header(AuthHeaders.USER_ID, "900")
                        .header(AuthHeaders.USERNAME, "admin")
                        .header(AuthHeaders.USER_ROLE, "ADMIN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message").value("Payment not found"));
    }

    @Test
    void adminPaymentDetailRejectsUserRole() throws Exception {
        saveOrder("ORD-ADM-DET-FORBID", 642L, "39.80");
        savePaidPayment("PAY-ADM-DET-FORBID", "ORD-ADM-DET-FORBID", "39.80",
                LocalDateTime.of(2026, 5, 20, 10, 0), LocalDateTime.of(2026, 5, 20, 10, 5));

        mockMvc.perform(get("/api/admin/payments/PAY-ADM-DET-FORBID")
                        .header(AuthHeaders.USER_ID, "642")
                        .header(AuthHeaders.USERNAME, "user")
                        .header(AuthHeaders.USER_ROLE, "USER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));
    }

    @Test
    void customerPaymentQueryByOrderNoStillWorks() throws Exception {
        saveOrder("ORD-CUST-PAY-1001", 701L, "9.99");
        savePaidPayment("PAY-CUST-1001", "ORD-CUST-PAY-1001", "9.99",
                LocalDateTime.of(2026, 5, 20, 10, 0), LocalDateTime.of(2026, 5, 20, 10, 5));

        mockMvc.perform(get("/api/payments/ORD-CUST-PAY-1001")
                        .header(AuthHeaders.USER_ID, "701")
                        .header(AuthHeaders.USERNAME, "customer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.paymentNo").value("PAY-CUST-1001"))
                .andExpect(jsonPath("$.data.orderNo").value("ORD-CUST-PAY-1001"))
                .andExpect(jsonPath("$.data.userId").value(701))
                .andExpect(jsonPath("$.data.createdAt").doesNotExist());
    }

    private void saveOrder(String orderNo, Long userId, String totalAmount) {
        orderRepository.saveAndFlush(new Order(
                orderNo, userId, "SKU-" + orderNo, OrderStatus.PAID, new BigDecimal(totalAmount)));
    }

    private void savePaidPayment(
            String paymentNo, String orderNo, String amount, LocalDateTime createdAt, LocalDateTime paidAt) {
        Payment payment = new Payment(
                paymentNo, orderNo, new BigDecimal(amount), PaymentChannel.MOCK, "idem-" + paymentNo);
        payment.markSuccess(paidAt);
        paymentRepository.saveAndFlush(payment);
        setCreatedAt(paymentNo, createdAt);
    }

    private void savePendingPayment(String paymentNo, String orderNo, String amount, LocalDateTime createdAt) {
        paymentRepository.saveAndFlush(new Payment(
                paymentNo, orderNo, new BigDecimal(amount), PaymentChannel.MOCK, "idem-" + paymentNo));
        setCreatedAt(paymentNo, createdAt);
    }

    private void setCreatedAt(String paymentNo, LocalDateTime createdAt) {
        jdbcTemplate.update(
                "update payments set created_at = ?, updated_at = ? where payment_no = ?",
                Timestamp.valueOf(createdAt),
                Timestamp.valueOf(createdAt),
                paymentNo);
    }
}
