package com.minimall.notification.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.minimall.notification.domain.NotificationLog;
import com.minimall.notification.domain.NotificationLogStatus;
import com.minimall.notification.domain.NotificationType;
import java.time.LocalDateTime;

/**
 * Admin-facing notification log view.
 *
 * <p>Mapping note (Phase 2, Approach A — no schema change): the locked admin contract exposes an
 * {@code orderNo} filter/field, but {@code notification_logs} has no dedicated order column. The
 * payment-success consumer stores the order number in {@link NotificationLog#getRecipient()}, so
 * {@code orderNo} here is sourced from {@code recipient}. Likewise the contract's {@code channel}
 * filter maps onto {@link NotificationType} (the only categorical type today). This coupling holds
 * only while PAYMENT_SUCCESS is the sole notification type and {@code recipient == orderNo}. If a
 * future phase adds a real delivery channel, a non-order recipient, or a second notification type,
 * revisit this mapping (add real {@code order_no}/{@code channel} columns + a migration) — see the
 * Task 9 dev-log entry.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminNotificationResponse(
        Long id,
        String eventId,
        String orderNo,
        NotificationType notificationType,
        NotificationLogStatus status,
        String errorMessage,
        String payload,
        LocalDateTime sentAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static AdminNotificationResponse from(NotificationLog log) {
        return new AdminNotificationResponse(
                log.getId(),
                log.getEventId(),
                log.getRecipient(),
                log.getNotificationType(),
                log.getStatus(),
                log.getErrorMessage(),
                log.getPayload(),
                log.getSentAt(),
                log.getCreatedAt(),
                log.getUpdatedAt());
    }
}
