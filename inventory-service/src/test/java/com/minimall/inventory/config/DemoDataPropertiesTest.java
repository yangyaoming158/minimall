package com.minimall.inventory.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

class DemoDataPropertiesTest {

    @Test
    void defaultsToDisabled() {
        DemoDataProperties properties = new DemoDataProperties();

        assertThat(properties.isEnabled()).isFalse();
    }

    @Test
    void bindsDemoDataSettings() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("minimall.demo-data.enabled", "true");

        DemoDataProperties properties = Binder.get(environment)
                .bind("minimall.demo-data", Bindable.of(DemoDataProperties.class))
                .orElseThrow(() -> new AssertionError("Expected demo data properties to bind"));

        assertThat(properties.isEnabled()).isTrue();
    }
}
