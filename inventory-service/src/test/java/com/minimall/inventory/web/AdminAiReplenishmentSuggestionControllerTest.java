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
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.minimall.common.auth.context.AuthRole;
import com.minimall.common.auth.jwt.JwtUtils;
import com.minimall.common.core.audit.AdminAuditAction;
import com.minimall.common.core.audit.AdminAuditLogWriteRequest;
import com.minimall.common.core.audit.AdminAuditWriter;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.common.core.response.PageResponse;
import com.minimall.inventory.ai.AiProvider;
import com.minimall.inventory.ai.AiProviderResponse;
import com.minimall.inventory.ai.AiProviderTokenUsage;
import com.minimall.inventory.ai.AiProviderType;
import com.minimall.inventory.ai.MockAiProvider;
import com.minimall.inventory.client.order.SalesEvidenceClient;
import com.minimall.inventory.config.AiProviderProperties;
import com.minimall.inventory.domain.AiOperationSuggestionStatus;
import com.minimall.inventory.domain.Inventory;
import com.minimall.inventory.dto.AiSalesEvidenceResponse;
import com.minimall.inventory.repository.AiOperationSuggestionItemRepository;
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
        "spring.datasource.url=jdbc:h2:mem:admin_ai_replenishment_controller;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "minimall.auth.jwt.secret=test-jwt-secret-for-admin-ai-replenishment-controller",
        "minimall.auth.jwt.expire-seconds=3600"
})
class AdminAiReplenishmentSuggestionControllerTest {

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
    private AiOperationSuggestionItemRepository itemRepository;

    @Autowired
    private InboundOrderRepository inboundOrderRepository;

    @MockBean
    private SalesEvidenceClient salesEvidenceClient;

    @MockBean
    private AdminAuditWriter adminAuditWriter;

    @MockBean
    private AiProvider aiProvider;

    @BeforeEach
    void setUp() {
        itemRepository.deleteAll();
        suggestionRepository.deleteAll();
        inventoryRecordRepository.deleteAll();
        inventoryRepository.deleteAll();
        reset(salesEvidenceClient, adminAuditWriter, aiProvider);
        givenHotSales(List.of());
    }

    @Test
    void generateRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/admin/ai/replenishment-suggestions/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(5, 1)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()))
                .andExpect(jsonPath("$.message").value("Unauthorized"));

        verify(aiProvider, never()).generate(any());
        assertThat(suggestionRepository.count()).isZero();
    }

    @Test
    void generateRejectsNonAdmin() throws Exception {
        mockMvc.perform(post("/api/admin/ai/replenishment-suggestions/generate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(5, 1)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()))
                .andExpect(jsonPath("$.message").value(ErrorCode.FORBIDDEN.getMessage()));

        verify(aiProvider, never()).generate(any());
        assertThat(suggestionRepository.count()).isZero();
    }

    @Test
    void generatePersistsPendingReviewSuggestionWithoutMutatingInventory() throws Exception {
        inventoryRepository.saveAndFlush(inventory("SKU-GENERATE-1", 2, 1, 5));
        givenProductSales("SKU-GENERATE-1", 9, 3, "198.00");
        delegateProviderToRealMock();
        long beforeInventoryCount = inventoryRepository.count();
        long beforeRecordCount = inventoryRecordRepository.count();

        mockMvc.perform(post("/api/admin/ai/replenishment-suggestions/generate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                        .header("X-Request-Id", "REQ-AI-GENERATE-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(5, 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.suggestionNo").exists())
                .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.data.items[0].productId").value("SKU-GENERATE-1"))
                .andExpect(jsonPath("$.data.items[0].suggestedQuantity").value(8))
                .andExpect(jsonPath("$.data.items[0].riskLevel").value("HIGH"));

        assertThat(suggestionRepository.count()).isEqualTo(1);
        assertThat(itemRepository.count()).isEqualTo(1);
        assertThat(suggestionRepository.findAll())
                .singleElement()
                .satisfies(saved -> {
                    assertThat(saved.getStatus()).isEqualTo(AiOperationSuggestionStatus.PENDING_REVIEW);
                    assertThat(saved.getModelProvider()).isEqualTo("MOCK");
                    assertThat(saved.getPromptVersion()).isEqualTo("replenishment-suggestion-v1");
                    assertThat(saved.getLinkedInboundNo()).isNull();
                });

        assertThat(inventoryRepository.count()).isEqualTo(beforeInventoryCount);
        assertThat(inventoryRecordRepository.count()).isEqualTo(beforeRecordCount);
        assertThat(inventoryRepository.findByProductId("SKU-GENERATE-1"))
                .get()
                .satisfies(inventory -> {
                    assertThat(inventory.getAvailableStock()).isEqualTo(2);
                    assertThat(inventory.getLockedStock()).isEqualTo(1);
                });
        assertThat(inboundOrderRepository.count()).isZero();

        ArgumentCaptor<AdminAuditLogWriteRequest> auditCaptor =
                ArgumentCaptor.forClass(AdminAuditLogWriteRequest.class);
        verify(adminAuditWriter).write(auditCaptor.capture());
        assertThat(auditCaptor.getValue().action()).isEqualTo(AdminAuditAction.AI_SUGGESTION_CREATE);
    }

    @Test
    void generateRejectsInvalidModelOutputWithStableEnvelopeAndDoesNotPersist() throws Exception {
        inventoryRepository.saveAndFlush(inventory("SKU-GENERATE-2", 1, 0, 4));
        givenProductSales("SKU-GENERATE-2", 4, 2, "88.00");
        givenProviderContent("""
                {"summary":"bad","analysisType":"REPLENISHMENT","items":[
                {"productId":"SKU-UNKNOWN","suggestedQuantity":3,"riskLevel":"HIGH"}],
                "limitations":[]}
                """);

        mockMvc.perform(post("/api/admin/ai/replenishment-suggestions/generate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(5, 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()));

        assertThat(suggestionRepository.count()).isZero();
        assertThat(itemRepository.count()).isZero();
        verify(adminAuditWriter, never()).write(any());
    }

    @Test
    void generateRejectsWhenNoEvidenceWithControlledConflict() throws Exception {
        mockMvc.perform(post("/api/admin/ai/replenishment-suggestions/generate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(5, 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.CONFLICT.getCode()));

        verify(aiProvider, never()).generate(any());
        assertThat(suggestionRepository.count()).isZero();
    }

    @Test
    void generateRejectsInvalidRequestWithApiResponseEnvelope() throws Exception {
        mockMvc.perform(post("/api/admin/ai/replenishment-suggestions/generate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(0, 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()));

        verify(aiProvider, never()).generate(any());
        assertThat(suggestionRepository.count()).isZero();
    }

    private void delegateProviderToRealMock() {
        MockAiProvider realMock = new MockAiProvider(new AiProviderProperties(), objectMapper);
        given(aiProvider.generate(any()))
                .willAnswer(invocation -> realMock.generate(invocation.getArgument(0)));
    }

    private void givenProviderContent(String content) {
        given(aiProvider.generate(any())).willReturn(new AiProviderResponse(
                AiProviderType.MOCK,
                "stub-model",
                content,
                AiProviderTokenUsage.empty(),
                "stub-request"));
    }

    private Inventory inventory(String productId, int availableStock, int lockedStock, int safetyStock) {
        Inventory inventory = new Inventory(productId, availableStock, lockedStock);
        inventory.setSafetyStock(safetyStock);
        return inventory;
    }

    private void givenProductSales(String productId, long soldQuantity, long orderCount, String totalAmount) {
        given(salesEvidenceClient.salesByProduct(
                        eq(productId), any(LocalDateTime.class), any(LocalDateTime.class), eq(PageRequest.of(0, 1))))
                .willReturn(page(List.of(new AiSalesEvidenceResponse(
                        productId, soldQuantity, orderCount, new BigDecimal(totalAmount))), 1));
    }

    private void givenHotSales(List<AiSalesEvidenceResponse> sales) {
        given(salesEvidenceClient.salesByProduct(
                        isNull(), any(LocalDateTime.class), any(LocalDateTime.class), any(PageRequest.class)))
                .willReturn(page(sales, sales.size()));
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

    private String adminToken() {
        return jwtUtils.generateToken(42L, "admin", AuthRole.ADMIN);
    }

    private String userToken() {
        return jwtUtils.generateToken(43L, "alice", AuthRole.USER);
    }
}
