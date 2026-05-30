package com.minimall.common.core.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.minimall.common.core.response.ApiResponse;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AdminAuditLogResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void serializesStableApiResponseContract() throws Exception {
        JsonNode beforeSnapshot = objectMapper.readTree("{\"availableStock\":8}");
        JsonNode afterSnapshot = objectMapper.readTree("{\"availableStock\":13}");
        AdminAuditLogResponse response = new AdminAuditLogResponse(
                501L,
                1001L,
                "admin",
                AdminAuditAction.INVENTORY_ADJUST,
                AdminAuditResourceType.INVENTORY,
                "SKU-1",
                "REQ-1",
                AdminAuditSourceType.AI_SUGGESTION,
                "AIS-20260530-1",
                beforeSnapshot,
                afterSnapshot,
                "127.0.0.1",
                "Mozilla/5.0",
                "Adjust SKU-1 stock by 5",
                Instant.parse("2026-05-30T10:15:30Z"));

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(ApiResponse.success(response)));

        assertTrue(json.get("success").asBoolean());
        assertEquals("INVENTORY_ADJUST", json.get("data").get("action").asText());
        assertEquals("INVENTORY", json.get("data").get("resourceType").asText());
        assertEquals("AI_SUGGESTION", json.get("data").get("sourceType").asText());
        assertEquals("REQ-1", json.get("data").get("requestId").asText());
        assertEquals("AIS-20260530-1", json.get("data").get("referenceNo").asText());
        assertEquals(8, json.get("data").get("beforeSnapshot").get("availableStock").asInt());
        assertEquals(13, json.get("data").get("afterSnapshot").get("availableStock").asInt());
        assertEquals("2026-05-30T10:15:30Z", json.get("data").get("createdAt").asText());
    }
}
