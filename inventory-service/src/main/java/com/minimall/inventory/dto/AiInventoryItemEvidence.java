package com.minimall.inventory.dto;

import com.minimall.inventory.domain.Inventory;
import com.minimall.inventory.domain.InventoryStatus;
import com.minimall.inventory.domain.StockState;
import java.time.LocalDateTime;

public record AiInventoryItemEvidence(
        String productId,
        int availableStock,
        int lockedStock,
        int safetyStock,
        InventoryStatus status,
        StockState stockState,
        boolean lowStock,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static AiInventoryItemEvidence from(Inventory inventory) {
        return new AiInventoryItemEvidence(
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
