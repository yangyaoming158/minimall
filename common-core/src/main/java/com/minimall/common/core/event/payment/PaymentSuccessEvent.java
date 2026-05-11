package com.minimall.common.core.event.payment;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public final class PaymentSuccessEvent {

    public static final int CURRENT_VERSION = 1;

    private final String eventId;
    private final String orderNo;
    private final String paymentNo;
    private final BigDecimal amount;
    private final Instant paidAt;
    private final int version;

    public PaymentSuccessEvent(String eventId, String orderNo, String paymentNo, BigDecimal amount, Instant paidAt) {
        this(eventId, orderNo, paymentNo, amount, paidAt, CURRENT_VERSION);
    }

    @JsonCreator
    public PaymentSuccessEvent(
            @JsonProperty(value = "eventId", required = true) String eventId,
            @JsonProperty(value = "orderNo", required = true) String orderNo,
            @JsonProperty(value = "paymentNo", required = true) String paymentNo,
            @JsonProperty(value = "amount", required = true) BigDecimal amount,
            @JsonProperty(value = "paidAt", required = true) Instant paidAt,
            @JsonProperty(value = "version") Integer version) {
        this.eventId = requireText(eventId, "eventId must not be blank");
        this.orderNo = requireText(orderNo, "orderNo must not be blank");
        this.paymentNo = requireText(paymentNo, "paymentNo must not be blank");
        this.amount = Objects.requireNonNull(amount, "amount must not be null");
        this.paidAt = Objects.requireNonNull(paidAt, "paidAt must not be null");
        this.version = version == null ? CURRENT_VERSION : version;
    }

    public String getEventId() {
        return eventId;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public String getPaymentNo() {
        return paymentNo;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public int getVersion() {
        return version;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
