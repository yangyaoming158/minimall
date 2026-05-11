package com.minimall.common.core.config;

import com.minimall.common.core.event.payment.PaymentEventNames;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentRabbitTopologyAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PaymentRabbitTopologyAutoConfiguration.class));

    @Test
    void registersPaymentSuccessTopologyDeclarables() {
        contextRunner.run(context -> {
            Declarables declarables = context.getBean("paymentRabbitTopologyDeclarables", Declarables.class);

            List<DirectExchange> exchanges = declarables.getDeclarablesByType(DirectExchange.class);
            Map<String, Queue> queues = declarables.getDeclarablesByType(Queue.class)
                    .stream()
                    .collect(Collectors.toMap(Queue::getName, Function.identity()));
            List<Binding> bindings = declarables.getDeclarablesByType(Binding.class);

            assertEquals(1, exchanges.size());
            assertEquals(PaymentEventNames.PAYMENT_EXCHANGE, exchanges.get(0).getName());
            assertTrue(exchanges.get(0).isDurable());
            assertQueue(queues, PaymentEventNames.ORDER_PAYMENT_SUCCESS_QUEUE);
            assertQueue(queues, PaymentEventNames.NOTIFICATION_PAYMENT_SUCCESS_QUEUE);
            assertBinding(bindings, PaymentEventNames.ORDER_PAYMENT_SUCCESS_QUEUE);
            assertBinding(bindings, PaymentEventNames.NOTIFICATION_PAYMENT_SUCCESS_QUEUE);
        });
    }

    @Test
    void backsOffWhenCustomPaymentTopologyBeanExists() {
        Declarables customDeclarables = new Declarables();

        contextRunner
                .withBean("paymentRabbitTopologyDeclarables", Declarables.class, () -> customDeclarables)
                .run(context -> assertEquals(customDeclarables, context.getBean("paymentRabbitTopologyDeclarables")));
    }

    private static void assertQueue(Map<String, Queue> queues, String queueName) {
        Queue queue = queues.get(queueName);

        assertNotNull(queue);
        assertTrue(queue.isDurable());
    }

    private static void assertBinding(List<Binding> bindings, String queueName) {
        Binding binding = bindings.stream()
                .filter(candidate -> queueName.equals(candidate.getDestination()))
                .findFirst()
                .orElseThrow();

        assertTrue(binding.isDestinationQueue());
        assertEquals(PaymentEventNames.PAYMENT_EXCHANGE, binding.getExchange());
        assertEquals(PaymentEventNames.PAYMENT_SUCCESS_ROUTING_KEY, binding.getRoutingKey());
    }
}
