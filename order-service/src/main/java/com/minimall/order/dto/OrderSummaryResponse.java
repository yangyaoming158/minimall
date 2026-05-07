package com.minimall.order.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.minimall.order.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderSummaryResponse(
        String orderNo,
        OrderStatus status,
        BigDecimal totalAmount,
        List<OrderItemSummary> items,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime expireAt,
        LocalDateTime paidAt,
        LocalDateTime closedAt) {
}
