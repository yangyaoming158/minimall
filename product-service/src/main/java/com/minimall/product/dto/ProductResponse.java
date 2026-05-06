package com.minimall.product.dto;

import com.minimall.product.domain.Product;
import com.minimall.product.domain.ProductStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductResponse(
        String productId,
        String name,
        String description,
        BigDecimal price,
        ProductStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getProductId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStatus(),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }
}
