package com.minimall.order.dto;

import java.math.BigDecimal;

public record OrderItemSummary(
        String productId,
        String productName,
        int quantity,
        BigDecimal unitPrice) {
}
