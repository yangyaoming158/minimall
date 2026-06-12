package com.minimall.order.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.minimall.order.domain.OrderStatus;
import java.time.LocalDateTime;

/**
 * One point on an admin order lifecycle timeline. Recorded entries (sourced from {@code order_events})
 * carry {@code eventType}, {@code eventId}, and {@code payload}; entries derived from order state
 * timestamps as a minimal fallback leave those null. Status fields serialize as stable enum names.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderEventResponse(
        String eventType,
        OrderStatus fromStatus,
        OrderStatus toStatus,
        LocalDateTime occurredAt,
        String eventId,
        String payload) {

    public static OrderEventResponse derived(OrderStatus fromStatus, OrderStatus toStatus, LocalDateTime occurredAt) {
        return new OrderEventResponse(null, fromStatus, toStatus, occurredAt, null, null);
    }
}
