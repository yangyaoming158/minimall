package com.minimall.product.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateProductStatusRequest(
        @NotBlank(message = "status must not be blank")
        String status) {
}
