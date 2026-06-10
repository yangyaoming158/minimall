package com.minimall.inventory.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record AiInventoryHotProductsAnalysisRequest(
        Integer days,

        @Min(value = 1, message = "limit must be at least 1")
        @Max(value = 100, message = "limit must be at most 100")
        Integer limit,

        @Min(value = 0, message = "recordLimit must be at least 0")
        @Max(value = 20, message = "recordLimit must be at most 20")
        Integer recordLimit) {
}
