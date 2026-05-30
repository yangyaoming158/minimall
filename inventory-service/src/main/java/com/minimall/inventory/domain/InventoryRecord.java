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
        name = "inventory_records",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_inventory_records_order_change",
                columnNames = {"order_no", "change_type"}),
        indexes = {
                @Index(name = "idx_inventory_records_product_id", columnList = "product_id"),
                @Index(name = "idx_inventory_records_order_no", columnList = "order_no"),
                @Index(name = "idx_inventory_records_request_id", columnList = "request_id"),
                @Index(name = "idx_inventory_records_reference_no", columnList = "reference_no"),
                @Index(
                        name = "uk_inventory_records_source_request",
                        columnList = "source_type, request_id",
                        unique = true)
        })
public class InventoryRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false, length = 64)
    private String productId;

    @Column(name = "order_no", length = 64)
    private String orderNo;

    @Column(name = "request_id", length = 128)
    private String requestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 32)
    private InventoryChangeType changeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    private InventoryRecordSourceType sourceType;

    @Column(nullable = false)
    private int quantity;

    @Column(length = 512)
    private String reason;

    @Column(name = "admin_user_id")
    private Long adminUserId;

    @Column(name = "admin_username", length = 64)
    private String adminUsername;

    @Column(name = "reference_no", length = 128)
    private String referenceNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private InventoryRecordStatus status = InventoryRecordStatus.SUCCESS;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected InventoryRecord() {
    }

    public InventoryRecord(String productId, String orderNo, InventoryChangeType changeType, int quantity) {
        this(
                productId,
                orderNo,
                changeType,
                quantity,
                orderNo,
                null,
                null,
                null,
                sourceTypeFor(changeType),
                orderNo);
    }

    public InventoryRecord(
            String productId,
            String orderNo,
            InventoryChangeType changeType,
            int quantity,
            String requestId,
            String reason,
            Long adminUserId,
            String adminUsername,
            InventoryRecordSourceType sourceType,
            String referenceNo) {
        this.productId = productId;
        this.orderNo = normalize(orderNo);
        this.changeType = changeType;
        this.quantity = quantity;
        this.requestId = normalize(requestId);
        this.reason = normalize(reason);
        this.adminUserId = adminUserId;
        this.adminUsername = normalize(adminUsername);
        this.sourceType = sourceType == null ? sourceTypeFor(changeType) : sourceType;
        this.referenceNo = normalize(referenceNo);
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (status == null) {
            status = InventoryRecordStatus.SUCCESS;
        }
        if (sourceType == null) {
            sourceType = sourceTypeFor(changeType);
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getProductId() {
        return productId;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public String getRequestId() {
        return requestId;
    }

    public InventoryChangeType getChangeType() {
        return changeType;
    }

    public InventoryRecordSourceType getSourceType() {
        return sourceType;
    }

    public int getQuantity() {
        return quantity;
    }

    public String getReason() {
        return reason;
    }

    public Long getAdminUserId() {
        return adminUserId;
    }

    public String getAdminUsername() {
        return adminUsername;
    }

    public String getReferenceNo() {
        return referenceNo;
    }

    public InventoryRecordStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    private static InventoryRecordSourceType sourceTypeFor(InventoryChangeType changeType) {
        if (changeType == InventoryChangeType.RELEASE) {
            return InventoryRecordSourceType.ORDER_RELEASE;
        }
        return InventoryRecordSourceType.ORDER_DEDUCT;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
