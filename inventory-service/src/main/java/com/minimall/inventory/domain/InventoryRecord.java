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
                @Index(name = "idx_inventory_records_order_no", columnList = "order_no")
        })
public class InventoryRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false, length = 64)
    private String productId;

    @Column(name = "order_no", nullable = false, length = 64)
    private String orderNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 32)
    private InventoryChangeType changeType;

    @Column(nullable = false)
    private int quantity;

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
        this.productId = productId;
        this.orderNo = orderNo;
        this.changeType = changeType;
        this.quantity = quantity;
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

    public InventoryChangeType getChangeType() {
        return changeType;
    }

    public int getQuantity() {
        return quantity;
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
}
