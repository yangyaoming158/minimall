package com.minimall.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "ai_operation_suggestion",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ai_operation_suggestion_no",
                columnNames = "suggestion_no"),
        indexes = {
                @Index(name = "idx_ai_operation_suggestion_status_created", columnList = "status, created_at"),
                @Index(name = "idx_ai_operation_suggestion_type_created", columnList = "type, created_at"),
                @Index(name = "idx_ai_operation_suggestion_linked_inbound_no", columnList = "linked_inbound_no"),
                @Index(name = "idx_ai_operation_suggestion_reviewed_at", columnList = "reviewed_at")
        })
public class AiOperationSuggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "suggestion_no", nullable = false, length = 64)
    private String suggestionNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private AiOperationSuggestionType type = AiOperationSuggestionType.REPLENISHMENT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AiOperationSuggestionStatus status = AiOperationSuggestionStatus.PENDING_REVIEW;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private AiOperationSuggestionSource source = AiOperationSuggestionSource.AI_MODEL;

    @Column(length = 1024)
    private String reason;

    @Column(name = "input_snapshot_ref", length = 128)
    private String inputSnapshotRef;

    @Column(name = "input_summary", length = 4096)
    private String inputSummary;

    @Column(name = "model_provider", length = 64)
    private String modelProvider;

    @Column(name = "model_name", length = 128)
    private String modelName;

    @Column(name = "prompt_version", length = 64)
    private String promptVersion;

    @Column(name = "output_schema_version", length = 64)
    private String outputSchemaVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_status", length = 32)
    private AiSuggestionValidationStatus validationStatus;

    @Column(name = "validation_error", length = 512)
    private String validationError;

    @Column(name = "input_snapshot_json", length = 16384)
    private String inputSnapshotJson;

    @Column(name = "validated_output_json", length = 16384)
    private String validatedOutputJson;

    @Column(name = "raw_model_output_json", length = 16384)
    private String rawModelOutputJson;

    @Column(name = "linked_inbound_no", length = 64)
    private String linkedInboundNo;

    @Column(name = "rejected_reason", length = 512)
    private String rejectedReason;

    @Column(name = "reviewed_by_admin_user_id")
    private Long reviewedByAdminUserId;

    @Column(name = "reviewed_by_admin_username", length = 64)
    private String reviewedByAdminUsername;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected AiOperationSuggestion() {
    }

    public AiOperationSuggestion(
            String suggestionNo,
            AiOperationSuggestionType type,
            AiOperationSuggestionSource source,
            String reason,
            String inputSnapshotRef,
            String inputSummary) {
        this.suggestionNo = normalize(suggestionNo);
        this.type = type == null ? AiOperationSuggestionType.REPLENISHMENT : type;
        this.source = source == null ? AiOperationSuggestionSource.AI_MODEL : source;
        this.reason = normalize(reason);
        this.inputSnapshotRef = normalize(inputSnapshotRef);
        this.inputSummary = normalize(inputSummary);
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (status == null) {
            status = AiOperationSuggestionStatus.PENDING_REVIEW;
        }
        if (type == null) {
            type = AiOperationSuggestionType.REPLENISHMENT;
        }
        if (source == null) {
            source = AiOperationSuggestionSource.AI_MODEL;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getSuggestionNo() {
        return suggestionNo;
    }

    public AiOperationSuggestionType getType() {
        return type;
    }

    public AiOperationSuggestionStatus getStatus() {
        return status;
    }

    public AiOperationSuggestionSource getSource() {
        return source;
    }

    public String getReason() {
        return reason;
    }

    public String getInputSnapshotRef() {
        return inputSnapshotRef;
    }

    public String getInputSummary() {
        return inputSummary;
    }

    public String getModelProvider() {
        return modelProvider;
    }

    public String getModelName() {
        return modelName;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public String getOutputSchemaVersion() {
        return outputSchemaVersion;
    }

    public AiSuggestionValidationStatus getValidationStatus() {
        return validationStatus;
    }

    public String getValidationError() {
        return validationError;
    }

    public String getInputSnapshotJson() {
        return inputSnapshotJson;
    }

    public String getValidatedOutputJson() {
        return validatedOutputJson;
    }

    public String getRawModelOutputJson() {
        return rawModelOutputJson;
    }

    public String getLinkedInboundNo() {
        return linkedInboundNo;
    }

    public String getRejectedReason() {
        return rejectedReason;
    }

    public Long getReviewedByAdminUserId() {
        return reviewedByAdminUserId;
    }

    public String getReviewedByAdminUsername() {
        return reviewedByAdminUsername;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void reject(String rejectedReason, Long adminUserId, String adminUsername) {
        status = AiOperationSuggestionStatus.REJECTED;
        this.rejectedReason = normalize(rejectedReason);
        reviewedByAdminUserId = adminUserId;
        reviewedByAdminUsername = normalize(adminUsername);
        reviewedAt = LocalDateTime.now();
    }

    public void convertToDraft(String inboundNo, Long adminUserId, String adminUsername) {
        status = AiOperationSuggestionStatus.CONVERTED_TO_DRAFT;
        linkedInboundNo = normalize(inboundNo);
        reviewedByAdminUserId = adminUserId;
        reviewedByAdminUsername = normalize(adminUsername);
        reviewedAt = LocalDateTime.now();
    }

    public void markApplied() {
        status = AiOperationSuggestionStatus.APPLIED;
    }

    public void recordAiMetadata(
            String modelProvider,
            String modelName,
            String promptVersion,
            String outputSchemaVersion,
            AiSuggestionValidationStatus validationStatus,
            String validationError,
            String inputSnapshotJson,
            String validatedOutputJson,
            String rawModelOutputJson) {
        this.modelProvider = normalize(modelProvider);
        this.modelName = normalize(modelName);
        this.promptVersion = normalize(promptVersion);
        this.outputSchemaVersion = normalize(outputSchemaVersion);
        this.validationStatus = validationStatus;
        this.validationError = normalize(validationError);
        this.inputSnapshotJson = nullIfBlank(inputSnapshotJson);
        this.validatedOutputJson = nullIfBlank(validatedOutputJson);
        this.rawModelOutputJson = nullIfBlank(rawModelOutputJson);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String nullIfBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
