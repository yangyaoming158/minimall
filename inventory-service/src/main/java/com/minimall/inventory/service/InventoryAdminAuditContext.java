package com.minimall.inventory.service;

public record InventoryAdminAuditContext(
        Long adminUserId,
        String adminUsername,
        String requestId,
        String ip,
        String userAgent) {
}
