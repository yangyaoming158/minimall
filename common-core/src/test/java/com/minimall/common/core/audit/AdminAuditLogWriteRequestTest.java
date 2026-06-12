package com.minimall.common.core.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AdminAuditLogWriteRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void defaultsManualSourceAndKeepsTraceFields() throws Exception {
        JsonNode afterSnapshot = objectMapper.readTree("{\"name\":\"Keyboard\"}");

        AdminAuditLogWriteRequest request = new AdminAuditLogWriteRequest(
                1001L,
                "admin",
                AdminAuditAction.PRODUCT_CREATE,
                AdminAuditResourceType.PRODUCT,
                "SKU-1",
                "REQ-1",
                null,
                "MANUAL-1",
                null,
                afterSnapshot,
                "127.0.0.1",
                "Mozilla/5.0",
                "Create product SKU-1");

        assertEquals(1001L, request.adminUserId());
        assertEquals("admin", request.adminUsername());
        assertEquals(AdminAuditSourceType.ADMIN_MANUAL, request.sourceType());
        assertEquals("REQ-1", request.requestId());
        assertEquals("MANUAL-1", request.referenceNo());
        assertEquals("Keyboard", request.afterSnapshot().get("name").asText());
    }

    @Test
    void normalizesOptionalBlankFields() {
        AdminAuditLogWriteRequest request = new AdminAuditLogWriteRequest(
                1001L,
                "admin",
                AdminAuditAction.PRODUCT_UPDATE,
                AdminAuditResourceType.PRODUCT,
                " ",
                "",
                AdminAuditSourceType.ADMIN_MANUAL,
                " ",
                null,
                null,
                " ",
                "",
                "Update product");

        assertNull(request.resourceId());
        assertNull(request.requestId());
        assertNull(request.referenceNo());
        assertNull(request.ip());
        assertNull(request.userAgent());
    }

    @Test
    void requiresOperatorActionResourceAndSummary() {
        assertThrows(NullPointerException.class, () -> new AdminAuditLogWriteRequest(
                null,
                "admin",
                AdminAuditAction.PRODUCT_UPDATE,
                AdminAuditResourceType.PRODUCT,
                "SKU-1",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "Update product"));
        assertThrows(IllegalArgumentException.class, () -> new AdminAuditLogWriteRequest(
                1001L,
                " ",
                AdminAuditAction.PRODUCT_UPDATE,
                AdminAuditResourceType.PRODUCT,
                "SKU-1",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "Update product"));
        assertThrows(NullPointerException.class, () -> new AdminAuditLogWriteRequest(
                1001L,
                "admin",
                null,
                AdminAuditResourceType.PRODUCT,
                "SKU-1",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "Update product"));
        assertThrows(IllegalArgumentException.class, () -> new AdminAuditLogWriteRequest(
                1001L,
                "admin",
                AdminAuditAction.PRODUCT_UPDATE,
                AdminAuditResourceType.PRODUCT,
                "SKU-1",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                " "));
    }
}
