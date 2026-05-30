package com.minimall.common.core.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AdminAuditTaxonomyTest {

    @Test
    void includesPhaseTwoAdminWriteActions() {
        assertEquals(AdminAuditAction.PRODUCT_CREATE, AdminAuditAction.valueOf("PRODUCT_CREATE"));
        assertEquals(AdminAuditAction.PRODUCT_UPDATE, AdminAuditAction.valueOf("PRODUCT_UPDATE"));
        assertEquals(AdminAuditAction.PRODUCT_ON_SHELF, AdminAuditAction.valueOf("PRODUCT_ON_SHELF"));
        assertEquals(AdminAuditAction.PRODUCT_OFF_SHELF, AdminAuditAction.valueOf("PRODUCT_OFF_SHELF"));
        assertEquals(AdminAuditAction.INVENTORY_INITIALIZE, AdminAuditAction.valueOf("INVENTORY_INITIALIZE"));
        assertEquals(AdminAuditAction.INVENTORY_ADJUST, AdminAuditAction.valueOf("INVENTORY_ADJUST"));
    }

    @Test
    void reservesFutureAiInventoryTraceTypes() {
        assertEquals(
                AdminAuditSourceType.INVENTORY_ADJUSTMENT,
                AdminAuditSourceType.valueOf("INVENTORY_ADJUSTMENT"));
        assertEquals(AdminAuditSourceType.INBOUND_ORDER, AdminAuditSourceType.valueOf("INBOUND_ORDER"));
        assertEquals(AdminAuditSourceType.AI_SUGGESTION, AdminAuditSourceType.valueOf("AI_SUGGESTION"));
        assertEquals(AdminAuditResourceType.INBOUND_ORDER, AdminAuditResourceType.valueOf("INBOUND_ORDER"));
        assertEquals(AdminAuditResourceType.AI_SUGGESTION, AdminAuditResourceType.valueOf("AI_SUGGESTION"));
    }
}
