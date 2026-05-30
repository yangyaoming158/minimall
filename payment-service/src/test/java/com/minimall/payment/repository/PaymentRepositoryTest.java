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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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
    void findAllBySpecificationFiltersByStatusAndPaidRangeNewestFirst() {
        LocalDateTime base = LocalDateTime.of(2026, 5, 20, 0, 0);
        // SUCCESS, paid inside range.
        savePaid("PAY-SPEC-1001", "ORD-SPEC-1001", base.plusDays(1), base.plusHours(10));
        // SUCCESS, paid inside range, newer createdAt.
        savePaid("PAY-SPEC-1002", "ORD-SPEC-1002", base.plusDays(3), base.plusHours(20));
        // SUCCESS, paid OUTSIDE range -> excluded.
        savePaid("PAY-SPEC-1003", "ORD-SPEC-1003", base.plusHours(5), base.plusDays(30));
        // PENDING (paidAt null) -> excluded by status and by paid range.
        paymentRepository.saveAndFlush(new Payment(
                "PAY-SPEC-1004", "ORD-SPEC-1004", new BigDecimal("5.00"), PaymentChannel.MOCK, "idem-spec-1004"));

        Specification<Payment> specification = (root, query, cb) -> cb.and(
                cb.equal(root.get("status"), PaymentStatus.SUCCESS),
                cb.greaterThanOrEqualTo(root.get("paidAt"), base),
                cb.lessThanOrEqualTo(root.get("paidAt"), base.plusDays(5)));

        Page<Payment> page = paymentRepository.findAll(specification,
                PageRequest.of(0, 10, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent())
                .extracting(Payment::getPaymentNo)
                .containsExactly("PAY-SPEC-1002", "PAY-SPEC-1001");
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

    private void savePaid(String paymentNo, String orderNo, LocalDateTime createdAt, LocalDateTime paidAt) {
        Payment payment = new Payment(
                paymentNo, orderNo, new BigDecimal("9.99"), PaymentChannel.MOCK, "idem-" + paymentNo);
        payment.markSuccess(paidAt);
        paymentRepository.saveAndFlush(payment);
        jdbcTemplate.update(
                "update payments set created_at = ? where payment_no = ?",
                java.sql.Timestamp.valueOf(createdAt),
                paymentNo);
    }
}
