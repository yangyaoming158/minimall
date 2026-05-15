package com.minimall.gateway.ratelimit;

public record GatewayRateLimitResult(boolean allowed, long remainingTokens) {

    public static GatewayRateLimitResult allowed(long remainingTokens) {
        return new GatewayRateLimitResult(true, remainingTokens);
    }

    public static GatewayRateLimitResult denied(long remainingTokens) {
        return new GatewayRateLimitResult(false, remainingTokens);
    }
}
