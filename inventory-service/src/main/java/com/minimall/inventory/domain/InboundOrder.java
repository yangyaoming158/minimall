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
        name = "inbound_order",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_inbound_order_inbound_no",
                        columnNames = "inbound_no"),
                @UniqueConstraint(
                        name = "uk_inbound_order_confirm_request_id",
                        columnNames = "confirm_request_id")
        },
        indexes = {
                @Index(name = "idx_inbound_order_status_created", columnList = "status, created_at"),
                @Index(name = "idx_inbound_order_source_created", columnList = "source, created_at"),
                @Index(name = "idx_inbound_order_admin_created", columnList = "created_by_admin_user_id, created_at"),
                @Index(name = "idx_inbound_order_confirmed_at", columnList = "confirmed_at")
        })
public class InboundOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inbound_no", nullable = false, length = 64)
    private String inboundNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private InboundOrderStatus status = InboundOrderStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private InboundOrderSource source = InboundOrderSource.ADMIN_MANUAL;

    @Column(name = "created_by_admin_user_id", nullable = false)
    private Long createdByAdminUserId;

    @Column(name = "created_by_admin_username", nullable = false, length = 64)
    private String createdByAdminUsername;

    @Column(name = "confirm_request_id", length = 128)
    private String confirmRequestId;

    @Column(name = "confirmed_by_admin_user_id")
    private Long confirmedByAdminUserId;

    @Column(name = "confirmed_by_admin_username", length = 64)
    private String confirmedByAdminUsername;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected InboundOrder() {
    }

    public InboundOrder(String inboundNo, Long createdByAdminUserId, String createdByAdminUsername) {
        this(inboundNo, InboundOrderSource.ADMIN_MANUAL, createdByAdminUserId, createdByAdminUsername);
    }

    public InboundOrder(
            String inboundNo,
            InboundOrderSource source,
            Long createdByAdminUserId,
            String createdByAdminUsername) {
        this.inboundNo = normalize(inboundNo);
        this.source = source == null ? InboundOrderSource.ADMIN_MANUAL : source;
        this.createdByAdminUserId = createdByAdminUserId;
        this.createdByAdminUsername = normalize(createdByAdminUsername);
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (status == null) {
            status = InboundOrderStatus.DRAFT;
        }
        if (source == null) {
            source = InboundOrderSource.ADMIN_MANUAL;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getInboundNo() {
        return inboundNo;
    }

    public InboundOrderStatus getStatus() {
        return status;
    }

    public InboundOrderSource getSource() {
        return source;
    }

    public Long getCreatedByAdminUserId() {
        return createdByAdminUserId;
    }

    public String getCreatedByAdminUsername() {
        return createdByAdminUsername;
    }

    public String getConfirmRequestId() {
        return confirmRequestId;
    }

    public Long getConfirmedByAdminUserId() {
        return confirmedByAdminUserId;
    }

    public String getConfirmedByAdminUsername() {
        return confirmedByAdminUsername;
    }

    public LocalDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setStatus(InboundOrderStatus status) {
        this.status = status;
    }

    public void confirm(String requestId, Long adminUserId, String adminUsername) {
        status = InboundOrderStatus.CONFIRMED;
        confirmRequestId = normalize(requestId);
        confirmedByAdminUserId = adminUserId;
        confirmedByAdminUsername = normalize(adminUsername);
        confirmedAt = LocalDateTime.now();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
