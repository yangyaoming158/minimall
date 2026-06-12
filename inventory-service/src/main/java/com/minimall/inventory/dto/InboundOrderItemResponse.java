package com.minimall.inventory.dto;

import com.minimall.inventory.domain.InboundOrderItem;

public record InboundOrderItemResponse(
        String productId,
        int quantity) {

    public static InboundOrderItemResponse from(InboundOrderItem item) {
        return new InboundOrderItemResponse(item.getProductId(), item.getQuantity());
    }
}
