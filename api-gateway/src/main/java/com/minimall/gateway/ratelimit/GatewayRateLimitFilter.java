package com.minimall.gateway.ratelimit;

import com.minimall.common.auth.constants.AuthHeaders;
import com.minimall.gateway.config.GatewayRateLimitProperties;
import com.minimall.gateway.web.GatewayErrorResponseWriter;
import java.net.InetSocketAddress;
import java.util.List;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class GatewayRateLimitFilter implements GlobalFilter, Ordered {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";

    private final GatewayRateLimitProperties properties;
    private final GatewayRateLimiter rateLimiter;
    private final GatewayErrorResponseWriter errorResponseWriter;

    public GatewayRateLimitFilter(
            GatewayRateLimitProperties properties,
            GatewayRateLimiter rateLimiter,
            GatewayErrorResponseWriter errorResponseWriter) {
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        if (shouldSkip(request)) {
            return chain.filter(exchange);
        }

        String key = resolveKey(request);
        return rateLimiter.isAllowed(key)
                .flatMap(result -> {
                    if (result.allowed()) {
                        return chain.filter(exchange);
                    }
                    return errorResponseWriter.tooManyRequests(exchange);
                });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 30;
    }

    private boolean shouldSkip(ServerHttpRequest request) {
        return !properties.isEnabled()
                || !request.getURI().getPath().startsWith("/api/")
                || HttpMethod.OPTIONS.equals(request.getMethod());
    }

    private String resolveKey(ServerHttpRequest request) {
        String userId = request.getHeaders().getFirst(AuthHeaders.USER_ID);
        if (userId != null && !userId.isBlank()) {
            return "user:" + userId.trim();
        }

        // X-Forwarded-For is client-controllable, so trusting it lets an attacker
        // rotate the rate-limit key freely (e.g. to brute-force /api/admin/login).
        // Only honor it when the gateway is explicitly told it sits behind a
        // trusted reverse proxy; otherwise key on the real socket address.
        if (properties.isTrustForwardedFor()) {
            String forwardedFor = firstForwardedFor(request.getHeaders());
            if (forwardedFor != null) {
                return "ip:" + forwardedFor;
            }
        }

        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return "ip:" + remoteAddress.getAddress().getHostAddress();
        }
        return "ip:unknown";
    }

    private String firstForwardedFor(HttpHeaders headers) {
        List<String> forwardedForHeaders = headers.get(X_FORWARDED_FOR);
        if (forwardedForHeaders == null || forwardedForHeaders.isEmpty()) {
            return null;
        }

        String firstHeader = forwardedForHeaders.get(0);
        if (firstHeader == null || firstHeader.isBlank()) {
            return null;
        }

        String firstAddress = firstHeader.split(",", 2)[0].trim();
        return firstAddress.isBlank() ? null : firstAddress;
    }
}
