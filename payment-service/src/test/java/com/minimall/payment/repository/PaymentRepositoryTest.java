package com.minimall.payment.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minimall.payment.domain.Payment;
import com.minimall.payment.domain.PaymentChannel;
import com.minimall.payment.domain.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class PaymentRepositoryTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void savesPaymentAndFindsByBusinessKeys() {
        Payment saved = paymentRepository.saveAndFlush(new Payment(
                "PAY-1001",
                "ORD-PAY-1001",
                new BigDecimal("39.80"),
                PaymentChannel.MOCK,
                "idem-pay-1001"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(paymentRepository.findByPaymentNo("PAY-1001")).isPresent();
        assertThat(paymentRepository.findByOrderNo("ORD-PAY-1001"))
                .isPresent()
                .get()
                .satisfies(payment -> {
                    assertThat(payment.getAmount()).isEqualByComparingTo("39.80");
                    assertThat(payment.getChannel()).isEqualTo(PaymentChannel.MOCK);
                    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
                });
        assertThat(paymentRepository.findByIdempotencyKey("idem-pay-1001")).isPresent();
        assertThat(paymentRepository.existsByOrderNoAndStatus("ORD-PAY-1001", PaymentStatus.PENDING)).isTrue();
    }

    @Test
    void persistsEnumsAsStableNamesAndSuccessTimestamp() {
        LocalDateTime paidAt = LocalDateTime.of(2026, 5, 12, 9, 30);
        Payment payment = new Payment(
                "PAY-1002",
                "ORD-PAY-1002",
                new BigDecimal("9.99"),
                PaymentChannel.MOCK,
                "idem-pay-1002");
        payment.markSuccess(paidAt);
        paymentRepository.saveAndFlush(payment);

        String status = jdbcTemplate.queryForObject(
                "select status from payments where payment_no = ?",
                String.class,
                "PAY-1002");
        String channel = jdbcTemplate.queryForObject(
                "select channel from payments where payment_no = ?",
                String.class,
                "PAY-1002");

        assertThat(status).isEqualTo("SUCCESS");
        assertThat(channel).isEqualTo("MOCK");
        assertThat(paymentRepository.findByPaymentNo("PAY-1002"))
                .isPresent()
                .get()
                .extracting(Payment::getPaidAt)
                .isEqualTo(paidAt);
    }

    @Test
    void duplicatePaymentNoViolatesUniqueConstraint() {
        paymentRepository.saveAndFlush(new Payment(
                "PAY-DUP",
                "ORD-PAY-DUP-1",
                new BigDecimal("1.00"),
                PaymentChannel.MOCK,
                "idem-pay-dup-1"));

        assertThatThrownBy(() -> paymentRepository.saveAndFlush(new Payment(
                "PAY-DUP",
                "ORD-PAY-DUP-2",
                new BigDecimal("2.00"),
                PaymentChannel.MOCK,
                "idem-pay-dup-2")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicateOrderNoViolatesUniqueConstraint() {
        paymentRepository.saveAndFlush(new Payment(
                "PAY-ORDER-DUP-1",
                "ORD-PAY-DUP",
                new BigDecimal("1.00"),
                PaymentChannel.MOCK,
                "idem-order-dup-1"));

        assertThatThrownBy(() -> paymentRepository.saveAndFlush(new Payment(
                "PAY-ORDER-DUP-2",
                "ORD-PAY-DUP",
                new BigDecimal("2.00"),
                PaymentChannel.MOCK,
                "idem-order-dup-2")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicateIdempotencyKeyViolatesUniqueConstraint() {
        paymentRepository.saveAndFlush(new Payment(
                "PAY-IDEM-DUP-1",
                "ORD-IDEM-DUP-1",
                new BigDecimal("1.00"),
                PaymentChannel.MOCK,
                "idem-pay-dup"));

        assertThatThrownBy(() -> paymentRepository.saveAndFlush(new Payment(
                "PAY-IDEM-DUP-2",
                "ORD-IDEM-DUP-2",
                new BigDecimal("2.00"),
                PaymentChannel.MOCK,
                "idem-pay-dup")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
