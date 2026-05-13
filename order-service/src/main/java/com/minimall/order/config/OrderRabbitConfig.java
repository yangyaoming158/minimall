package com.minimall.order.config;

import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OrderRabbitConfig {

    @Bean
    public MessageConverter orderJsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
