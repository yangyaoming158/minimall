package com.minimall.common.core.audit;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Objects;

public record AdminAuditLogResponse(
        Long id,
        Long adminUserId,
        String adminUsername,
        AdminAuditAction action,
        AdminAuditResourceType resourceType,
        String resourceId,
        String requestId,
        AdminAuditSourceType sourceType,
        String referenceNo,
        JsonNode beforeSnapshot,
        JsonNode afterSnapshot,
        String ip,
        String userAgent,
        String summary,
        Instant createdAt) {

    public AdminAuditLogResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(adminUserId, "adminUserId must not be null");
        adminUsername = AuditText.requireText(adminUsername, "adminUsername must not be blank");
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(resourceType, "resourceType must not be null");
        resourceId = AuditText.nullIfBlank(resourceId);
        requestId = AuditText.nullIfBlank(requestId);
        sourceType = sourceType == null ? AdminAuditSourceType.ADMIN_MANUAL : sourceType;
        referenceNo = AuditText.nullIfBlank(referenceNo);
        ip = AuditText.nullIfBlank(ip);
        userAgent = AuditText.nullIfBlank(userAgent);
        summary = AuditText.requireText(summary, "summary must not be blank");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
