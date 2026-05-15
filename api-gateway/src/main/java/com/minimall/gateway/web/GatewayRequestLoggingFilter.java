package com.minimall.gateway.web;

import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
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
        return chain.filter(exchange)
                .doFinally(signalType -> logRequest(exchange, startedAt));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    private void logRequest(ServerWebExchange exchange, long startedAt) {
        ServerHttpRequest request = exchange.getRequest();
        HttpMethod method = request.getMethod();
        String path = request.getURI().getPath();
        HttpStatusCode status = exchange.getResponse().getStatusCode();
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        log.info("gateway request method={} path={} status={} durationMs={}",
                method == null ? "UNKNOWN" : method.name(),
                path,
                status == null ? HttpStatus.OK.value() : status.value(),
                durationMs);
    }
}
