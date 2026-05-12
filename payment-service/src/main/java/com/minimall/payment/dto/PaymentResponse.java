package com.minimall.payment.dto;

import com.minimall.payment.domain.Payment;
import com.minimall.payment.domain.PaymentChannel;
import com.minimall.payment.domain.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
        String paymentNo,
        String orderNo,
        PaymentStatus status,
        BigDecimal amount,
        PaymentChannel channel,
        LocalDateTime paidAt) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getPaymentNo(),
                payment.getOrderNo(),
                payment.getStatus(),
                payment.getAmount(),
                payment.getChannel(),
                payment.getPaidAt());
    }
}
