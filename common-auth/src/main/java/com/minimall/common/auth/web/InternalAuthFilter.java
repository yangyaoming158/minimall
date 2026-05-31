package com.minimall.common.auth.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minimall.common.auth.constants.AuthHeaders;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.common.core.response.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Authenticates service-to-service traffic to {@code /internal/**}. These
 * endpoints are never exposed through the gateway (it blocks the path), so the
 * only legitimate callers are trusted internal services that present the shared
 * secret. Without a matching secret the request is rejected, which closes the
 * hole where a caller could reach internal stock mutations by hitting a service
 * port directly.
 *
 * <p>When no secret is configured the filter is inert (legacy behaviour); the
 * network boundary is then the only protection.
 */
public class InternalAuthFilter extends OncePerRequestFilter {

    private static final String INTERNAL_PATH_PREFIX = "/internal/";
    private static final String INTERNAL_PATH = "/internal";

    private final String internalSecret;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public InternalAuthFilter(String internalSecret) {
        this.internalSecret = internalSecret == null || internalSecret.isBlank() ? null : internalSecret;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (internalSecret != null && isInternalPath(request) && !secretMatches(request)) {
            writeForbidden(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isInternalPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path != null && (INTERNAL_PATH.equals(path) || path.startsWith(INTERNAL_PATH_PREFIX));
    }

    private boolean secretMatches(HttpServletRequest request) {
        return internalSecret.equals(request.getHeader(AuthHeaders.GATEWAY_TOKEN));
    }

    private void writeForbidden(HttpServletResponse response) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(
                response.getWriter(),
                ApiResponse.failure(ErrorCode.FORBIDDEN, "Internal API requires a trusted caller"));
    }
}
