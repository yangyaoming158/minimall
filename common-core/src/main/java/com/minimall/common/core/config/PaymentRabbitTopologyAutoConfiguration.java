package com.minimall.common.core.config;

import com.minimall.common.core.event.payment.PaymentEventNames;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(Declarables.class)
public class PaymentRabbitTopologyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "paymentRabbitTopologyDeclarables")
    public Declarables paymentRabbitTopologyDeclarables() {
        DirectExchange paymentExchange = ExchangeBuilder
                .directExchange(PaymentEventNames.PAYMENT_EXCHANGE)
                .durable(true)
                .build();
        Queue orderPaymentSuccessQueue = QueueBuilder
                .durable(PaymentEventNames.ORDER_PAYMENT_SUCCESS_QUEUE)
                .build();
        Queue notificationPaymentSuccessQueue = QueueBuilder
                .durable(PaymentEventNames.NOTIFICATION_PAYMENT_SUCCESS_QUEUE)
                .build();
        Binding orderPaymentSuccessBinding = BindingBuilder
                .bind(orderPaymentSuccessQueue)
                .to(paymentExchange)
                .with(PaymentEventNames.PAYMENT_SUCCESS_ROUTING_KEY);
        Binding notificationPaymentSuccessBinding = BindingBuilder
                .bind(notificationPaymentSuccessQueue)
                .to(paymentExchange)
                .with(PaymentEventNames.PAYMENT_SUCCESS_ROUTING_KEY);

        return new Declarables(
                paymentExchange,
                orderPaymentSuccessQueue,
                notificationPaymentSuccessQueue,
                orderPaymentSuccessBinding,
                notificationPaymentSuccessBinding);
    }
}
