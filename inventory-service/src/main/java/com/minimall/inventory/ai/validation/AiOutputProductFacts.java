package com.minimall.inventory.ai.validation;

public record AiOutputProductFacts(
        String productId,
        String productName,
        Integer availableStock,
        Integer lockedStock,
        Integer safetyStock,
        Long soldQuantityLast7Days) {

    public AiOutputProductFacts {
        productId = requireText(productId, "productId");
        productName = normalize(productName);
        validateNonNegative(availableStock, "availableStock");
        validateNonNegative(lockedStock, "lockedStock");
        validateNonNegative(safetyStock, "safetyStock");
        validateNonNegative(soldQuantityLast7Days, "soldQuantityLast7Days");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void validateNonNegative(Number value, String name) {
        if (value != null && value.longValue() < 0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
    }
}
