package com.minimall.inventory.ai.validation;

import java.util.Arrays;

public enum AiAnalysisType {
    INVENTORY_QA,
    LOW_STOCK,
    HOT_PRODUCTS,
    REPLENISHMENT;

    public static AiAnalysisType from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("analysisType must not be blank");
        }
        String normalized = value.trim();
        return Arrays.stream(values())
                .filter(type -> type.name().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unsupported analysisType: " + normalized));
    }
}
