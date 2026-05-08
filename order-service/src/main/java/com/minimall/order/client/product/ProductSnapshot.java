package com.minimall.order.client.product;

import java.math.BigDecimal;

public record ProductSnapshot(
        String productId,
        String name,
        BigDecimal price,
        ProductStatus status) {
}
