package com.minimall.order.client.inventory;

record InventoryApiResponse(
        boolean success,
        String code,
        String message,
        InventorySnapshot data) {
}