package com.minimall.inventory.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.minimall.common.auth.context.AuthRole;
import com.minimall.common.auth.jwt.JwtUtils;
import com.minimall.common.core.audit.AdminAuditWriter;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.common.core.response.PageResponse;
import com.minimall.inventory.ai.AiProviderTokenUsage;
import com.minimall.inventory.ai.AiProviderType;
import com.minimall.inventory.ai.analysis.AiInventoryAnalysisRequest;
import com.minimall.inventory.ai.analysis.AiInventoryAnalysisResult;
import com.minimall.inventory.ai.analysis.AiInventoryAnalysisService;
import com.minimall.inventory.ai.prompt.AiPromptTemplateId;
import com.minimall.inventory.ai.validation.AiAnalysisType;
import com.minimall.inventory.ai.validation.AiOutputValidationException;
import com.minimall.inventory.client.order.SalesEvidenceClient;
import com.minimall.inventory.domain.AiSuggestionValidationStatus;
import com.minimall.inventory.domain.Inventory;
import com.minimall.inventory.domain.InventoryChangeType;
import com.minimall.inventory.domain.InventoryRecord;
import com.minimall.inventory.domain.InventoryRecordSourceType;
import com.minimall.inventory.dto.AiSalesEvidenceResponse;
import com.minimall.inventory.repository.InventoryRecordRepository;
import com.minimall.inventory.repository.InventoryRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:admin_ai_analysis_controller;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "minimall.auth.jwt.secret=test-jwt-secret-for-admin-ai-analysis-controller",
        "minimall.auth.jwt.expire-seconds=3600"
})
class AdminAiInventoryAnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private InventoryRecordRepository inventoryRecordRepository;

    @MockBean
    private SalesEvidenceClient salesEvidenceClient;

    @MockBean
    private AdminAuditWriter adminAuditWriter;

    @MockBean
    private AiInventoryAnalysisService analysisService;

    @BeforeEach
    void setUp() {
        inventoryRecordRepository.deleteAll();
        inventoryRepository.deleteAll();
        reset(salesEvidenceClient, adminAuditWriter, analysisService);
        given(analysisService.generateValidatedAnalysis(any()))
                .willReturn(analysisResult("SKU-LOW-ANALYSIS has high low-stock risk."));
    }

    @Test
    void lowStockAnalysisRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/admin/ai/inventory/low-stock-analysis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(5, 1)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()))
                .andExpect(jsonPath("$.message").value("Unauthorized"));

        verify(analysisService, never()).generateValidatedAnalysis(any());
    }

    @Test
    void lowStockAnalysisRejectsNonAdmin() throws Exception {
        mockMvc.perform(post("/api/admin/ai/inventory/low-stock-analysis")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(5, 1)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()))
                .andExpect(jsonPath("$.message").value(ErrorCode.FORBIDDEN.getMessage()));

        verify(analysisService, never()).generateValidatedAnalysis(any());
    }

    @Test
    void lowStockAnalysisReturnsValidatedAnswerAndDoesNotMutateInventory() throws Exception {
        inventoryRepository.saveAndFlush(inventory("SKU-LOW-ANALYSIS", 2, 1, 5));
        inventoryRecordRepository.saveAndFlush(record("SKU-LOW-ANALYSIS", "REQ-LOW-ANALYSIS"));
        givenSales("SKU-LOW-ANALYSIS", 9, 3, "198.00");
        long beforeInventoryCount = inventoryRepository.count();
        long beforeRecordCount = inventoryRecordRepository.count();

        mockMvc.perform(post("/api/admin/ai/inventory/low-stock-analysis")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(5, 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.analysisType").value("LOW_STOCK"))
                .andExpect(jsonPath("$.data.summary").value("SKU-LOW-ANALYSIS has high low-stock risk."))
                .andExpect(jsonPath("$.data.queryTime").exists())
                .andExpect(jsonPath("$.data.evidence.evidenceType").value("LOW_STOCK_ANALYSIS"))
                .andExpect(jsonPath("$.data.evidence.products[0].productId").value("SKU-LOW-ANALYSIS"))
                .andExpect(jsonPath("$.data.evidence.products[0].inventory.availableStock").value(2))
                .andExpect(jsonPath("$.data.evidence.products[0].sales.soldQuantity").value(9))
                .andExpect(jsonPath("$.data.evidence.products[0].records[0].requestId").value("REQ-LOW-ANALYSIS"))
                .andExpect(jsonPath("$.data.items[0].productId").value("SKU-LOW-ANALYSIS"))
                .andExpect(jsonPath("$.data.items[0].availableStock").value(2))
                .andExpect(jsonPath("$.data.items[0].lockedStock").value(1))
                .andExpect(jsonPath("$.data.items[0].safetyStock").value(5))
                .andExpect(jsonPath("$.data.items[0].soldQuantityLast7Days").value(9))
                .andExpect(jsonPath("$.data.items[0].riskLevel").value("HIGH"))
                .andExpect(jsonPath("$.data.items[0].reason")
                        .value("Available stock is below safety stock with recent paid sales."))
                .andExpect(jsonPath("$.data.limitations[0]")
                        .value("Sales evidence is limited to paid orders in the selected window."))
                .andExpect(jsonPath("$.data.limitations[2]")
                        .value("Model output is advisory and requires administrator review."));

        assertThat(inventoryRepository.count()).isEqualTo(beforeInventoryCount);
        assertThat(inventoryRecordRepository.count()).isEqualTo(beforeRecordCount);
        assertThat(inventoryRepository.findByProductId("SKU-LOW-ANALYSIS"))
                .get()
                .satisfies(saved -> {
                    assertThat(saved.getAvailableStock()).isEqualTo(2);
                    assertThat(saved.getLockedStock()).isEqualTo(1);
                });
        verify(adminAuditWriter, never()).write(any());
        ArgumentCaptor<AiInventoryAnalysisRequest> requestCaptor =
                ArgumentCaptor.forClass(AiInventoryAnalysisRequest.class);
        verify(analysisService).generateValidatedAnalysis(requestCaptor.capture());
        AiInventoryAnalysisRequest analysisRequest = requestCaptor.getValue();
        assertThat(analysisRequest.templateId()).isEqualTo(AiPromptTemplateId.LOW_STOCK_ANALYSIS);
        assertThat(analysisRequest.expectedAnalysisType()).isEqualTo(AiAnalysisType.LOW_STOCK);
        assertThat(analysisRequest.productFacts()).singleElement().satisfies(facts -> {
            assertThat(facts.productId()).isEqualTo("SKU-LOW-ANALYSIS");
            assertThat(facts.availableStock()).isEqualTo(2);
            assertThat(facts.lockedStock()).isEqualTo(1);
            assertThat(facts.safetyStock()).isEqualTo(5);
            assertThat(facts.soldQuantityLast7Days()).isEqualTo(9);
        });
        assertThat(analysisRequest.allowedDateValues()).isNotEmpty();
    }

    @Test
    void lowStockAnalysisRejectsInvalidModelOutputWithStableEnvelope() throws Exception {
        inventoryRepository.saveAndFlush(inventory("SKU-LOW-INVALID", 1, 0, 4));
        givenSales("SKU-LOW-INVALID", 4, 2, "88.00");
        given(analysisService.generateValidatedAnalysis(any()))
                .willThrow(new AiOutputValidationException("riskLevel is not supported: EXTREME"));

        mockMvc.perform(post("/api/admin/ai/inventory/low-stock-analysis")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(5, 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()))
                .andExpect(jsonPath("$.message")
                        .value("AI output validation failed: riskLevel is not supported: EXTREME"));
    }

    @Test
    void lowStockAnalysisRejectsInvalidRequestWithApiResponseEnvelope() throws Exception {
        mockMvc.perform(post("/api/admin/ai/inventory/low-stock-analysis")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(0, 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()));

        verify(analysisService, never()).generateValidatedAnalysis(any());
    }

    private Inventory inventory(String productId, int availableStock, int lockedStock, int safetyStock) {
        Inventory inventory = new Inventory(productId, availableStock, lockedStock);
        inventory.setSafetyStock(safetyStock);
        return inventory;
    }

    private InventoryRecord record(String productId, String requestId) {
        return new InventoryRecord(
                productId,
                null,
                InventoryChangeType.DEDUCT,
                4,
                requestId,
                "Low-stock analysis endpoint test record",
                42L,
                "admin",
                InventoryRecordSourceType.ORDER_DEDUCT,
                requestId);
    }

    private void givenSales(String productId, long soldQuantity, long orderCount, String totalAmount) {
        given(salesEvidenceClient.salesByProduct(
                        eq(productId), any(LocalDateTime.class), any(LocalDateTime.class), eq(PageRequest.of(0, 1))))
                .willReturn(page(List.of(new AiSalesEvidenceResponse(
                        productId,
                        soldQuantity,
                        orderCount,
                        new BigDecimal(totalAmount))), 1));
    }

    private PageResponse<AiSalesEvidenceResponse> page(List<AiSalesEvidenceResponse> content, long totalElements) {
        int totalPages = totalElements == 0 ? 0 : 1;
        return new PageResponse<>(content, 0, Math.max(1, content.size()), totalElements, totalPages);
    }

    private String requestJson(Integer limit, Integer recordLimit) throws Exception {
        ObjectNode json = objectMapper.createObjectNode();
        if (limit != null) {
            json.put("limit", limit);
        }
        if (recordLimit != null) {
            json.put("recordLimit", recordLimit);
        }
        return objectMapper.writeValueAsString(json);
    }

    private AiInventoryAnalysisResult analysisResult(String summary) {
        ObjectNode output = objectMapper.createObjectNode();
        output.put("summary", summary);
        output.put("analysisType", "LOW_STOCK");
        ObjectNode timeRange = output.putObject("timeRange");
        timeRange.put("from", "2026-06-08T00:00:00");
        timeRange.put("to", "2026-06-09T00:00:00");
        ArrayNode items = output.putArray("items");
        ObjectNode item = items.addObject();
        item.put("productId", "SKU-LOW-ANALYSIS");
        item.put("availableStock", 2);
        item.put("lockedStock", 1);
        item.put("safetyStock", 5);
        item.put("soldQuantityLast7Days", 9);
        item.put("riskLevel", "HIGH");
        item.put("reason", "Available stock is below safety stock with recent paid sales.");
        output.putArray("limitations")
                .add("Model output is advisory and requires administrator review.");
        String outputJson = output.toString();
        return new AiInventoryAnalysisResult(
                AiPromptTemplateId.LOW_STOCK_ANALYSIS,
                AiAnalysisType.LOW_STOCK,
                "low-stock-analysis-v1",
                "inventory-analysis-output-v1",
                AiProviderType.MOCK,
                "mock-model",
                "mock-request-id",
                AiProviderTokenUsage.empty(),
                AiSuggestionValidationStatus.VALID,
                "{}",
                outputJson,
                outputJson,
                output);
    }

    private String adminToken() {
        return jwtUtils.generateToken(42L, "admin", AuthRole.ADMIN);
    }

    private String userToken() {
        return jwtUtils.generateToken(43L, "alice", AuthRole.USER);
    }
}
