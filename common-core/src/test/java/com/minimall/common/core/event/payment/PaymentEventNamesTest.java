package com.minimall.common.core.event.payment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PaymentEventNamesTest {

    @Test
    void exposesStablePaymentSuccessTopologyNames() {
        assertEquals("minimall.payment.exchange", PaymentEventNames.PAYMENT_EXCHANGE);
        assertEquals("payment.success", PaymentEventNames.PAYMENT_SUCCESS_ROUTING_KEY);
        assertEquals("minimall.order.payment-success.queue", PaymentEventNames.ORDER_PAYMENT_SUCCESS_QUEUE);
        assertEquals("minimall.notification.payment-success.queue", PaymentEventNames.NOTIFICATION_PAYMENT_SUCCESS_QUEUE);
    }
}
