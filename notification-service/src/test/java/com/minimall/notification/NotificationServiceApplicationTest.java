package com.minimall.notification;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:notification_service_context;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.rabbitmq.host=127.0.0.1",
        "spring.rabbitmq.port=5672",
        "spring.rabbitmq.username=guest",
        "spring.rabbitmq.password=guest",
        "spring.rabbitmq.virtual-host=/",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
class NotificationServiceApplicationTest {

    @Autowired
    private MessageConverter messageConverter;

    @Autowired
    private Declarables paymentRabbitTopologyDeclarables;

    @Test
    void contextLoadsWithRabbitJsonConverterAndPaymentTopology() {
        assertThat(messageConverter).isInstanceOf(Jackson2JsonMessageConverter.class);
        assertThat(paymentRabbitTopologyDeclarables.getDeclarables()).hasSize(5);
    }
}
