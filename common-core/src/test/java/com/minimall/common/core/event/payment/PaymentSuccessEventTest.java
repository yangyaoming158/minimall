package com.minimall.common.core.event.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentSuccessEventTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void constructorAppliesCurrentVersionByDefault() {
        PaymentSuccessEvent event = new PaymentSuccessEvent(
                "event-1",
                "ORDER-1",
                "PAY-1",
                new BigDecimal("19.90"),
                Instant.parse("2026-05-11T10:15:30Z"));

        assertEquals(PaymentSuccessEvent.CURRENT_VERSION, event.getVersion());
    }

    @Test
    void constructorRequiresEventId() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentSuccessEvent(
                        " ",
                        "ORDER-1",
                        "PAY-1",
                        new BigDecimal("19.90"),
                        Instant.parse("2026-05-11T10:15:30Z")));

        assertEquals("eventId must not be blank", exception.getMessage());
    }

    @Test
    void serializesAndDeserializesStableJsonContract() throws Exception {
        PaymentSuccessEvent event = new PaymentSuccessEvent(
                "event-1",
                "ORDER-1",
                "PAY-1",
                new BigDecimal("19.90"),
                Instant.parse("2026-05-11T10:15:30Z"));

        String json = objectMapper.writeValueAsString(event);
        PaymentSuccessEvent restored = objectMapper.readValue(json, PaymentSuccessEvent.class);

        assertTrue(json.contains("\"eventId\":\"event-1\""));
        assertTrue(json.contains("\"orderNo\":\"ORDER-1\""));
        assertTrue(json.contains("\"paymentNo\":\"PAY-1\""));
        assertTrue(json.contains("\"amount\":19.90"));
        assertTrue(json.contains("\"paidAt\":\"2026-05-11T10:15:30Z\""));
        assertTrue(json.contains("\"version\":1"));
        assertEquals(event.getEventId(), restored.getEventId());
        assertEquals(event.getOrderNo(), restored.getOrderNo());
        assertEquals(event.getPaymentNo(), restored.getPaymentNo());
        assertEquals(0, event.getAmount().compareTo(restored.getAmount()));
        assertEquals(event.getPaidAt(), restored.getPaidAt());
        assertEquals(event.getVersion(), restored.getVersion());
    }

    @Test
    void deserializesMissingVersionAsCurrentVersion() throws Exception {
        String json = """
                {
                  "eventId": "event-1",
                  "orderNo": "ORDER-1",
                  "paymentNo": "PAY-1",
                  "amount": 19.90,
                  "paidAt": "2026-05-11T10:15:30Z"
                }
                """;

        PaymentSuccessEvent event = objectMapper.readValue(json, PaymentSuccessEvent.class);

        assertEquals(PaymentSuccessEvent.CURRENT_VERSION, event.getVersion());
    }

    @Test
    void deserializationRequiresEventId() {
        String json = """
                {
                  "orderNo": "ORDER-1",
                  "paymentNo": "PAY-1",
                  "amount": 19.90,
                  "paidAt": "2026-05-11T10:15:30Z"
                }
                """;

        assertThrows(Exception.class, () -> objectMapper.readValue(json, PaymentSuccessEvent.class));
    }
}
