package com.minimall.inventory.dto;

import jakarta.validation.constraints.Min;

public record UpdateSafetyStockRequest(
        @Min(value = 0, message = "safetyStock must be greater than or equal to 0")
        int safetyStock) {
}
