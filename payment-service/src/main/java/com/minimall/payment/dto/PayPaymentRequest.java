package com.minimall.payment.dto;

import com.minimall.payment.domain.PaymentChannel;
import jakarta.validation.constraints.Size;

public record PayPaymentRequest(
        PaymentChannel channel,
        @Size(max = 128, message = "idempotencyKey must be at most 128 characters")
        String idempotencyKey) {

    public PaymentChannel normalizedChannel() {
        return channel == null ? PaymentChannel.MOCK : channel;
    }
}
