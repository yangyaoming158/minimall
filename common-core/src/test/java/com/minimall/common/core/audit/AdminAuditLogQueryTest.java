package com.minimall.common.core.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class AdminAuditLogQueryTest {

    @Test
    void defaultsPagingAndNormalizesBlankTraceFields() {
        AdminAuditLogQuery query = new AdminAuditLogQuery(
                1001L,
                AdminAuditAction.INVENTORY_ADJUST,
                AdminAuditResourceType.INVENTORY,
                " ",
                "",
                AdminAuditSourceType.INVENTORY_ADJUSTMENT,
                " ",
                Instant.parse("2026-05-30T10:00:00Z"),
                Instant.parse("2026-05-30T11:00:00Z"),
                null,
                null);

        assertEquals(1001L, query.adminUserId());
        assertEquals(AdminAuditAction.INVENTORY_ADJUST, query.action());
        assertEquals(AdminAuditSourceType.INVENTORY_ADJUSTMENT, query.sourceType());
        assertNull(query.resourceId());
        assertNull(query.requestId());
        assertNull(query.referenceNo());
        assertEquals(AdminAuditLogQuery.DEFAULT_PAGE, query.page());
        assertEquals(AdminAuditLogQuery.DEFAULT_SIZE, query.size());
    }

    @Test
    void rejectsInvalidPaging() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AdminAuditLogQuery(null, null, null, null, null, null, null, null, null, -1, 10));
        assertThrows(
                IllegalArgumentException.class,
                () -> new AdminAuditLogQuery(null, null, null, null, null, null, null, null, null, 0, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new AdminAuditLogQuery(null, null, null, null, null, null, null, null, null, 0, 101));
    }
}
