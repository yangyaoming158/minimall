package com.minimall.product.dto;

import com.minimall.product.domain.Product;
import com.minimall.product.domain.ProductStatus;
import java.math.BigDecimal;

public record InternalProductResponse(
        String productId,
        String name,
        BigDecimal price,
        ProductStatus status) {

    public static InternalProductResponse from(Product product) {
        return new InternalProductResponse(
                product.getProductId(),
                product.getName(),
                product.getPrice(),
                product.getStatus());
    }
}
