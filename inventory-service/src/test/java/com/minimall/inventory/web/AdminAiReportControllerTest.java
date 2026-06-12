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
import com.minimall.inventory.domain.AiOperationSuggestion;
import com.minimall.inventory.domain.AiOperationSuggestionSource;
import com.minimall.inventory.domain.AiOperationSuggestionStatus;
import com.minimall.inventory.domain.AiOperationSuggestionType;
import com.minimall.inventory.domain.InboundOrder;
import com.minimall.inventory.domain.InboundOrderStatus;
import com.minimall.inventory.domain.Inventory;
import com.minimall.inventory.dto.AiSalesEvidenceResponse;
import com.minimall.inventory.repository.AiOperationSuggestionRepository;
import com.minimall.inventory.repository.InboundOrderRepository;
import com.minimall.inventory.repository.InventoryRecordRepository;
import com.minimall.inventory.repository.InventoryRepository;
import java.math.BigDecimal;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:admin_ai_report_controller;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "minimall.auth.jwt.secret=test-jwt-secret-for-admin-ai-report-controller",
        "minimall.auth.jwt.expire-seconds=3600"
})
class AdminAiReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private InventoryRecordRepository inventoryRecordRepository;

    @Autowired
    private AiOperationSuggestionRepository suggestionRepository;

    @Autowired
    private InboundOrderRepository inboundOrderRepository;

    @MockBean
    private SalesEvidenceClient salesEvidenceClient;

    @MockBean
    private AdminAuditWriter adminAuditWriter;

    @BeforeEach
    void setUp() {
        suggestionRepository.deleteAll();
        inboundOrderRepository.deleteAll();
        inventoryRecordRepository.deleteAll();
        inventoryRepository.deleteAll();
        reset(salesEvidenceClient, adminAuditWriter);
    }

    @Test
    void dailyReportRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/ai/reports/daily"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()))
                .andExpect(jsonPath("$.message").value("Unauthorized"));

        verify(salesEvidenceClient, never()).salesByProduct(any(), any(), any(), any());
    }

    @Test
    void dailyReportRejectsNonAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/ai/reports/daily")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()))
                .andExpect(jsonPath("$.message").value(ErrorCode.FORBIDDEN.getMessage()));

        verify(salesEvidenceClient, never()).salesByProduct(any(), any(), any(), any());
    }

    @Test
    void dailyReportSummarizesInventorySuggestionsInboundAndHotProducts() throws Exception {
        inventoryRepository.saveAndFlush(inventory("SKU-REPORT-LOW-A", 1, 0, 5));
        inventoryRepository.saveAndFlush(inventory("SKU-REPORT-LOW-B", 0, 1, 2));
        inventoryRepository.saveAndFlush(inventory("SKU-REPORT-HEALTHY", 20, 0, 5));
        inventoryRepository.saveAndFlush(inventory("SKU-REPORT-HOT-A", 9, 1, 4));
        saveSuggestion("AIS-REPORT-PENDING", AiOperationSuggestionStatus.PENDING_REVIEW);
        saveSuggestion("AIS-REPORT-REJECTED", AiOperationSuggestionStatus.REJECTED);
        saveSuggestion("AIS-REPORT-CONVERTED", AiOperationSuggestionStatus.CONVERTED_TO_DRAFT);
        saveInboundOrder("INB-REPORT-APPLIED", InboundOrderStatus.APPLIED);
        given(salesEvidenceClient.salesByProduct(
                        isNull(),
                        any(LocalDateTime.class),
                        any(LocalDateTime.class),
                        eq(PageRequest.of(0, 5))))
                .willReturn(page(List.of(
                        sales("SKU-REPORT-HOT-A", 12, 4, "360.00"),
                        sales("SKU-REPORT-HOT-MISSING", 8, 2, "88.00")), 2));
        long beforeInventoryCount = inventoryRepository.count();
        long beforeRecordCount = inventoryRecordRepository.count();

        mockMvc.perform(get("/api/admin/ai/reports/daily")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.reportDate").exists())
                .andExpect(jsonPath("$.data.generatedAt").exists())
                .andExpect(jsonPath("$.data.windowFrom").exists())
                .andExpect(jsonPath("$.data.windowTo").exists())
                .andExpect(jsonPath("$.data.lowStockCount").value(2))
                .andExpect(jsonPath("$.data.hotProductDays").value(7))
                .andExpect(jsonPath("$.data.hotProductLimit").value(5))
                .andExpect(jsonPath("$.data.hotProducts[0].productId").value("SKU-REPORT-HOT-A"))
                .andExpect(jsonPath("$.data.hotProducts[0].inventory.productId").value("SKU-REPORT-HOT-A"))
                .andExpect(jsonPath("$.data.hotProducts[0].sales.soldQuantity").value(12))
                .andExpect(jsonPath("$.data.hotProducts[1].productId").value("SKU-REPORT-HOT-MISSING"))
                .andExpect(jsonPath("$.data.hotProducts[1].inventory").doesNotExist())
                .andExpect(jsonPath("$.data.suggestions.generatedSuggestions").value(3))
                .andExpect(jsonPath("$.data.suggestions.rejectedSuggestions").value(1))
                .andExpect(jsonPath("$.data.suggestions.convertedDrafts").value(1))
                .andExpect(jsonPath("$.data.inboundOrders.appliedInboundOrders").value(1))
                .andExpect(jsonPath("$.data.limitations[0]")
                        .value("日报统计使用服务所在时区的自然日。"))
                .andExpect(jsonPath("$.data.limitations[1]")
                        .value("低库存数量为当前库存快照，并非当日事件计数。"));

        assertThat(inventoryRepository.count()).isEqualTo(beforeInventoryCount);
        assertThat(inventoryRecordRepository.count()).isEqualTo(beforeRecordCount);
        verify(adminAuditWriter, never()).write(any());
    }

    private Inventory inventory(String productId, int available, int locked, int safetyStock) {
        Inventory inventory = new Inventory(productId, available, locked);
        inventory.setSafetyStock(safetyStock);
        return inventory;
    }

    private void saveSuggestion(String suggestionNo, AiOperationSuggestionStatus status) {
        AiOperationSuggestion suggestion = new AiOperationSuggestion(
                suggestionNo,
                AiOperationSuggestionType.REPLENISHMENT,
                AiOperationSuggestionSource.AI_MODEL,
                "Replenish " + suggestionNo,
                "snapshot:" + suggestionNo,
                "Daily report summary " + suggestionNo);
        if (status == AiOperationSuggestionStatus.REJECTED) {
            suggestion.reject("not needed", 42L, "admin");
        } else if (status == AiOperationSuggestionStatus.CONVERTED_TO_DRAFT) {
            suggestion.convertToDraft("INB-" + suggestionNo, 42L, "admin");
        } else if (status == AiOperationSuggestionStatus.APPLIED) {
            suggestion.markApplied();
        }
        suggestionRepository.saveAndFlush(suggestion);
    }

    private void saveInboundOrder(String inboundNo, InboundOrderStatus status) {
        InboundOrder inboundOrder = new InboundOrder(inboundNo, 42L, "admin");
        if (status == InboundOrderStatus.CONFIRMED || status == InboundOrderStatus.APPLIED) {
            inboundOrder.confirm("REQ-" + inboundNo, 42L, "admin");
        }
        if (status == InboundOrderStatus.APPLIED) {
            inboundOrder.apply();
        } else {
            inboundOrder.setStatus(status);
        }
        inboundOrderRepository.saveAndFlush(inboundOrder);
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
