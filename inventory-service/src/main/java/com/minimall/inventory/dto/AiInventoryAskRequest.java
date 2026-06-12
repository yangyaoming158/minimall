package com.minimall.inventory.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiInventoryAskRequest(
        @NotBlank(message = "question must not be blank")
        @Size(max = 1000, message = "question length must be at most 1000")
        String question,

        @Size(max = 64, message = "productId length must be at most 64")
        String productId,

        @Min(value = 1, message = "limit must be at least 1")
        @Max(value = 100, message = "limit must be at most 100")
        Integer limit,

        @Min(value = 0, message = "recordLimit must be at least 0")
        @Max(value = 20, message = "recordLimit must be at most 20")
        Integer recordLimit) {

    public AiInventoryAskRequest {
        question = normalize(question);
        productId = normalize(productId);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
