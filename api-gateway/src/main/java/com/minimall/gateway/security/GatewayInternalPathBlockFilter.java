package com.minimall.gateway.security;

import com.minimall.gateway.web.GatewayErrorResponseWriter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class GatewayInternalPathBlockFilter implements WebFilter, Ordered {

    private static final String INTERNAL_PATH = "/internal";

    private final GatewayErrorResponseWriter errorResponseWriter;

    public GatewayInternalPathBlockFilter(GatewayErrorResponseWriter errorResponseWriter) {
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (INTERNAL_PATH.equals(path) || path.startsWith(INTERNAL_PATH + "/")) {
            return errorResponseWriter.forbidden(exchange, "Internal API is not exposed");
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
