package com.minimall.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InventoryChangeRequest(
        @NotBlank(message = "orderNo must not be blank")
        @Size(max = 64, message = "orderNo length must be at most 64")
        String orderNo,

        @NotBlank(message = "productId must not be blank")
        @Size(max = 64, message = "productId length must be at most 64")
        String productId,

        @Min(value = 1, message = "quantity must be greater than or equal to 1")
        int quantity) {
}