package com.minimall.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateOrderRequest(
        @NotBlank(message = "productId must not be blank")
        @Size(max = 64, message = "productId length must be at most 64")
        String productId,

        @NotNull(message = "quantity must not be null")
        @Min(value = 1, message = "quantity must be at least 1")
        Integer quantity,

        @NotBlank(message = "idempotencyKey must not be blank")
        @Size(max = 128, message = "idempotencyKey length must be at most 128")
        String idempotencyKey) {
}
