package com.minimall.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InitializeInventoryRequest(
        @NotBlank(message = "productId must not be blank")
        @Size(max = 64, message = "productId length must be at most 64")
        String productId,

        @Min(value = 0, message = "initialStock must be greater than or equal to 0")
        int initialStock,

        @Min(value = 0, message = "safetyStock must be greater than or equal to 0")
        int safetyStock) {
}
