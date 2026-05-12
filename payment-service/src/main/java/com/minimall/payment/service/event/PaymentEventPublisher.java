package com.minimall.payment.service.event;

import com.minimall.common.core.event.payment.PaymentEventNames;
import com.minimall.common.core.event.payment.PaymentSuccessEvent;
import com.minimall.payment.domain.Payment;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public PaymentEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishSuccess(Payment payment) {
        PaymentSuccessEvent event = new PaymentSuccessEvent(
                UUID.randomUUID().toString(),
                payment.getOrderNo(),
                payment.getPaymentNo(),
                payment.getAmount().setScale(2, RoundingMode.UNNECESSARY),
                payment.getPaidAt().atZone(ZoneId.systemDefault()).toInstant());
        rabbitTemplate.convertAndSend(
                PaymentEventNames.PAYMENT_EXCHANGE,
                PaymentEventNames.PAYMENT_SUCCESS_ROUTING_KEY,
                event);
    }
}
