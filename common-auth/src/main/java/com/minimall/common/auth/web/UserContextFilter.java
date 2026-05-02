package com.minimall.common.auth.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minimall.common.auth.constants.AuthHeaders;
import com.minimall.common.auth.context.UserContext;
import com.minimall.common.auth.context.UserContextHolder;
import com.minimall.common.auth.jwt.JwtUtils;
import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.common.core.response.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class UserContextFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtils jwtUtils;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public UserContextFilter(JwtUtils jwtUtils) {
        this.jwtUtils = jwtUtils;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            UserContext userContext = resolveUserContext(request);
            if (userContext != null) {
                UserContextHolder.set(userContext);
            }
            filterChain.doFilter(request, response);
        } catch (BusinessException exception) {
            writeUnauthorized(response, exception.getMessage());
        } finally {
            UserContextHolder.clear();
        }
    }

    private UserContext resolveUserContext(HttpServletRequest request) {
        UserContext headerUserContext = resolveFromHeaders(request);
        if (headerUserContext != null) {
            return headerUserContext;
        }

        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        if (!authorization.trim().regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }
        if (jwtUtils == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "JWT parser is not configured");
        }
        return jwtUtils.parseToken(authorization);
    }

    private UserContext resolveFromHeaders(HttpServletRequest request) {
        String userIdHeader = request.getHeader(AuthHeaders.USER_ID);
        String username = request.getHeader(AuthHeaders.USERNAME);
        boolean hasUserId = userIdHeader != null && !userIdHeader.isBlank();
        boolean hasUsername = username != null && !username.isBlank();

        if (!hasUserId && !hasUsername) {
            return null;
        }
        if (!hasUserId || !hasUsername) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid user propagation headers");
        }

        try {
            return UserContext.of(Long.valueOf(userIdHeader), username);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid user propagation headers", exception);
        }
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.failure(ErrorCode.UNAUTHORIZED, message));
    }
}
