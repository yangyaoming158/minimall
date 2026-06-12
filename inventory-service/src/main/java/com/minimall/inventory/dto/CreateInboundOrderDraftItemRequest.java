package com.minimall.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateInboundOrderDraftItemRequest(
        @NotBlank(message = "productId must not be blank")
        @Size(max = 64, message = "productId must be at most 64 characters")
        String productId,

        @Positive(message = "quantity must be positive")
        int quantity) {
}
