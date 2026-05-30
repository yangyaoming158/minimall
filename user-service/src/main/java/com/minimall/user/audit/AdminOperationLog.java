package com.minimall.user.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.minimall.common.core.audit.AdminAuditAction;
import com.minimall.common.core.audit.AdminAuditLogWriteRequest;
import com.minimall.common.core.audit.AdminAuditResourceType;
import com.minimall.common.core.audit.AdminAuditSourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(
        name = "admin_operation_logs",
        indexes = {
                @Index(name = "idx_admin_operation_logs_admin_created", columnList = "admin_user_id,created_at"),
                @Index(name = "idx_admin_operation_logs_action_created", columnList = "action,created_at"),
                @Index(name = "idx_admin_operation_logs_resource", columnList = "resource_type,resource_id"),
                @Index(name = "idx_admin_operation_logs_request_id", columnList = "request_id"),
                @Index(name = "idx_admin_operation_logs_source_ref", columnList = "source_type,reference_no"),
                @Index(name = "idx_admin_operation_logs_created_at", columnList = "created_at")
        })
public class AdminOperationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_user_id", nullable = false)
    private Long adminUserId;

    @Column(name = "admin_username", nullable = false, length = 64)
    private String adminUsername;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private AdminAuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 64)
    private AdminAuditResourceType resourceType;

    @Column(name = "resource_id", length = 128)
    private String resourceId;

    @Column(name = "request_id", length = 128)
    private String requestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 64)
    private AdminAuditSourceType sourceType = AdminAuditSourceType.ADMIN_MANUAL;

    @Column(name = "reference_no", length = 128)
    private String referenceNo;

    @Column(name = "before_snapshot", length = 8192)
    private String beforeSnapshot;

    @Column(name = "after_snapshot", length = 8192)
    private String afterSnapshot;

    @Column(length = 64)
    private String ip;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(nullable = false, length = 512)
    private String summary;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected AdminOperationLog() {
    }

    public AdminOperationLog(
            Long adminUserId,
            String adminUsername,
            AdminAuditAction action,
            AdminAuditResourceType resourceType,
            String resourceId,
            String requestId,
            AdminAuditSourceType sourceType,
            String referenceNo,
            String beforeSnapshot,
            String afterSnapshot,
            String ip,
            String userAgent,
            String summary) {
        this.adminUserId = Objects.requireNonNull(adminUserId, "adminUserId must not be null");
        this.adminUsername = requireText(adminUsername, "adminUsername must not be blank");
        this.action = Objects.requireNonNull(action, "action must not be null");
        this.resourceType = Objects.requireNonNull(resourceType, "resourceType must not be null");
        this.resourceId = nullIfBlank(resourceId);
        this.requestId = nullIfBlank(requestId);
        this.sourceType = sourceType == null ? AdminAuditSourceType.ADMIN_MANUAL : sourceType;
        this.referenceNo = nullIfBlank(referenceNo);
        this.beforeSnapshot = nullIfBlank(beforeSnapshot);
        this.afterSnapshot = nullIfBlank(afterSnapshot);
        this.ip = nullIfBlank(ip);
        this.userAgent = nullIfBlank(userAgent);
        this.summary = requireText(summary, "summary must not be blank");
    }

    public static AdminOperationLog from(AdminAuditLogWriteRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return new AdminOperationLog(
                request.adminUserId(),
                request.adminUsername(),
                request.action(),
                request.resourceType(),
                request.resourceId(),
                request.requestId(),
                request.sourceType(),
                request.referenceNo(),
                jsonToString(request.beforeSnapshot()),
                jsonToString(request.afterSnapshot()),
                request.ip(),
                request.userAgent(),
                request.summary());
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (sourceType == null) {
            sourceType = AdminAuditSourceType.ADMIN_MANUAL;
        }
    }

    public Long getId() {
        return id;
    }

    public Long getAdminUserId() {
        return adminUserId;
    }

    public String getAdminUsername() {
        return adminUsername;
    }

    public AdminAuditAction getAction() {
        return action;
    }

    public AdminAuditResourceType getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getRequestId() {
        return requestId;
    }

    public AdminAuditSourceType getSourceType() {
        return sourceType;
    }

    public String getReferenceNo() {
        return referenceNo;
    }

    public String getBeforeSnapshot() {
        return beforeSnapshot;
    }

    public String getAfterSnapshot() {
        return afterSnapshot;
    }

    public String getIp() {
        return ip;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getSummary() {
        return summary;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    private static String jsonToString(JsonNode jsonNode) {
        return jsonNode == null ? null : jsonNode.toString();
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static String nullIfBlank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
