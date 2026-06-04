package com.minimall.inventory.dto;

import com.minimall.inventory.domain.AiOperationSuggestionItem;
import com.minimall.inventory.domain.AiSuggestionRiskLevel;

public record AiSuggestionItemResponse(
        String productId,
        String productName,
        Integer availableStock,
        Integer lockedStock,
        Integer safetyStock,
        Integer soldQuantityLast7Days,
        int suggestedQuantity,
        AiSuggestionRiskLevel riskLevel,
        String reason) {

    public static AiSuggestionItemResponse from(AiOperationSuggestionItem item) {
        return new AiSuggestionItemResponse(
                item.getProductId(),
                item.getProductName(),
                item.getAvailableStock(),
                item.getLockedStock(),
                item.getSafetyStock(),
                item.getSoldQuantityLast7Days(),
                item.getSuggestedQuantity(),
                item.getRiskLevel(),
                item.getReason());
    }
}
