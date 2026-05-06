package com.minimall.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateProductRequest(
        @NotBlank(message = "productId must not be blank")
        @Size(max = 64, message = "productId length must be at most 64")
        String productId,

        @NotBlank(message = "name must not be blank")
        @Size(max = 128, message = "name length must be at most 128")
        String name,

        @Size(max = 1024, message = "description length must be at most 1024")
        String description,

        @NotNull(message = "price must not be null")
        @DecimalMin(value = "0.01", message = "price must be greater than or equal to 0.01")
        @Digits(integer = 10, fraction = 2, message = "price must fit DECIMAL(12,2)")
        BigDecimal price) {
}
