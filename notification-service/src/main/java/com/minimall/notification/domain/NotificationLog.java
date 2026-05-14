package com.minimall.notification.domain;

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
import java.util.Objects;

@Entity
@Table(
        name = "notification_logs",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_notification_logs_event_id", columnNames = "event_id")
        },
        indexes = {
                @Index(name = "idx_notification_logs_status", columnList = "status")
        })
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 64)
    private NotificationType notificationType;

    @Column(nullable = false, length = 128)
    private String recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NotificationLogStatus status = NotificationLogStatus.PENDING;

    @Column(length = 4096)
    private String payload;

    @Column(name = "error_message", length = 512)
    private String errorMessage;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected NotificationLog() {
    }

    public NotificationLog(
            String eventId,
            NotificationType notificationType,
            String recipient,
            String payload) {
        this.eventId = Objects.requireNonNull(eventId, "eventId must not be null");
        this.notificationType = Objects.requireNonNull(notificationType, "notificationType must not be null");
        this.recipient = Objects.requireNonNull(recipient, "recipient must not be null");
        this.payload = payload;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (status == null) {
            status = NotificationLogStatus.PENDING;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void markSent(LocalDateTime sentAt) {
        this.status = NotificationLogStatus.SENT;
        this.sentAt = sentAt;
        this.errorMessage = null;
    }

    public void markFailed(String errorMessage) {
        this.status = NotificationLogStatus.FAILED;
        this.errorMessage = errorMessage;
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public NotificationType getNotificationType() {
        return notificationType;
    }

    public String getRecipient() {
        return recipient;
    }

    public NotificationLogStatus getStatus() {
        return status;
    }

    public String getPayload() {
        return payload;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
