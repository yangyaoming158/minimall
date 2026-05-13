package com.minimall.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(
        name = "order_events",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_order_events_event_id", columnNames = "event_id")
        },
        indexes = {
                @Index(name = "idx_order_events_order_no", columnList = "order_no")
        })
public class OrderEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Column(name = "order_no", nullable = false, length = 64)
    private String orderNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 64)
    private OrderEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 32)
    private OrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", length = 32)
    private OrderStatus toStatus;

    @Column(name = "payload", length = 4096)
    private String payload;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected OrderEvent() {
    }

    public OrderEvent(
            String eventId,
            String orderNo,
            OrderEventType eventType,
            OrderStatus fromStatus,
            OrderStatus toStatus,
            String payload) {
        this.eventId = Objects.requireNonNull(eventId, "eventId must not be null");
        this.orderNo = Objects.requireNonNull(orderNo, "orderNo must not be null");
        this.eventType = Objects.requireNonNull(eventType, "eventType must not be null");
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.payload = payload;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public OrderEventType getEventType() {
        return eventType;
    }

    public OrderStatus getFromStatus() {
        return fromStatus;
    }

    public OrderStatus getToStatus() {
        return toStatus;
    }

    public String getPayload() {
        return payload;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
