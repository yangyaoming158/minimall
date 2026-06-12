package com.minimall.inventory.dto;

import com.minimall.inventory.domain.InboundOrder;
import com.minimall.inventory.domain.InboundOrderItem;
import com.minimall.inventory.domain.InboundOrderSource;
import com.minimall.inventory.domain.InboundOrderStatus;
import java.time.LocalDateTime;
import java.util.List;

public record InboundOrderResponse(
        String inboundNo,
        InboundOrderStatus status,
        InboundOrderSource source,
        Long createdByAdminUserId,
        String createdByAdminUsername,
        String confirmRequestId,
        Long confirmedByAdminUserId,
        String confirmedByAdminUsername,
        LocalDateTime confirmedAt,
        int itemCount,
        int totalQuantity,
        List<InboundOrderItemResponse> items,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static InboundOrderResponse from(InboundOrder order, List<InboundOrderItem> items) {
        List<InboundOrderItemResponse> itemResponses = items.stream()
                .map(InboundOrderItemResponse::from)
                .toList();
        int totalQuantity = items.stream()
                .mapToInt(InboundOrderItem::getQuantity)
                .sum();
        return new InboundOrderResponse(
                order.getInboundNo(),
                order.getStatus(),
                order.getSource(),
                order.getCreatedByAdminUserId(),
                order.getCreatedByAdminUsername(),
                order.getConfirmRequestId(),
                order.getConfirmedByAdminUserId(),
                order.getConfirmedByAdminUsername(),
                order.getConfirmedAt(),
                itemResponses.size(),
                totalQuantity,
                itemResponses,
                order.getCreatedAt(),
                order.getUpdatedAt());
    }
}
