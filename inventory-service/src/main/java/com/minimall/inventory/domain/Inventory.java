package com.minimall.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "inventory",
        uniqueConstraints = @UniqueConstraint(name = "uk_inventory_product_id", columnNames = "product_id"))
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false, length = 64)
    private String productId;

    @Column(name = "available_stock", nullable = false)
    private int availableStock;

    @Column(name = "locked_stock", nullable = false)
    private int lockedStock;

    @Column(name = "safety_stock", nullable = false)
    private int safetyStock;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private InventoryStatus status = InventoryStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Inventory() {
    }

    public Inventory(String productId, int availableStock, int lockedStock) {
        this.productId = productId;
        this.availableStock = availableStock;
        this.lockedStock = lockedStock;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (status == null) {
            status = InventoryStatus.ACTIVE;
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

    public int getAvailableStock() {
        return availableStock;
    }

    public int getLockedStock() {
        return lockedStock;
    }

    public int getSafetyStock() {
        return safetyStock;
    }

    public InventoryStatus getStatus() {
        return status;
    }

    /**
     * Derived current stock state, centralized here so admin/customer reads and
     * later AI replenishment analysis share one definition.
     */
    public StockState stockState() {
        if (status != InventoryStatus.ACTIVE) {
            return StockState.INACTIVE;
        }
        return availableStock > 0 ? StockState.IN_STOCK : StockState.OUT_OF_STOCK;
    }

    /**
     * Structured low-stock signal: an active product whose available stock has
     * fallen to or below its configured safety threshold. A threshold of 0
     * means low-stock tracking is disabled for the product.
     */
    public boolean isLowStock() {
        return status == InventoryStatus.ACTIVE && safetyStock > 0 && availableStock <= safetyStock;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setStatus(InventoryStatus status) {
        this.status = status;
    }

    public void setSafetyStock(int safetyStock) {
        this.safetyStock = safetyStock;
    }

    /**
     * Apply an admin adjustment to available stock. Callers must validate the
     * resulting stock is non-negative; this guards as a last line of defense.
     */
    public void adjustAvailableStock(int delta) {
        int updated = this.availableStock + delta;
        if (updated < 0) {
            throw new IllegalArgumentException("available stock must not become negative");
        }
        this.availableStock = updated;
    }
}
