package com.minimall.inventory.dto;

import com.minimall.inventory.domain.StockState;

public record InventoryResponse(
        String productId,
        int availableStock,
        int lockedStock,
        StockState stockState) {
}
