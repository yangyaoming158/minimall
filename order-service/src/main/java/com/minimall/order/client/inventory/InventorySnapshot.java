package com.minimall.order.client.inventory;

public record InventorySnapshot(
        String productId,
        int availableStock,
        int lockedStock,
        InventoryStockState stockState) {
}