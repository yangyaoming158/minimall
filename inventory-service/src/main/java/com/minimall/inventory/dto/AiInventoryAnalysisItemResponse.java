package com.minimall.inventory.dto;

import com.minimall.inventory.domain.AiSuggestionRiskLevel;

public record AiInventoryAnalysisItemResponse(
        String productId,
        String productName,
        Integer availableStock,
        Integer lockedStock,
        Integer safetyStock,
        Long soldQuantityLast7Days,
        AiSuggestionRiskLevel riskLevel,
        String reason) {
}
