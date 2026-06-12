package com.minimall.payment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.minimall.payment.domain.Order;
import com.minimall.payment.domain.Payment;
import com.minimall.payment.domain.PaymentChannel;
import com.minimall.payment.domain.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Admin-facing payment view. Like the customer {@link PaymentResponse} it enriches a payment with
 * its order's {@code userId}/{@code productId} for operator context, and additionally exposes the
 * payment record timestamps. The order may be absent (null), in which case the enriched fields are
 * left null. Status and channel serialize as stable enum names.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminPaymentResponse(
        String paymentNo,
        String orderNo,
        Long userId,
        String productId,
        PaymentStatus status,
        BigDecimal amount,
        PaymentChannel channel,
        LocalDateTime paidAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static AdminPaymentResponse from(Payment payment, Order order) {
        return new AdminPaymentResponse(
                payment.getPaymentNo(),
                payment.getOrderNo(),
                order == null ? null : order.getUserId(),
                order == null ? null : order.getProductId(),
                payment.getStatus(),
                payment.getAmount(),
                payment.getChannel(),
                payment.getPaidAt(),
                payment.getCreatedAt(),
                payment.getUpdatedAt());
    }
}
