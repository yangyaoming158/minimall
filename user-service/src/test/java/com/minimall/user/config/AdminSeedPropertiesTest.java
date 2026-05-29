package com.minimall.user.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

class AdminSeedPropertiesTest {

    @Test
    void bindsAdminSeedSettings() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("minimall.admin.seed.enabled", "true")
                .withProperty("minimall.admin.seed.username", "admin")
                .withProperty("minimall.admin.seed.password", "password123")
                .withProperty("minimall.admin.seed.email", "admin@example.com")
                .withProperty("minimall.admin.seed.phone", "13800000000");

        AdminSeedProperties properties = Binder.get(environment)
                .bind("minimall.admin.seed", Bindable.of(AdminSeedProperties.class))
                .orElseThrow(() -> new AssertionError("Expected admin seed properties to bind"));

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.hasCredentials()).isTrue();
        assertThat(properties.normalizedUsername()).isEqualTo("admin");
        assertThat(properties.getPassword()).isEqualTo("password123");
        assertThat(properties.getEmail()).isEqualTo("admin@example.com");
        assertThat(properties.getPhone()).isEqualTo("13800000000");
    }

    @Test
    void defaultsToDisabledAndMissingCredentials() {
        AdminSeedProperties properties = new AdminSeedProperties();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.hasCredentials()).isFalse();
    }
}
