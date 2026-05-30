package com.minimall.common.core.audit;

import java.time.Instant;

public record AdminAuditLogQuery(
        Long adminUserId,
        AdminAuditAction action,
        AdminAuditResourceType resourceType,
        String resourceId,
        String requestId,
        AdminAuditSourceType sourceType,
        String referenceNo,
        Instant createdFrom,
        Instant createdTo,
        Integer page,
        Integer size) {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 10;
    public static final int MAX_SIZE = 100;

    public AdminAuditLogQuery {
        resourceId = AuditText.nullIfBlank(resourceId);
        requestId = AuditText.nullIfBlank(requestId);
        referenceNo = AuditText.nullIfBlank(referenceNo);
        page = page == null ? DEFAULT_PAGE : page;
        size = size == null ? DEFAULT_SIZE : size;
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }
    }
}
