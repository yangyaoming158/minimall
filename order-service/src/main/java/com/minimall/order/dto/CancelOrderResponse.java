package com.minimall.order.dto;

import com.minimall.order.domain.OrderStatus;

public record CancelOrderResponse(
        String orderNo,
        OrderStatus status) {
}
