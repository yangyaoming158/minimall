package com.minimall.common.core.event.payment;

public final class PaymentEventNames {

    public static final String PAYMENT_EXCHANGE = "minimall.payment.exchange";
    public static final String PAYMENT_SUCCESS_ROUTING_KEY = "payment.success";
    public static final String ORDER_PAYMENT_SUCCESS_QUEUE = "minimall.order.payment-success.queue";
    public static final String NOTIFICATION_PAYMENT_SUCCESS_QUEUE = "minimall.notification.payment-success.queue";

    private PaymentEventNames() {
    }
}
