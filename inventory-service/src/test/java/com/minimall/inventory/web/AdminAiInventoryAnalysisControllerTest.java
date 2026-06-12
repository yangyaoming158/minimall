package com.minimall.inventory.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
import com.minimall.inventory.repository.AiOperationSuggestionRepository;
import com.minimall.inventory.repository.InboundOrderRepository;
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

    @Autowired
    private AiOperationSuggestionRepository suggestionRepository;

    @Autowired
    private InboundOrderRepository inboundOrderRepository;

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
                        .value("销量证据仅统计所选时间窗内的已支付订单。"))
                .andExpect(jsonPath("$.data.limitations[2]")
                        .value("Model output is advisory and requires administrator review."));

        assertThat(inventoryRepository.count()).isEqualTo(beforeInventoryCount);
        assertThat(inventoryRecordRepository.count()).isEqualTo(beforeRecordCount);
        assertThat(suggestionRepository.count()).isZero();
        assertThat(inboundOrderRepository.count()).isZero();
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

    @Test
    void hotProductsAnalysisRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/admin/ai/inventory/hot-products-analysis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(hotRequestJson(7, 2, 1)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()))
                .andExpect(jsonPath("$.message").value("Unauthorized"));

        verify(salesEvidenceClient, never()).salesByProduct(any(), any(), any(), any());
        verify(analysisService, never()).generateValidatedAnalysis(any());
    }

    @Test
    void hotProductsAnalysisRejectsNonAdmin() throws Exception {
        mockMvc.perform(post("/api/admin/ai/inventory/hot-products-analysis")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(hotRequestJson(7, 2, 1)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()))
                .andExpect(jsonPath("$.message").value(ErrorCode.FORBIDDEN.getMessage()));

        verify(salesEvidenceClient, never()).salesByProduct(any(), any(), any(), any());
        verify(analysisService, never()).generateValidatedAnalysis(any());
    }

    @Test
    void hotProductsAnalysisReturnsValidatedAnswerAndDoesNotMutateInventory() throws Exception {
        inventoryRepository.saveAndFlush(inventory("SKU-HOT-ANALYSIS", 4, 0, 10));
        inventoryRecordRepository.saveAndFlush(record("SKU-HOT-ANALYSIS", "REQ-HOT-ANALYSIS"));
        givenHotSales(2, List.of(
                new AiSalesEvidenceResponse("SKU-HOT-ANALYSIS", 21, 6, new BigDecimal("588.00")),
                new AiSalesEvidenceResponse("SKU-HOT-MISSING", 7, 2, new BigDecimal("168.00"))), 2);
        given(analysisService.generateValidatedAnalysis(any()))
                .willReturn(hotProductsAnalysisResult("SKU-HOT-ANALYSIS is a hot product with stock risk."));
        long beforeInventoryCount = inventoryRepository.count();
        long beforeRecordCount = inventoryRecordRepository.count();

        mockMvc.perform(post("/api/admin/ai/inventory/hot-products-analysis")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(hotRequestJson(30, 2, 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.analysisType").value("HOT_PRODUCTS"))
                .andExpect(jsonPath("$.data.summary")
                        .value("SKU-HOT-ANALYSIS is a hot product with stock risk."))
                .andExpect(jsonPath("$.data.queryTime").exists())
                .andExpect(jsonPath("$.data.evidence.evidenceType").value("HOT_PRODUCTS"))
                .andExpect(jsonPath("$.data.evidence.days").value(30))
                .andExpect(jsonPath("$.data.evidence.products[0].productId").value("SKU-HOT-ANALYSIS"))
                .andExpect(jsonPath("$.data.evidence.products[0].inventory.availableStock").value(4))
                .andExpect(jsonPath("$.data.evidence.products[0].sales.soldQuantity").value(21))
                .andExpect(jsonPath("$.data.evidence.products[0].records[0].requestId")
                        .value("REQ-HOT-ANALYSIS"))
                .andExpect(jsonPath("$.data.evidence.products[1].productId").value("SKU-HOT-MISSING"))
                .andExpect(jsonPath("$.data.evidence.products[1].inventory").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].productId").value("SKU-HOT-ANALYSIS"))
                .andExpect(jsonPath("$.data.items[0].availableStock").value(4))
                .andExpect(jsonPath("$.data.items[0].safetyStock").value(10))
                .andExpect(jsonPath("$.data.items[0].soldQuantityLast7Days").value(21))
                .andExpect(jsonPath("$.data.items[0].riskLevel").value("HIGH"))
                .andExpect(jsonPath("$.data.items[0].reason")
                        .value("Hot sales velocity may outpace current available stock."))
                .andExpect(jsonPath("$.data.limitations[0]")
                        .value("销量证据仅统计所选时间窗内的已支付订单。"))
                .andExpect(jsonPath("$.data.limitations[2]")
                        .value("Model output is advisory and requires administrator review."));

        assertThat(inventoryRepository.count()).isEqualTo(beforeInventoryCount);
        assertThat(inventoryRecordRepository.count()).isEqualTo(beforeRecordCount);
        assertThat(suggestionRepository.count()).isZero();
        assertThat(inboundOrderRepository.count()).isZero();
        assertThat(inventoryRepository.findByProductId("SKU-HOT-ANALYSIS"))
                .get()
                .extracting(Inventory::getAvailableStock)
                .isEqualTo(4);
        verify(adminAuditWriter, never()).write(any());
        ArgumentCaptor<AiInventoryAnalysisRequest> requestCaptor =
                ArgumentCaptor.forClass(AiInventoryAnalysisRequest.class);
        verify(analysisService).generateValidatedAnalysis(requestCaptor.capture());
        AiInventoryAnalysisRequest analysisRequest = requestCaptor.getValue();
        assertThat(analysisRequest.templateId()).isEqualTo(AiPromptTemplateId.HOT_PRODUCTS_ANALYSIS);
        assertThat(analysisRequest.expectedAnalysisType()).isEqualTo(AiAnalysisType.HOT_PRODUCTS);
        assertThat(analysisRequest.productFacts())
                .extracting(facts -> facts.productId())
                .containsExactly("SKU-HOT-ANALYSIS", "SKU-HOT-MISSING");
        assertThat(analysisRequest.productFacts().get(0)).satisfies(facts -> {
            assertThat(facts.availableStock()).isEqualTo(4);
            assertThat(facts.lockedStock()).isZero();
            assertThat(facts.safetyStock()).isEqualTo(10);
            assertThat(facts.soldQuantityLast7Days()).isEqualTo(21);
        });
        assertThat(analysisRequest.productFacts().get(1)).satisfies(facts -> {
            assertThat(facts.availableStock()).isNull();
            assertThat(facts.lockedStock()).isNull();
            assertThat(facts.safetyStock()).isNull();
            assertThat(facts.soldQuantityLast7Days()).isEqualTo(7);
        });
        assertThat(analysisRequest.allowedDateValues()).isNotEmpty();
    }

    @Test
    void hotProductsAnalysisRejectsUnsupportedDaysWithStableEnvelope() throws Exception {
        mockMvc.perform(post("/api/admin/ai/inventory/hot-products-analysis")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(hotRequestJson(14, 2, 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.getCode()))
                .andExpect(jsonPath("$.message").value("hot product evidence days must be 7 or 30"));

        verify(salesEvidenceClient, never()).salesByProduct(any(), any(), any(), any());
        verify(analysisService, never()).generateValidatedAnalysis(any());
    }

    @Test
    void hotProductsAnalysisRejectsInvalidModelOutputWithStableEnvelope() throws Exception {
        inventoryRepository.saveAndFlush(inventory("SKU-HOT-INVALID", 5, 0, 8));
        givenHotSales(2, List.of(
                new AiSalesEvidenceResponse("SKU-HOT-INVALID", 12, 4, new BigDecimal("320.00"))), 1);
        given(analysisService.generateValidatedAnalysis(any()))
                .willThrow(new AiOutputValidationException(
                        "analysisType LOW_STOCK does not match expected HOT_PRODUCTS"));

        mockMvc.perform(post("/api/admin/ai/inventory/hot-products-analysis")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(hotRequestJson(7, 2, 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()))
                .andExpect(jsonPath("$.message")
                        .value("AI output validation failed: analysisType LOW_STOCK does not match expected HOT_PRODUCTS"));
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

    private void givenHotSales(int limit, List<AiSalesEvidenceResponse> sales, long totalElements) {
        given(salesEvidenceClient.salesByProduct(
                        isNull(), any(LocalDateTime.class), any(LocalDateTime.class), eq(PageRequest.of(0, limit))))
                .willReturn(page(sales, totalElements));
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

    private String hotRequestJson(Integer days, Integer limit, Integer recordLimit) throws Exception {
        ObjectNode json = objectMapper.createObjectNode();
        if (days != null) {
            json.put("days", days);
        }
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

    private AiInventoryAnalysisResult hotProductsAnalysisResult(String summary) {
        ObjectNode output = objectMapper.createObjectNode();
        output.put("summary", summary);
        output.put("analysisType", "HOT_PRODUCTS");
        ObjectNode timeRange = output.putObject("timeRange");
        timeRange.put("from", "2026-05-10T00:00:00");
        timeRange.put("to", "2026-06-09T00:00:00");
        ArrayNode items = output.putArray("items");
        ObjectNode item = items.addObject();
        item.put("productId", "SKU-HOT-ANALYSIS");
        item.put("availableStock", 4);
        item.put("lockedStock", 0);
        item.put("safetyStock", 10);
        item.put("soldQuantityLast7Days", 21);
        item.put("riskLevel", "HIGH");
        item.put("reason", "Hot sales velocity may outpace current available stock.");
        output.putArray("limitations")
                .add("Model output is advisory and requires administrator review.");
        String outputJson = output.toString();
        return new AiInventoryAnalysisResult(
                AiPromptTemplateId.HOT_PRODUCTS_ANALYSIS,
                AiAnalysisType.HOT_PRODUCTS,
                "hot-products-analysis-v1",
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
