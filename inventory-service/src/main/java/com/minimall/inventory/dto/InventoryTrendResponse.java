package com.minimall.inventory.dto;

import java.time.LocalDate;

public record InventoryTrendResponse(
        String productId,
        LocalDate bucketDate,
        long inboundQuantity,
        long outboundQuantity,
        long adjustmentQuantity,
        long endingStock) {
}
