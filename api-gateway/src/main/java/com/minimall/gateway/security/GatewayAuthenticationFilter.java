package com.minimall.gateway.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minimall.common.auth.constants.AuthHeaders;
import com.minimall.common.auth.context.UserContext;
import com.minimall.common.auth.jwt.JwtUtils;
import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.common.core.response.ApiResponse;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class GatewayAuthenticationFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String USER_LOGIN_PATH = "/api/user/users/login";
    private static final String USER_REGISTER_PATH = "/api/user/users/register";

    private final JwtUtils jwtUtils;
    private final ObjectMapper objectMapper;

    public GatewayAuthenticationFilter(JwtUtils jwtUtils, ObjectMapper objectMapper) {
        this.jwtUtils = jwtUtils;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest sanitizedRequest = sanitizePropagationHeaders(exchange.getRequest());
        ServerWebExchange sanitizedExchange = exchange.mutate().request(sanitizedRequest).build();

        if (shouldBypassAuthentication(sanitizedRequest)) {
            return chain.filter(sanitizedExchange);
        }

        String authorization = sanitizedRequest.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || authorization.isBlank()) {
            return writeError(sanitizedExchange, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "Missing token");
        }
        if (!authorization.trim().regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return writeError(sanitizedExchange, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED,
                    "Missing bearer token");
        }

        try {
            UserContext userContext = jwtUtils.parseToken(authorization);
            ServerHttpRequest authenticatedRequest = sanitizedRequest.mutate()
                    .headers(headers -> {
                        headers.set(AuthHeaders.USER_ID, String.valueOf(userContext.getUserId()));
                        headers.set(AuthHeaders.USERNAME, userContext.getUsername());
                    })
                    .build();
            return chain.filter(sanitizedExchange.mutate().request(authenticatedRequest).build());
        } catch (BusinessException exception) {
            return writeError(sanitizedExchange, statusFor(exception.getErrorCode()), exception.getErrorCode(),
                    exception.getMessage());
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private ServerHttpRequest sanitizePropagationHeaders(ServerHttpRequest request) {
        return request.mutate()
                .headers(headers -> {
                    headers.remove(AuthHeaders.USER_ID);
                    headers.remove(AuthHeaders.USERNAME);
                })
                .build();
    }

    private boolean shouldBypassAuthentication(ServerHttpRequest request) {
        if (HttpMethod.OPTIONS.equals(request.getMethod())) {
            return true;
        }

        String path = request.getURI().getPath();
        if (!path.startsWith("/api/")) {
            return true;
        }

        return USER_LOGIN_PATH.equals(path) || USER_REGISTER_PATH.equals(path);
    }

    private HttpStatus statusFor(ErrorCode errorCode) {
        if (ErrorCode.FORBIDDEN.equals(errorCode)) {
            return HttpStatus.FORBIDDEN;
        }
        return HttpStatus.UNAUTHORIZED;
    }

    private Mono<Void> writeError(
            ServerWebExchange exchange,
            HttpStatus status,
            ErrorCode errorCode,
            String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(ApiResponse.failure(errorCode, message));
        } catch (JsonProcessingException exception) {
            return Mono.error(exception);
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }
}
