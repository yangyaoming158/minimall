package com.minimall.inventory.dto;

import com.minimall.inventory.domain.Inventory;
import com.minimall.inventory.domain.InventoryStatus;
import com.minimall.inventory.domain.StockState;
import java.time.LocalDateTime;

/**
 * Admin-facing inventory view. Unlike the customer {@code InventoryResponse},
 * it exposes the structured {@code safetyStock} threshold and a derived
 * {@code lowStock} signal so the admin console and later AI replenishment
 * analysis can reason about stock health without direct database access.
 */
public record AdminInventoryResponse(
        String productId,
        int availableStock,
        int lockedStock,
        int safetyStock,
        InventoryStatus status,
        StockState stockState,
        boolean lowStock,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static AdminInventoryResponse from(Inventory inventory) {
        return new AdminInventoryResponse(
                inventory.getProductId(),
                inventory.getAvailableStock(),
                inventory.getLockedStock(),
                inventory.getSafetyStock(),
                inventory.getStatus(),
                inventory.stockState(),
                inventory.isLowStock(),
                inventory.getCreatedAt(),
                inventory.getUpdatedAt());
    }
}
