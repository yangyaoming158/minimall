package com.minimall.order.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

class OrderTimeoutPropertiesTest {

    @Test
    void bindsTimeoutSchedulerSettings() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("minimall.order.timeout.enabled", "false")
                .withProperty("minimall.order.timeout.fixed-delay", "30000")
                .withProperty("minimall.order.timeout.batch-size", "25");

        OrderTimeoutProperties properties = Binder.get(environment)
                .bind("minimall.order.timeout", Bindable.of(OrderTimeoutProperties.class))
                .orElseThrow(() -> new AssertionError("Expected order timeout properties to bind"));

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getFixedDelay()).isEqualTo(30_000L);
        assertThat(properties.getBatchSize()).isEqualTo(25);
    }

    @Test
    void clampsInvalidNumericSettingsToOne() {
        OrderTimeoutProperties properties = new OrderTimeoutProperties();

        properties.setFixedDelay(0);
        properties.setBatchSize(0);

        assertThat(properties.getFixedDelay()).isEqualTo(1L);
        assertThat(properties.getBatchSize()).isEqualTo(1);
    }
}
