package com.minimall.gateway.ratelimit;

import reactor.core.publisher.Mono;

public interface GatewayRateLimiter {

    Mono<GatewayRateLimitResult> isAllowed(String key);
}
