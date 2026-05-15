package com.minimall.gateway.web;

import com.minimall.gateway.config.GatewayCorsProperties;
import java.util.List;
import java.util.Locale;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.PatternMatchUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

public class GatewayCorsWebFilter implements WebFilter, Ordered {

    private final GatewayCorsProperties properties;

    public GatewayCorsWebFilter(GatewayCorsProperties properties) {
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        if (!isApiRequest(request) || !isCorsRequest(request)) {
            return chain.filter(exchange);
        }

        String origin = request.getHeaders().getOrigin();
        String allowedOrigin = allowedOrigin(origin);
        if (allowedOrigin == null) {
            return reject(exchange);
        }

        ServerHttpResponse response = exchange.getResponse();
        addVaryHeaders(response);
        response.getHeaders().setAccessControlAllowOrigin(allowedOrigin);
        if (properties.isAllowCredentials()) {
            response.getHeaders().setAccessControlAllowCredentials(true);
        }
        if (!properties.getExposedHeaders().isEmpty()) {
            response.getHeaders().setAccessControlExposeHeaders(properties.getExposedHeaders());
        }

        if (isPreflightRequest(request)) {
            return handlePreflight(exchange, request, response);
        }

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private Mono<Void> handlePreflight(
            ServerWebExchange exchange,
            ServerHttpRequest request,
            ServerHttpResponse response) {
        String requestedMethod = request.getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD);
        if (!isAllowedMethod(requestedMethod)) {
            return reject(exchange);
        }

        List<String> requestedHeaders = request.getHeaders().getAccessControlRequestHeaders();
        if (!areAllowedHeaders(requestedHeaders)) {
            return reject(exchange);
        }

        response.getHeaders().setAccessControlAllowMethods(allowedResponseMethods(requestedMethod));
        response.getHeaders().setAccessControlAllowHeaders(allowedResponseHeaders(requestedHeaders));
        response.getHeaders().setAccessControlMaxAge(properties.getMaxAge());
        response.setStatusCode(HttpStatus.OK);
        return response.setComplete();
    }

    private boolean isApiRequest(ServerHttpRequest request) {
        return request.getURI().getPath().startsWith("/api/");
    }

    private boolean isCorsRequest(ServerHttpRequest request) {
        return request.getHeaders().getOrigin() != null;
    }

    private boolean isPreflightRequest(ServerHttpRequest request) {
        return HttpMethod.OPTIONS.equals(request.getMethod())
                && request.getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD) != null;
    }

    private String allowedOrigin(String origin) {
        if (origin == null || origin.isBlank()) {
            return null;
        }
        if (properties.getAllowedOrigins().contains("*")) {
            return properties.isAllowCredentials() ? origin : "*";
        }
        if (properties.getAllowedOrigins().contains(origin)) {
            return origin;
        }
        return properties.getAllowedOriginPatterns().stream()
                .map(pattern -> pattern.replace("[*]", "*"))
                .anyMatch(pattern -> PatternMatchUtils.simpleMatch(pattern, origin))
                ? origin
                : null;
    }

    private boolean isAllowedMethod(String requestedMethod) {
        if (requestedMethod == null || requestedMethod.isBlank()) {
            return false;
        }
        List<String> allowedMethods = properties.getAllowedMethods();
        return allowedMethods.contains("*") || allowedMethods.stream()
                .anyMatch(method -> method.equalsIgnoreCase(requestedMethod));
    }

    private boolean areAllowedHeaders(List<String> requestedHeaders) {
        if (requestedHeaders.isEmpty() || properties.getAllowedHeaders().contains("*")) {
            return true;
        }
        return requestedHeaders.stream().allMatch(requestedHeader -> properties.getAllowedHeaders().stream()
                .anyMatch(allowedHeader -> allowedHeader.equalsIgnoreCase(requestedHeader)));
    }

    private List<String> allowedResponseHeaders(List<String> requestedHeaders) {
        if (properties.getAllowedHeaders().contains("*")) {
            return requestedHeaders;
        }
        return properties.getAllowedHeaders();
    }

    private List<HttpMethod> allowedResponseMethods(String requestedMethod) {
        if (properties.getAllowedMethods().contains("*")) {
            return List.of(HttpMethod.valueOf(requestedMethod.toUpperCase(Locale.ROOT)));
        }
        return properties.getAllowedMethods().stream()
                .map(method -> HttpMethod.valueOf(method.toUpperCase(Locale.ROOT)))
                .toList();
    }

    private void addVaryHeaders(ServerHttpResponse response) {
        response.getHeaders().add(HttpHeaders.VARY, HttpHeaders.ORIGIN);
        response.getHeaders().add(HttpHeaders.VARY, HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD);
        response.getHeaders().add(HttpHeaders.VARY, HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS);
    }

    private Mono<Void> reject(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        return exchange.getResponse().setComplete();
    }
}
