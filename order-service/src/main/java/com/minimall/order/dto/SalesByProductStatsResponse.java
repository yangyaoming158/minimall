package com.minimall.order.dto;

import java.math.BigDecimal;

public record SalesByProductStatsResponse(
        String productId,
        long orderCount,
        long soldQuantity,
        BigDecimal totalAmount) {
}
