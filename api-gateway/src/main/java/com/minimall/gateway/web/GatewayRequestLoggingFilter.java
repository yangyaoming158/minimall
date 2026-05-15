package com.minimall.gateway.web;

import java.net.URI;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class GatewayRequestLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(GatewayRequestLoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startedAt = System.nanoTime();
        ServerHttpRequest request = exchange.getRequest();
        HttpMethod method = request.getMethod();
        String path = browserFacingPath(exchange, request.getURI().getPath());
        return chain.filter(exchange)
                .doFinally(signalType -> logRequest(exchange, method, path, startedAt));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    private void logRequest(ServerWebExchange exchange, HttpMethod method, String path, long startedAt) {
        HttpStatusCode status = exchange.getResponse().getStatusCode();
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        log.info("gateway request method={} path={} status={} durationMs={}",
                method == null ? "UNKNOWN" : method.name(),
                path,
                status == null ? HttpStatus.OK.value() : status.value(),
                durationMs);
    }

    private String browserFacingPath(ServerWebExchange exchange, String fallbackPath) {
        Set<URI> originalUris = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR);
        if (originalUris == null || originalUris.isEmpty()) {
            return fallbackPath;
        }
        return originalUris.iterator().next().getPath();
    }
}
