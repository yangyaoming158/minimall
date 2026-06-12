package com.minimall.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "minimall.gateway.rate-limit")
public class GatewayRateLimitProperties {

    private boolean enabled = true;
    private int replenishRate = 10;
    private int burstCapacity = 20;
    private int requestedTokens = 1;
    private String keyPrefix = "minimall:gateway:rate-limit";
    private boolean failOpen = true;
    private boolean trustForwardedFor = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getReplenishRate() {
        return replenishRate;
    }

    public void setReplenishRate(int replenishRate) {
        this.replenishRate = atLeastOne(replenishRate);
    }

    public int getBurstCapacity() {
        return burstCapacity;
    }

    public void setBurstCapacity(int burstCapacity) {
        this.burstCapacity = atLeastOne(burstCapacity);
    }

    public int getRequestedTokens() {
        return requestedTokens;
    }

    public void setRequestedTokens(int requestedTokens) {
        this.requestedTokens = atLeastOne(requestedTokens);
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        if (keyPrefix != null && !keyPrefix.isBlank()) {
            this.keyPrefix = keyPrefix.trim();
        }
    }

    public boolean isFailOpen() {
        return failOpen;
    }

    public void setFailOpen(boolean failOpen) {
        this.failOpen = failOpen;
    }

    public boolean isTrustForwardedFor() {
        return trustForwardedFor;
    }

    public void setTrustForwardedFor(boolean trustForwardedFor) {
        this.trustForwardedFor = trustForwardedFor;
    }

    public int ttlSeconds() {
        int refillWindowSeconds = (int) Math.ceil((double) burstCapacity / replenishRate);
        return Math.max(1, refillWindowSeconds * 2);
    }

    private int atLeastOne(int value) {
        return Math.max(1, value);
    }
}
