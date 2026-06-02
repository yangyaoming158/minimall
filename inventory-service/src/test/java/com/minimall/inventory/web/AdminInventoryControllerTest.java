package com.minimall.inventory.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minimall.common.auth.context.AuthRole;
import com.minimall.common.auth.jwt.JwtUtils;
import com.minimall.common.core.audit.AdminAuditAction;
import com.minimall.common.core.audit.AdminAuditLogWriteRequest;
import com.minimall.common.core.audit.AdminAuditResourceType;
import com.minimall.common.core.audit.AdminAuditWriter;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.inventory.domain.Inventory;
import com.minimall.inventory.domain.InventoryChangeType;
import com.minimall.inventory.domain.InventoryRecord;
import com.minimall.inventory.domain.InventoryRecordSourceType;
import com.minimall.inventory.dto.AdjustInventoryRequest;
import com.minimall.inventory.dto.InitializeInventoryRequest;
import com.minimall.inventory.dto.UpdateSafetyStockRequest;
import com.minimall.inventory.repository.InventoryRecordRepository;
import com.minimall.inventory.repository.InventoryRepository;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:admin_inventory_controller;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "minimall.auth.jwt.secret=test-jwt-secret-for-admin-inventory-controller",
        "minimall.auth.jwt.expire-seconds=3600"
})
class AdminInventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private InventoryRecordRepository inventoryRecordRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private AdminAuditWriter adminAuditWriter;

    @BeforeEach
    void setUp() {
        inventoryRecordRepository.deleteAll();
        inventoryRepository.deleteAll();
        reset(adminAuditWriter);
    }

    @Test
    void listRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/inventories"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()));
    }

    @Test
    void listRejectsNonAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/inventories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));
    }

    @Test
    void adminListReturnsPagedAdminFields() throws Exception {
        inventoryRepository.saveAndFlush(buildInventory("SKU-ADM-LIST-1", 8, 0, 3));

        mockMvc.perform(get("/api/admin/inventories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].productId").value("SKU-ADM-LIST-1"))
                .andExpect(jsonPath("$.data.content[0].safetyStock").value(3))
                .andExpect(jsonPath("$.data.content[0].stockState").value("IN_STOCK"))
                .andExpect(jsonPath("$.data.content[0].lowStock").value(false));
    }

    @Test
    void adminListFiltersLowStock() throws Exception {
        inventoryRepository.saveAndFlush(buildInventory("SKU-LOW", 2, 0, 5));
        inventoryRepository.saveAndFlush(buildInventory("SKU-HEALTHY", 50, 0, 5));

        mockMvc.perform(get("/api/admin/inventories")
                        .param("lowStock", "true")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].productId").value("SKU-LOW"))
                .andExpect(jsonPath("$.data.content[0].lowStock").value(true));
    }

    @Test
    void lowStockRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/inventories/low-stock"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()));
    }

    @Test
    void lowStockReturnsBackendComputedCandidates() throws Exception {
        inventoryRepository.saveAndFlush(buildInventory("SKU-LOW-QUERY", 0, 2, 5));
        inventoryRepository.saveAndFlush(buildInventory("SKU-LOW-HEALTHY", 20, 0, 5));
        inventoryRepository.saveAndFlush(buildInventory("SKU-LOW-DISABLED", 0, 0, 0));

        mockMvc.perform(get("/api/admin/inventories/low-stock")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].productId").value("SKU-LOW-QUERY"))
                .andExpect(jsonPath("$.data.content[0].availableStock").value(0))
                .andExpect(jsonPath("$.data.content[0].lockedStock").value(2))
                .andExpect(jsonPath("$.data.content[0].safetyStock").value(5))
                .andExpect(jsonPath("$.data.content[0].stockState").value("OUT_OF_STOCK"))
                .andExpect(jsonPath("$.data.content[0].lowStock").value(true));
    }

    @Test
    void inventoryTrendsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/operation-stats/inventory-trends"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()));
    }

    @Test
    void inventoryTrendsRejectsNonAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/operation-stats/inventory-trends")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));
    }

    @Test
    void inventoryTrendsReturnsDailyBucketsAndDoesNotMutateInventory() throws Exception {
        inventoryRepository.saveAndFlush(buildInventory("SKU-TREND-A", 20, 0, 0));
        inventoryRepository.saveAndFlush(buildInventory("SKU-TREND-B", 8, 0, 0));
        inventoryRepository.saveAndFlush(buildInventory("SKU-TREND-OLD", 100, 0, 0));
        LocalDateTime day1 = LocalDateTime.of(2026, 5, 20, 0, 0);
        LocalDateTime day2 = day1.plusDays(1);

        saveRecord(
                "SKU-TREND-A",
                InventoryChangeType.ADJUST_INCREASE,
                20,
                InventoryRecordSourceType.ADMIN_INITIALIZE,
                "INIT-TREND-A",
                day1.plusHours(1));
        saveRecord(
                "SKU-TREND-A",
                InventoryChangeType.DEDUCT,
                3,
                InventoryRecordSourceType.ORDER_DEDUCT,
                "ORDER-TREND-A-1",
                day1.plusHours(2));
        saveRecord(
                "SKU-TREND-A",
                InventoryChangeType.ADJUST_INCREASE,
                5,
                InventoryRecordSourceType.INBOUND_ORDER,
                "INB-TREND-A-1",
                day2.plusHours(1));
        saveRecord(
                "SKU-TREND-A",
                InventoryChangeType.ADJUST_DECREASE,
                2,
                InventoryRecordSourceType.ADMIN_ADJUSTMENT,
                "ADJ-TREND-A-1",
                day2.plusHours(2));
        saveRecord(
                "SKU-TREND-B",
                InventoryChangeType.RELEASE,
                4,
                InventoryRecordSourceType.ORDER_RELEASE,
                "ORDER-TREND-B-1",
                day2.plusHours(3));
        saveRecord(
                "SKU-TREND-OLD",
                InventoryChangeType.ADJUST_INCREASE,
                100,
                InventoryRecordSourceType.ADMIN_INITIALIZE,
                "INIT-TREND-OLD",
                day1.minusDays(3));

        long beforeRecordCount = inventoryRecordRepository.count();

        mockMvc.perform(get("/api/admin/operation-stats/inventory-trends")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                        .param("createdFrom", "2026-05-20T00:00:00")
                        .param("createdTo", "2026-05-21T23:59:59")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.content[0].productId").value("SKU-TREND-A"))
                .andExpect(jsonPath("$.data.content[0].bucketDate").value("2026-05-20"))
                .andExpect(jsonPath("$.data.content[0].inboundQuantity").value(0))
                .andExpect(jsonPath("$.data.content[0].outboundQuantity").value(3))
                .andExpect(jsonPath("$.data.content[0].adjustmentQuantity").value(20))
                .andExpect(jsonPath("$.data.content[0].endingStock").value(17))
                .andExpect(jsonPath("$.data.content[0].id").doesNotExist())
                .andExpect(jsonPath("$.data.content[1].productId").value("SKU-TREND-A"))
                .andExpect(jsonPath("$.data.content[1].bucketDate").value("2026-05-21"))
                .andExpect(jsonPath("$.data.content[1].inboundQuantity").value(5))
                .andExpect(jsonPath("$.data.content[1].outboundQuantity").value(0))
                .andExpect(jsonPath("$.data.content[1].adjustmentQuantity").value(-2))
                .andExpect(jsonPath("$.data.content[1].endingStock").value(20))
                .andExpect(jsonPath("$.data.content[2].productId").value("SKU-TREND-B"))
                .andExpect(jsonPath("$.data.content[2].bucketDate").value("2026-05-21"))
                .andExpect(jsonPath("$.data.content[2].inboundQuantity").value(4))
                .andExpect(jsonPath("$.data.content[2].outboundQuantity").value(0))
                .andExpect(jsonPath("$.data.content[2].adjustmentQuantity").value(0))
                .andExpect(jsonPath("$.data.content[2].endingStock").value(8));

        assertThat(inventoryRecordRepository.count()).isEqualTo(beforeRecordCount);
        assertThat(inventoryRepository.findByProductId("SKU-TREND-A"))
                .get()
                .extracting(Inventory::getAvailableStock)
                .isEqualTo(20);
        verify(adminAuditWriter, never()).write(any());
    }

    @Test
    void inventoryTrendsRejectsInvalidDateRange() throws Exception {
        mockMvc.perform(get("/api/admin/operation-stats/inventory-trends")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                        .param("createdFrom", "2026-05-22T00:00:00")
                        .param("createdTo", "2026-05-21T00:00:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.getCode()))
                .andExpect(jsonPath("$.message").value("createdFrom must be before or equal to createdTo"));
    }

    @Test
    void initializeCreatesInventoryAndRecord() throws Exception {
        mockMvc.perform(post("/api/admin/inventories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new InitializeInventoryRequest("SKU-INIT-1", 10, 4))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productId").value("SKU-INIT-1"))
                .andExpect(jsonPath("$.data.availableStock").value(10))
                .andExpect(jsonPath("$.data.safetyStock").value(4))
                .andExpect(jsonPath("$.data.stockState").value("IN_STOCK"));

        assertThat(inventoryRepository.findByProductId("SKU-INIT-1")).isPresent();
        assertThat(inventoryRecordRepository.findByProductId("SKU-INIT-1"))
                .singleElement()
                .satisfies(record -> {
                    assertThat(record.getSourceType()).isEqualTo(InventoryRecordSourceType.ADMIN_INITIALIZE);
                    assertThat(record.getQuantity()).isEqualTo(10);
                });

        AdminAuditLogWriteRequest audit = captureSingleAudit();
        assertThat(audit.action()).isEqualTo(AdminAuditAction.INVENTORY_INITIALIZE);
        assertThat(audit.resourceType()).isEqualTo(AdminAuditResourceType.INVENTORY);
        assertThat(audit.resourceId()).isEqualTo("SKU-INIT-1");
        assertThat(audit.adminUserId()).isEqualTo(42L);
        assertThat(audit.adminUsername()).isEqualTo("admin");
        assertThat(audit.beforeSnapshot()).isNull();
        assertThat(audit.afterSnapshot()).isNotNull();
        assertThat(audit.afterSnapshot().get("availableStock").asInt()).isEqualTo(10);
    }

    @Test
    void initializeConflictDoesNotWriteAudit() throws Exception {
        inventoryRepository.saveAndFlush(buildInventory("SKU-INIT-NOAUDIT", 5, 0, 0));

        mockMvc.perform(post("/api/admin/inventories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new InitializeInventoryRequest("SKU-INIT-NOAUDIT", 1, 0))))
                .andExpect(status().isConflict());

        verify(adminAuditWriter, never()).write(any());
    }

    @Test
    void initializeConflictsWhenInventoryExists() throws Exception {
        inventoryRepository.saveAndFlush(buildInventory("SKU-INIT-DUP", 5, 0, 0));

        mockMvc.perform(post("/api/admin/inventories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new InitializeInventoryRequest("SKU-INIT-DUP", 1, 0))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.CONFLICT.getCode()));
    }

    @Test
    void adjustAppliesPositiveAndNegativeDelta() throws Exception {
        inventoryRepository.saveAndFlush(buildInventory("SKU-ADJ", 10, 0, 0));

        adjust("SKU-ADJ", 5, "restock", "REQ-ADJ-UP")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableStock").value(15));

        adjust("SKU-ADJ", -4, "shrinkage", "REQ-ADJ-DOWN")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableStock").value(11));

        assertThat(inventoryRepository.findByProductId("SKU-ADJ"))
                .get()
                .extracting(Inventory::getAvailableStock)
                .isEqualTo(11);
        assertThat(inventoryRecordRepository.findByProductId("SKU-ADJ")).hasSize(2);

        ArgumentCaptor<AdminAuditLogWriteRequest> captor =
                ArgumentCaptor.forClass(AdminAuditLogWriteRequest.class);
        verify(adminAuditWriter, org.mockito.Mockito.times(2)).write(captor.capture());
        AdminAuditLogWriteRequest lastAudit = captor.getAllValues().get(1);
        assertThat(lastAudit.action()).isEqualTo(AdminAuditAction.INVENTORY_ADJUST);
        assertThat(lastAudit.resourceId()).isEqualTo("SKU-ADJ");
        assertThat(lastAudit.referenceNo()).isEqualTo("REQ-ADJ-DOWN");
        assertThat(lastAudit.beforeSnapshot().get("availableStock").asInt()).isEqualTo(15);
        assertThat(lastAudit.afterSnapshot().get("availableStock").asInt()).isEqualTo(11);
    }

    @Test
    void adjustRejectsNegativeResultingStock() throws Exception {
        inventoryRepository.saveAndFlush(buildInventory("SKU-ADJ-NEG", 3, 0, 0));

        adjust("SKU-ADJ-NEG", -5, "oops", "REQ-NEG")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.CONFLICT.getCode()));

        assertThat(inventoryRepository.findByProductId("SKU-ADJ-NEG"))
                .get()
                .extracting(Inventory::getAvailableStock)
                .isEqualTo(3);
    }

    @Test
    void adjustIsIdempotentByRequestId() throws Exception {
        inventoryRepository.saveAndFlush(buildInventory("SKU-IDEM", 10, 0, 0));

        adjust("SKU-IDEM", 5, "restock", "REQ-IDEM")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableStock").value(15));
        // Same requestId again must not re-apply the delta.
        adjust("SKU-IDEM", 5, "restock", "REQ-IDEM")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableStock").value(15));

        assertThat(inventoryRepository.findByProductId("SKU-IDEM"))
                .get()
                .extracting(Inventory::getAvailableStock)
                .isEqualTo(15);
        assertThat(inventoryRecordRepository.existsBySourceTypeAndRequestId(
                InventoryRecordSourceType.ADMIN_ADJUSTMENT, "REQ-IDEM")).isTrue();
        assertThat(inventoryRecordRepository.findByProductId("SKU-IDEM")).hasSize(1);
    }

    @Test
    void adjustReturnsNotFoundForUnknownProduct() throws Exception {
        adjust("SKU-MISSING", 1, "reason", "REQ-MISSING")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.getCode()));
    }

    @Test
    void updateSafetyStockRejectsNonAdmin() throws Exception {
        inventoryRepository.saveAndFlush(buildInventory("SKU-SAFE-USER", 8, 0, 2));

        mockMvc.perform(patch("/api/admin/inventories/SKU-SAFE-USER/safety-stock")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateSafetyStockRequest(6))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));
    }

    @Test
    void updateSafetyStockUpdatesThresholdAndWritesAudit() throws Exception {
        inventoryRepository.saveAndFlush(buildInventory("SKU-SAFE-UPD", 4, 1, 2));

        mockMvc.perform(patch("/api/admin/inventories/SKU-SAFE-UPD/safety-stock")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateSafetyStockRequest(6))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.productId").value("SKU-SAFE-UPD"))
                .andExpect(jsonPath("$.data.availableStock").value(4))
                .andExpect(jsonPath("$.data.lockedStock").value(1))
                .andExpect(jsonPath("$.data.safetyStock").value(6))
                .andExpect(jsonPath("$.data.stockState").value("IN_STOCK"))
                .andExpect(jsonPath("$.data.lowStock").value(true));

        mockMvc.perform(get("/api/admin/inventories/SKU-SAFE-UPD")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.safetyStock").value(6))
                .andExpect(jsonPath("$.data.lowStock").value(true));

        AdminAuditLogWriteRequest audit = captureSingleAudit();
        assertThat(audit.action()).isEqualTo(AdminAuditAction.INVENTORY_ADJUST);
        assertThat(audit.resourceType()).isEqualTo(AdminAuditResourceType.INVENTORY);
        assertThat(audit.resourceId()).isEqualTo("SKU-SAFE-UPD");
        assertThat(audit.referenceNo()).isEqualTo("SKU-SAFE-UPD");
        assertThat(audit.beforeSnapshot().get("safetyStock").asInt()).isEqualTo(2);
        assertThat(audit.afterSnapshot().get("safetyStock").asInt()).isEqualTo(6);
        assertThat(audit.afterSnapshot().get("lowStock").asBoolean()).isTrue();
    }

    @Test
    void updateSafetyStockRejectsNegativeThreshold() throws Exception {
        inventoryRepository.saveAndFlush(buildInventory("SKU-SAFE-NEG", 4, 0, 2));

        mockMvc.perform(patch("/api/admin/inventories/SKU-SAFE-NEG/safety-stock")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateSafetyStockRequest(-1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()));

        assertThat(inventoryRepository.findByProductId("SKU-SAFE-NEG"))
                .get()
                .extracting(Inventory::getSafetyStock)
                .isEqualTo(2);
    }

    @Test
    void recordsReturnTimelineNewestFirst() throws Exception {
        inventoryRepository.saveAndFlush(buildInventory("SKU-REC", 10, 0, 0));
        adjust("SKU-REC", 5, "restock", "REQ-REC-1").andExpect(status().isOk());
        adjust("SKU-REC", -2, "shrinkage", "REQ-REC-2").andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/inventories/SKU-REC/records")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].requestId").value("REQ-REC-2"))
                .andExpect(jsonPath("$.data[0].changeType").value("ADJUST_DECREASE"))
                .andExpect(jsonPath("$.data[0].sourceType").value("ADMIN_ADJUSTMENT"))
                .andExpect(jsonPath("$.data[0].quantity").value(2))
                .andExpect(jsonPath("$.data[0].reason").value("shrinkage"))
                .andExpect(jsonPath("$.data[0].adminUserId").value(42))
                .andExpect(jsonPath("$.data[0].adminUsername").value("admin"))
                .andExpect(jsonPath("$.data[0].referenceNo").value("REQ-REC-2"))
                .andExpect(jsonPath("$.data[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.data[1].requestId").value("REQ-REC-1"));
    }

    private org.springframework.test.web.servlet.ResultActions adjust(
            String productId, int delta, String reason, String requestId) throws Exception {
        return mockMvc.perform(post("/api/admin/inventories/" + productId + "/adjust")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new AdjustInventoryRequest(delta, reason, requestId))));
    }

    private Inventory buildInventory(String productId, int available, int locked, int safetyStock) {
        Inventory inventory = new Inventory(productId, available, locked);
        inventory.setSafetyStock(safetyStock);
        return inventory;
    }

    private void saveRecord(
            String productId,
            InventoryChangeType changeType,
            int quantity,
            InventoryRecordSourceType sourceType,
            String referenceNo,
            LocalDateTime createdAt) {
        String orderNo = sourceType == InventoryRecordSourceType.ORDER_DEDUCT
                || sourceType == InventoryRecordSourceType.ORDER_RELEASE ? referenceNo : null;
        InventoryRecord saved = inventoryRecordRepository.saveAndFlush(new InventoryRecord(
                productId,
                orderNo,
                changeType,
                quantity,
                referenceNo,
                "trend test",
                42L,
                "admin",
                sourceType,
                referenceNo));
        jdbcTemplate.update(
                "update inventory_records set created_at = ?, updated_at = ? where id = ?",
                Timestamp.valueOf(createdAt),
                Timestamp.valueOf(createdAt),
                saved.getId());
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private AdminAuditLogWriteRequest captureSingleAudit() {
        ArgumentCaptor<AdminAuditLogWriteRequest> captor =
                ArgumentCaptor.forClass(AdminAuditLogWriteRequest.class);
        verify(adminAuditWriter).write(captor.capture());
        return captor.getValue();
    }

    private String adminToken() {
        return jwtUtils.generateToken(42L, "admin", AuthRole.ADMIN);
    }

    private String userToken() {
        return jwtUtils.generateToken(43L, "alice", AuthRole.USER);
    }
}
