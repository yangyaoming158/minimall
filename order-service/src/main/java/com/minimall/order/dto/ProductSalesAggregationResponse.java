package com.minimall.order.dto;

import java.math.BigDecimal;

public record ProductSalesAggregationResponse(
        String productId,
        long quantitySold,
        long orderCount,
        BigDecimal totalAmount) {
}
