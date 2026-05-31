package com.minimall.common.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Shared secret used to authenticate trusted, gateway-originated and
 * service-to-service traffic. When {@code secret} is blank the enforcement is
 * disabled (legacy behaviour, suitable for local single-process runs and unit
 * tests); the network layer is then the only boundary. When configured, the
 * gateway injects it on every forwarded request and internal callers must send
 * it, so a caller that bypasses the gateway cannot forge an admin identity or
 * reach {@code /internal/**}.
 */
@ConfigurationProperties(prefix = "minimall.auth.internal")
public class InternalAuthProperties {

    private String secret;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }
}
