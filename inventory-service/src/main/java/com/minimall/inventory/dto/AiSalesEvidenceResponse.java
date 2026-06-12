package com.minimall.inventory.dto;

import java.math.BigDecimal;

public record AiSalesEvidenceResponse(
        String productId,
        long soldQuantity,
        long orderCount,
        BigDecimal totalAmount) {

    public AiSalesEvidenceResponse {
        totalAmount = totalAmount == null ? BigDecimal.ZERO : totalAmount;
    }
}
