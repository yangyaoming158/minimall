package com.minimall.inventory.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.minimall.inventory.domain.InventoryRecordSourceType;
import com.minimall.inventory.dto.AdjustInventoryRequest;
import com.minimall.inventory.dto.InitializeInventoryRequest;
import com.minimall.inventory.repository.InventoryRecordRepository;
import com.minimall.inventory.repository.InventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
