package com.minimall.user.web;

import com.minimall.common.auth.context.AuthRole;
import com.minimall.common.auth.context.UserContext;
import com.minimall.common.auth.context.UserContextHolder;
import com.minimall.common.core.audit.AdminAuditAction;
import com.minimall.common.core.audit.AdminAuditLogQuery;
import com.minimall.common.core.audit.AdminAuditLogResponse;
import com.minimall.common.core.audit.AdminAuditResourceType;
import com.minimall.common.core.audit.AdminAuditSourceType;
import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.common.core.response.ApiResponse;
import com.minimall.common.core.response.PageResponse;
import com.minimall.user.audit.AdminAuditLogQueryService;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

@RestController
@RequestMapping("/api/admin/audit-logs")
public class AdminAuditLogController {

    private final AdminAuditLogQueryService adminAuditLogQueryService;

    public AdminAuditLogController(AdminAuditLogQueryService adminAuditLogQueryService) {
        this.adminAuditLogQueryService = adminAuditLogQueryService;
    }

    @GetMapping
    public ApiResponse<PageResponse<AdminAuditLogResponse>> list(
            @RequestParam(name = "adminUserId", required = false) Long adminUserId,
            @RequestParam(name = "action", required = false) String action,
            @RequestParam(name = "resourceType", required = false) String resourceType,
            @RequestParam(name = "resourceId", required = false) String resourceId,
            @RequestParam(name = "requestId", required = false) String requestId,
            @RequestParam(name = "sourceType", required = false) String sourceType,
            @RequestParam(name = "referenceNo", required = false) String referenceNo,
            @RequestParam(name = "createdFrom", required = false) String createdFrom,
            @RequestParam(name = "createdTo", required = false) String createdTo,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        requireAdmin();
        return ApiResponse.success(adminAuditLogQueryService.query(buildQuery(
                adminUserId,
                action,
                resourceType,
                resourceId,
                requestId,
                sourceType,
                referenceNo,
                createdFrom,
                createdTo,
                page,
                size)));
    }

    private void requireAdmin() {
        UserContext userContext = UserContextHolder.get()
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "Unauthorized"));
        if (!AuthRole.ADMIN.equals(userContext.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private AdminAuditLogQuery buildQuery(
            Long adminUserId,
            String action,
            String resourceType,
            String resourceId,
            String requestId,
            String sourceType,
            String referenceNo,
            String createdFrom,
            String createdTo,
            Integer page,
            Integer size) {
        try {
            return new AdminAuditLogQuery(
                    adminUserId,
                    parseEnum(AdminAuditAction.class, action, "action"),
                    parseEnum(AdminAuditResourceType.class, resourceType, "resourceType"),
                    resourceId,
                    requestId,
                    parseEnum(AdminAuditSourceType.class, sourceType, "sourceType"),
                    referenceNo,
                    parseInstant(createdFrom, "createdFrom"),
                    parseInstant(createdTo, "createdTo"),
                    page,
                    size);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    private <T extends Enum<T>> T parseEnum(Class<T> enumType, String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid " + fieldName, exception);
        }
    }

    private Instant parseInstant(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid " + fieldName, exception);
        }
    }
}
