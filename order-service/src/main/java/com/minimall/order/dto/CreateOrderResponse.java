package com.minimall.order.dto;

import com.minimall.order.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateOrderResponse(
        String orderNo,
        OrderStatus status,
        LocalDateTime expireAt,
        BigDecimal totalAmount,
        String productId,
        Integer quantity) {
}
