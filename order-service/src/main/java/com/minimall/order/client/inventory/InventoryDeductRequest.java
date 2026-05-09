package com.minimall.order.client.inventory;

public record InventoryDeductRequest(
        String orderNo,
        String productId,
        int quantity) {
}