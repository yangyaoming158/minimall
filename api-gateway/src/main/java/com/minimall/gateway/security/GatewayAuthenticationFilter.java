package com.minimall.gateway.security;

import com.minimall.common.auth.constants.AuthHeaders;
import com.minimall.common.auth.context.AuthRole;
import com.minimall.common.auth.context.UserContext;
import com.minimall.common.auth.jwt.JwtUtils;
import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.gateway.web.GatewayErrorResponseWriter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class GatewayAuthenticationFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String USER_LOGIN_PATH = "/api/users/login";
    private static final String USER_REGISTER_PATH = "/api/users/register";
    private static final String ADMIN_PATH = "/api/admin";
    private static final String ADMIN_PATH_PREFIX = "/api/admin/";
    private static final String ADMIN_LOGIN_PATH = "/api/admin/login";
    private static final String PRODUCTS_PATH = "/api/products";
    private static final String PRODUCTS_PATH_PREFIX = "/api/products/";
    private static final String INVENTORIES_PATH_PREFIX = "/api/inventories/";

    private final JwtUtils jwtUtils;
    private final GatewayErrorResponseWriter errorResponseWriter;

    public GatewayAuthenticationFilter(JwtUtils jwtUtils, GatewayErrorResponseWriter errorResponseWriter) {
        this.jwtUtils = jwtUtils;
        this.errorResponseWriter = errorResponseWriter;
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
            return errorResponseWriter.unauthorized(sanitizedExchange, "Missing token");
        }
        if (!authorization.trim().regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return errorResponseWriter.unauthorized(sanitizedExchange, "Missing bearer token");
        }

        try {
            UserContext userContext = jwtUtils.parseToken(authorization);
            if (isAdminPath(sanitizedRequest.getURI().getPath()) && !AuthRole.ADMIN.equals(userContext.getRole())) {
                return errorResponseWriter.forbidden(sanitizedExchange, ErrorCode.FORBIDDEN.getMessage());
            }

            ServerHttpRequest authenticatedRequest = sanitizedRequest.mutate()
                    .headers(headers -> {
                        headers.set(AuthHeaders.USER_ID, String.valueOf(userContext.getUserId()));
                        headers.set(AuthHeaders.USERNAME, userContext.getUsername());
                        headers.set(AuthHeaders.USER_ROLE, userContext.getRole().name());
                    })
                    .build();
            return chain.filter(sanitizedExchange.mutate().request(authenticatedRequest).build());
        } catch (BusinessException exception) {
            return errorResponseWriter.write(sanitizedExchange, statusFor(exception.getErrorCode()),
                    exception.getErrorCode(), exception.getMessage());
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
                    headers.remove(AuthHeaders.USER_ROLE);
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

        if (USER_LOGIN_PATH.equals(path) || USER_REGISTER_PATH.equals(path)) {
            return true;
        }

        if (ADMIN_LOGIN_PATH.equals(path) && HttpMethod.POST.equals(request.getMethod())) {
            return true;
        }

        return isPublicCatalogRead(request, path);
    }

    private boolean isAdminPath(String path) {
        return ADMIN_PATH.equals(path) || path.startsWith(ADMIN_PATH_PREFIX);
    }

    // Guests may browse the product catalog and check stock without a token
    // (Phase 1 PRD 4.1). Only GET reads are public; product mutations
    // (create/update/on-shelf/off-shelf) and all inventory writes still require
    // authentication because they are not GET requests.
    private boolean isPublicCatalogRead(ServerHttpRequest request, String path) {
        if (!HttpMethod.GET.equals(request.getMethod())) {
            return false;
        }
        return PRODUCTS_PATH.equals(path)
                || path.startsWith(PRODUCTS_PATH_PREFIX)
                || path.startsWith(INVENTORIES_PATH_PREFIX);
    }

    private HttpStatus statusFor(ErrorCode errorCode) {
        if (ErrorCode.FORBIDDEN.equals(errorCode)) {
            return HttpStatus.FORBIDDEN;
        }
        return HttpStatus.UNAUTHORIZED;
    }
}
