package com.minimall.product.service;

public record ProductAdminAuditContext(
        Long adminUserId,
        String adminUsername,
        String requestId,
        String ip,
        String userAgent) {
}
