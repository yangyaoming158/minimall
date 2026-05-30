package com.minimall.inventory.dto;

import com.minimall.inventory.domain.InventoryChangeType;
import com.minimall.inventory.domain.InventoryRecord;
import com.minimall.inventory.domain.InventoryRecordSourceType;
import com.minimall.inventory.domain.InventoryRecordStatus;
import java.time.LocalDateTime;

public record InventoryRecordResponse(
        Long id,
        String productId,
        String orderNo,
        String requestId,
        InventoryChangeType changeType,
        InventoryRecordSourceType sourceType,
        int quantity,
        String reason,
        Long adminUserId,
        String adminUsername,
        String referenceNo,
        InventoryRecordStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static InventoryRecordResponse from(InventoryRecord record) {
        return new InventoryRecordResponse(
                record.getId(),
                record.getProductId(),
                record.getOrderNo(),
                record.getRequestId(),
                record.getChangeType(),
                record.getSourceType(),
                record.getQuantity(),
                record.getReason(),
                record.getAdminUserId(),
                record.getAdminUsername(),
                record.getReferenceNo(),
                record.getStatus(),
                record.getCreatedAt(),
                record.getUpdatedAt());
    }
}
