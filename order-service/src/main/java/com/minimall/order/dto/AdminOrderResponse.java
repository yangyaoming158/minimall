package com.minimall.order.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.minimall.order.domain.Order;
import com.minimall.order.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Admin-facing order view. Unlike the customer {@link OrderSummaryResponse} it also exposes the
 * placing {@code username} so operators can identify who owns an order. Used for both the admin
 * order list and the admin order detail endpoints.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminOrderResponse(
        String orderNo,
        Long userId,
        String username,
        OrderStatus status,
        BigDecimal totalAmount,
        List<OrderItemSummary> items,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime expireAt,
        LocalDateTime paidAt,
        LocalDateTime closedAt) {

    public static AdminOrderResponse from(Order order) {
        return new AdminOrderResponse(
                order.getOrderNo(),
                order.getUserId(),
                order.getUsername(),
                order.getStatus(),
                order.getTotalAmount(),
                List.of(new OrderItemSummary(
                        order.getProductId(),
                        order.getProductName(),
                        order.getQuantity(),
                        order.getUnitPrice())),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getExpireAt(),
                order.getPaidAt(),
                order.getClosedAt());
    }
}
