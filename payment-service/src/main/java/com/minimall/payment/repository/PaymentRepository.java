package com.minimall.payment.repository;

import com.minimall.payment.domain.Payment;
import com.minimall.payment.domain.PaymentStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPaymentNo(String paymentNo);

    Optional<Payment> findByOrderNo(String orderNo);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    boolean existsByOrderNoAndStatus(String orderNo, PaymentStatus status);
}
