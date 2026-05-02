package com.minimall.common.auth.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtPropertiesTest {

    @Test
    void storesJwtConfiguration() {
        JwtProperties properties = new JwtProperties();

        properties.setSecret("test-secret");
        properties.setExpireSeconds(120);

        assertThat(properties.getSecret()).isEqualTo("test-secret");
        assertThat(properties.getExpireSeconds()).isEqualTo(120);
    }

    @Test
    void usesDefaultExpiration() {
        JwtProperties properties = new JwtProperties();

        assertThat(properties.getExpireSeconds()).isEqualTo(3600);
    }
}
