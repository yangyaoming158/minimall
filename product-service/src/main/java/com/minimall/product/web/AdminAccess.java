package com.minimall.product.web;

import com.minimall.common.auth.context.AuthRole;
import com.minimall.common.auth.context.UserContext;
import com.minimall.common.auth.context.UserContextHolder;
import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.product.service.ProductAdminAuditContext;
import jakarta.servlet.http.HttpServletRequest;

final class AdminAccess {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";
    private static final String USER_AGENT_HEADER = "User-Agent";

    private AdminAccess() {
    }

    static UserContext requireAdmin() {
        UserContext userContext = UserContextHolder.get()
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "Unauthorized"));
        if (!AuthRole.ADMIN.equals(userContext.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return userContext;
    }

    static ProductAdminAuditContext requireAdminAuditContext(HttpServletRequest request) {
        UserContext userContext = requireAdmin();
        return new ProductAdminAuditContext(
                userContext.getUserId(),
                userContext.getUsername(),
                header(request, REQUEST_ID_HEADER),
                clientIp(request),
                header(request, USER_AGENT_HEADER));
    }

    private static String clientIp(HttpServletRequest request) {
        String forwardedFor = header(request, FORWARDED_FOR_HEADER);
        if (forwardedFor != null) {
            int commaIndex = forwardedFor.indexOf(',');
            return commaIndex >= 0 ? forwardedFor.substring(0, commaIndex).trim() : forwardedFor;
        }
        String remoteAddress = request.getRemoteAddr();
        return remoteAddress == null || remoteAddress.isBlank() ? null : remoteAddress;
    }

    private static String header(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        return value == null || value.isBlank() ? null : value.trim();
    }
}
