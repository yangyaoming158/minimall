package com.minimall.inventory.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minimall.common.auth.context.AuthRole;
import com.minimall.common.auth.jwt.JwtUtils;
import com.minimall.common.core.audit.AdminAuditWriter;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.common.core.response.PageResponse;
import com.minimall.inventory.client.order.SalesEvidenceClient;
import com.minimall.inventory.domain.Inventory;
import com.minimall.inventory.domain.InventoryChangeType;
import com.minimall.inventory.domain.InventoryRecord;
import com.minimall.inventory.domain.InventoryRecordSourceType;
import com.minimall.inventory.dto.AiSalesEvidenceResponse;
import com.minimall.inventory.repository.InventoryRecordRepository;
import com.minimall.inventory.repository.InventoryRepository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:admin_ai_evidence_controller;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "minimall.auth.jwt.secret=test-jwt-secret-for-admin-ai-evidence-controller",
        "minimall.auth.jwt.expire-seconds=3600"
})
class AdminAiEvidenceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private InventoryRecordRepository inventoryRecordRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private SalesEvidenceClient salesEvidenceClient;

    @MockBean
    private AdminAuditWriter adminAuditWriter;

    @BeforeEach
    void setUp() {
        inventoryRecordRepository.deleteAll();
        inventoryRepository.deleteAll();
        reset(salesEvidenceClient, adminAuditWriter);
    }

    @Test
    void currentInventoryRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/ai/inventory/evidence/current/SKU-AI-EVIDENCE"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()))
                .andExpect(jsonPath("$.message").value("Unauthorized"));
    }

    @Test
    void currentInventoryRejectsNonAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/ai/inventory/evidence/current/SKU-AI-EVIDENCE")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()))
                .andExpect(jsonPath("$.message").value(ErrorCode.FORBIDDEN.getMessage()));
    }

    @Test
    void currentInventoryReturnsEvidenceAndDoesNotMutateInventory() throws Exception {
        Inventory inventory = inventoryRepository.saveAndFlush(buildInventory("SKU-AI-CURRENT", 6, 2, 8));
        touchInventory(inventory.getId(), LocalDateTime.of(2026, 6, 6, 9, 0),
                LocalDateTime.of(2026, 6, 6, 9, 5));
        saveRecord("SKU-AI-CURRENT", InventoryChangeType.ADJUST_INCREASE, 10,
                InventoryRecordSourceType.ADMIN_ADJUSTMENT, "REQ-AI-OLD",
                LocalDateTime.of(2026, 6, 6, 10, 0));
        saveRecord("SKU-AI-CURRENT", InventoryChangeType.DEDUCT, 4,
                InventoryRecordSourceType.ORDER_DEDUCT, "ORDER-AI-NEW",
                LocalDateTime.of(2026, 6, 6, 11, 0));
        long beforeInventoryCount = inventoryRepository.count();
        long beforeRecordCount = inventoryRecordRepository.count();

        mockMvc.perform(get("/api/admin/ai/inventory/evidence/current/SKU-AI-CURRENT")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                        .param("recordLimit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.evidenceType").value("CURRENT_INVENTORY"))
                .andExpect(jsonPath("$.data.inventories[0].productId").value("SKU-AI-CURRENT"))
                .andExpect(jsonPath("$.data.inventories[0].availableStock").value(6))
                .andExpect(jsonPath("$.data.inventories[0].lockedStock").value(2))
                .andExpect(jsonPath("$.data.inventories[0].safetyStock").value(8))
                .andExpect(jsonPath("$.data.inventories[0].lowStock").value(true))
                .andExpect(jsonPath("$.data.records[0].requestId").value("ORDER-AI-NEW"))
                .andExpect(jsonPath("$.data.records[0].changeType").value("DEDUCT"));

        assertThat(inventoryRepository.count()).isEqualTo(beforeInventoryCount);
        assertThat(inventoryRecordRepository.count()).isEqualTo(beforeRecordCount);
        assertThat(inventoryRepository.findByProductId("SKU-AI-CURRENT"))
                .get()
                .satisfies(saved -> {
                    assertThat(saved.getAvailableStock()).isEqualTo(6);
                    assertThat(saved.getLockedStock()).isEqualTo(2);
                });
        verify(adminAuditWriter, never()).write(any());
    }

    @Test
    void currentInventoryMissingProductReturnsStableErrorEnvelope() throws Exception {
        mockMvc.perform(get("/api/admin/ai/inventory/evidence/current/SKU-AI-MISSING")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message").value("Inventory not found"));
    }

    @Test
    void lowStockCandidatesReturnsBackendComputedEvidence() throws Exception {
        inventoryRepository.saveAndFlush(buildInventory("SKU-AI-LOW-A", 1, 0, 5));
        inventoryRepository.saveAndFlush(buildInventory("SKU-AI-LOW-B", 0, 1, 2));
        inventoryRepository.saveAndFlush(buildInventory("SKU-AI-HEALTHY", 20, 0, 5));
        saveRecord("SKU-AI-LOW-A", InventoryChangeType.DEDUCT, 2,
                InventoryRecordSourceType.ORDER_DEDUCT, "ORDER-AI-LOW-A",
                LocalDateTime.of(2026, 6, 6, 12, 0));

        mockMvc.perform(get("/api/admin/ai/inventory/evidence/low-stock-candidates")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                        .param("limit", "10")
                        .param("recordLimit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.evidenceType").value("LOW_STOCK_CANDIDATES"))
                .andExpect(jsonPath("$.data.inventories[0].productId").value("SKU-AI-LOW-A"))
                .andExpect(jsonPath("$.data.inventories[1].productId").value("SKU-AI-LOW-B"))
                .andExpect(jsonPath("$.data.records[0].requestId").value("ORDER-AI-LOW-A"));
    }

    @Test
    void lowStockAnalysisReturnsSalesEvidenceAndDoesNotMutateInventory() throws Exception {
        inventoryRepository.saveAndFlush(buildInventory("SKU-AI-ANALYSIS", 2, 1, 5));
        given(salesEvidenceClient.salesByProduct(
                        eq("SKU-AI-ANALYSIS"), any(LocalDateTime.class), any(LocalDateTime.class),
                        eq(PageRequest.of(0, 1))))
                .willReturn(page(List.of(sales("SKU-AI-ANALYSIS", 6, 2, "168.00")), 1));
        long beforeInventoryCount = inventoryRepository.count();
        long beforeRecordCount = inventoryRecordRepository.count();

        mockMvc.perform(get("/api/admin/ai/inventory/evidence/low-stock-analysis")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                        .param("limit", "10")
                        .param("recordLimit", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.evidenceType").value("LOW_STOCK_ANALYSIS"))
                .andExpect(jsonPath("$.data.days").value(7))
                .andExpect(jsonPath("$.data.products[0].productId").value("SKU-AI-ANALYSIS"))
                .andExpect(jsonPath("$.data.products[0].sales.soldQuantity").value(6))
                .andExpect(jsonPath("$.data.products[0].sales.orderCount").value(2))
                .andExpect(jsonPath("$.data.products[0].records").isEmpty());

        assertThat(inventoryRepository.count()).isEqualTo(beforeInventoryCount);
        assertThat(inventoryRecordRepository.count()).isEqualTo(beforeRecordCount);
        verify(adminAuditWriter, never()).write(any());
    }

    @Test
    void hotProductsReturnsJoinedEvidence() throws Exception {
        inventoryRepository.saveAndFlush(buildInventory("SKU-AI-HOT-A", 9, 1, 4));
        given(salesEvidenceClient.salesByProduct(
                        isNull(), any(LocalDateTime.class), any(LocalDateTime.class), eq(PageRequest.of(0, 2))))
                .willReturn(page(List.of(
                        sales("SKU-AI-HOT-A", 12, 4, "360.00"),
                        sales("SKU-AI-HOT-MISSING", 8, 2, "88.00")), 2));

        mockMvc.perform(get("/api/admin/ai/inventory/evidence/hot-products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                        .param("days", "30")
                        .param("limit", "2")
                        .param("recordLimit", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.evidenceType").value("HOT_PRODUCTS"))
                .andExpect(jsonPath("$.data.days").value(30))
                .andExpect(jsonPath("$.data.products[0].productId").value("SKU-AI-HOT-A"))
                .andExpect(jsonPath("$.data.products[0].inventory.productId").value("SKU-AI-HOT-A"))
                .andExpect(jsonPath("$.data.products[0].sales.totalAmount").value(360.00))
                .andExpect(jsonPath("$.data.products[1].productId").value("SKU-AI-HOT-MISSING"))
                .andExpect(jsonPath("$.data.products[1].inventory").doesNotExist())
                .andExpect(jsonPath("$.data.products[1].limitations[0]")
                        .value("无近期库存流水证据。"))
                .andExpect(jsonPath("$.data.products[1].limitations[1]")
                        .value("该商品当前库存证据缺失。"));
    }

    @Test
    void hotProductsRejectsUnsupportedDaysWithStableErrorEnvelope() throws Exception {
        mockMvc.perform(get("/api/admin/ai/inventory/evidence/hot-products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                        .param("days", "14"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.getCode()))
                .andExpect(jsonPath("$.message").value("hot product evidence days must be 7 or 30"));

        verify(salesEvidenceClient, never()).salesByProduct(any(), any(), any(), any());
    }

    private Inventory buildInventory(String productId, int available, int locked, int safetyStock) {
        Inventory inventory = new Inventory(productId, available, locked);
        inventory.setSafetyStock(safetyStock);
        return inventory;
    }

    private void touchInventory(Long inventoryId, LocalDateTime createdAt, LocalDateTime updatedAt) {
        jdbcTemplate.update(
                "update inventory set created_at = ?, updated_at = ? where id = ?",
                Timestamp.valueOf(createdAt),
                Timestamp.valueOf(updatedAt),
                inventoryId);
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
                "AI evidence test",
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

    private PageResponse<AiSalesEvidenceResponse> page(List<AiSalesEvidenceResponse> content, long totalElements) {
        int totalPages = totalElements == 0 ? 0 : 1;
        return new PageResponse<>(content, 0, Math.max(1, content.size()), totalElements, totalPages);
    }

    private AiSalesEvidenceResponse sales(
            String productId,
            long soldQuantity,
            long orderCount,
            String totalAmount) {
        return new AiSalesEvidenceResponse(productId, soldQuantity, orderCount, new BigDecimal(totalAmount));
    }

    private String adminToken() {
        return jwtUtils.generateToken(42L, "admin", AuthRole.ADMIN);
    }

    private String userToken() {
        return jwtUtils.generateToken(43L, "alice", AuthRole.USER);
    }
}
