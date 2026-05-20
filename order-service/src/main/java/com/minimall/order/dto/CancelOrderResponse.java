package com.minimall.order.dto;

import com.minimall.order.domain.OrderStatus;

public record CancelOrderResponse(
        String orderNo,
        Long userId,
        String productId,
        OrderStatus status) {
}
