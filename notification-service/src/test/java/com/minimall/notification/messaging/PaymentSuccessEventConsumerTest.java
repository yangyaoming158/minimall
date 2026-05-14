package com.minimall.notification.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minimall.common.core.event.payment.PaymentSuccessEvent;
import com.minimall.notification.domain.NotificationLog;
import com.minimall.notification.domain.NotificationLogStatus;
import com.minimall.notification.domain.NotificationType;
import com.minimall.notification.repository.NotificationLogRepository;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:payment_notification_consumer;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
class PaymentSuccessEventConsumerTest {

    @Autowired
    private PaymentSuccessEventConsumer consumer;

    @Autowired
    private NotificationLogRepository notificationLogRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        notificationLogRepository.deleteAll();
    }

    @Test
    void writesSentNotificationLogForPaymentSuccessEvent() {
        PaymentSuccessEvent event = newEvent("notification-event-1001", "ORD-NOTIFY-1001");

        consumer.handle(event);

        assertThat(notificationLogRepository.findAll()).hasSize(1);
        NotificationLog recorded = notificationLogRepository.findByEventId(event.getEventId()).orElseThrow();
        JsonNode payload = payload(recorded);
        assertThat(recorded.getNotificationType()).isEqualTo(NotificationType.PAYMENT_SUCCESS);
        assertThat(recorded.getRecipient()).isEqualTo(event.getOrderNo());
        assertThat(recorded.getStatus()).isEqualTo(NotificationLogStatus.SENT);
        assertThat(recorded.getSentAt()).isNotNull();
        assertThat(recorded.getErrorMessage()).isNull();
        assertThat(payload.path("eventId").asText()).isEqualTo(event.getEventId());
        assertThat(payload.path("orderNo").asText()).isEqualTo(event.getOrderNo());
        assertThat(payload.path("paymentNo").asText()).isEqualTo(event.getPaymentNo());
        assertThat(payload.path("handleResult").asText()).isEqualTo("sent");
        assertThat(payload.path("version").asInt()).isEqualTo(PaymentSuccessEvent.CURRENT_VERSION);
    }

    @Test
    void duplicateEventIdIsIgnoredWithoutAdditionalRows() {
        PaymentSuccessEvent event = newEvent("notification-event-duplicate", "ORD-NOTIFY-1002");

        consumer.handle(event);
        consumer.handle(event);

        assertThat(notificationLogRepository.findAll()).hasSize(1);
        NotificationLog recorded = notificationLogRepository.findByEventId(event.getEventId()).orElseThrow();
        assertThat(recorded.getStatus()).isEqualTo(NotificationLogStatus.SENT);
        assertThat(payload(recorded).path("handleResult").asText()).isEqualTo("sent");
    }

    @Test
    void serializationFailureFallsBackToMinimalPayloadAndDoesNotThrow() {
        PaymentSuccessEvent event = newEvent("notification-event-fallback", "ORD-NOTIFY-1003");
        PaymentSuccessEventConsumer fallbackConsumer = new PaymentSuccessEventConsumer(
                notificationLogRepository,
                new FailingObjectMapper());

        fallbackConsumer.handle(event);

        assertThat(notificationLogRepository.findAll()).hasSize(1);
        NotificationLog recorded = notificationLogRepository.findByEventId(event.getEventId()).orElseThrow();
        JsonNode payload = payload(recorded);
        assertThat(recorded.getStatus()).isEqualTo(NotificationLogStatus.SENT);
        assertThat(payload.path("eventId").asText()).isEqualTo(event.getEventId());
        assertThat(payload.path("handleResult").asText()).isEqualTo("sent");
    }

    private PaymentSuccessEvent newEvent(String eventId, String orderNo) {
        return new PaymentSuccessEvent(
                eventId,
                orderNo,
                "PAY-" + eventId,
                new BigDecimal("19.90"),
                Instant.parse("2026-05-14T10:15:30Z"));
    }

    private JsonNode payload(NotificationLog notificationLog) {
        try {
            return objectMapper.readTree(notificationLog.getPayload());
        } catch (Exception exception) {
            throw new AssertionError("notification log payload should be valid JSON", exception);
        }
    }

    private static final class FailingObjectMapper extends ObjectMapper {
        @Override
        public String writeValueAsString(Object value) throws JsonProcessingException {
            throw JsonMappingException.fromUnexpectedIOE(new java.io.IOException("serialization failed"));
        }
    }
}
