package com.minimall.inventory.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import com.minimall.inventory.ai.AiProviderTokenUsage;
import com.minimall.inventory.ai.AiProviderType;
import com.minimall.inventory.ai.analysis.AiInventoryAnalysisRequest;
import com.minimall.inventory.ai.analysis.AiInventoryAnalysisResult;
import com.minimall.inventory.ai.analysis.AiInventoryAnalysisService;
import com.minimall.inventory.ai.prompt.AiPromptTemplateId;
import com.minimall.inventory.ai.validation.AiAnalysisType;
import com.minimall.inventory.client.order.SalesEvidenceClient;
import com.minimall.inventory.domain.AiSuggestionValidationStatus;
import com.minimall.inventory.domain.Inventory;
import com.minimall.inventory.domain.InventoryChangeType;
import com.minimall.inventory.domain.InventoryRecord;
import com.minimall.inventory.domain.InventoryRecordSourceType;
import com.minimall.inventory.repository.InventoryRecordRepository;
import com.minimall.inventory.repository.InventoryRepository;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
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
        "spring.datasource.url=jdbc:h2:mem:admin_ai_question_controller;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "minimall.auth.jwt.secret=test-jwt-secret-for-admin-ai-question-controller",
        "minimall.auth.jwt.expire-seconds=3600"
})
class AdminAiInventoryQuestionControllerTest {

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
    private JdbcTemplate jdbcTemplate;

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
                .willReturn(analysisResult("SKU-ASK has 6 available and 2 locked.", List.of("AI limitation")));
    }

    @Test
    void askRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/admin/ai/inventory/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(askJson("What is the current stock?", "SKU-ASK", null, 0)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()))
                .andExpect(jsonPath("$.message").value("Unauthorized"));

        verify(analysisService, never()).generateValidatedAnalysis(any());
    }

    @Test
    void askRejectsNonAdmin() throws Exception {
        mockMvc.perform(post("/api/admin/ai/inventory/ask")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(askJson("What is the current stock?", "SKU-ASK", null, 0)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()))
                .andExpect(jsonPath("$.message").value(ErrorCode.FORBIDDEN.getMessage()));

        verify(analysisService, never()).generateValidatedAnalysis(any());
    }

    @Test
    void askReturnsSupportedAnswerAndDoesNotMutateInventory() throws Exception {
        Inventory inventory = inventoryRepository.saveAndFlush(inventory("SKU-ASK", 6, 2, 8));
        touchInventory(inventory.getId(), LocalDateTime.of(2026, 6, 8, 9, 0));
        inventoryRecordRepository.saveAndFlush(record("SKU-ASK", "REQ-ASK-1"));
        long beforeInventoryCount = inventoryRepository.count();
        long beforeRecordCount = inventoryRecordRepository.count();

        mockMvc.perform(post("/api/admin/ai/inventory/ask")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(askJson("What is the current stock?", " SKU-ASK ", null, 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.intent").value("CURRENT_STOCK"))
                .andExpect(jsonPath("$.data.supported").value(true))
                .andExpect(jsonPath("$.data.answer").value("SKU-ASK has 6 available and 2 locked."))
                .andExpect(jsonPath("$.data.evidence.evidenceType").value("CURRENT_INVENTORY"))
                .andExpect(jsonPath("$.data.evidence.inventories[0].productId").value("SKU-ASK"))
                .andExpect(jsonPath("$.data.evidence.inventories[0].availableStock").value(6))
                .andExpect(jsonPath("$.data.evidence.inventories[0].lockedStock").value(2))
                .andExpect(jsonPath("$.data.evidence.records[0].requestId").value("REQ-ASK-1"))
                .andExpect(jsonPath("$.data.limitations[0]")
                        .value("Inventory Q&A is read-only and does not reserve, deduct, or adjust stock."))
                .andExpect(jsonPath("$.data.limitations[1]").value("AI limitation"));

        assertThat(inventoryRepository.count()).isEqualTo(beforeInventoryCount);
        assertThat(inventoryRecordRepository.count()).isEqualTo(beforeRecordCount);
        assertThat(inventoryRepository.findByProductId("SKU-ASK"))
                .get()
                .satisfies(saved -> {
                    assertThat(saved.getAvailableStock()).isEqualTo(6);
                    assertThat(saved.getLockedStock()).isEqualTo(2);
                });
        verify(adminAuditWriter, never()).write(any());
        ArgumentCaptor<AiInventoryAnalysisRequest> requestCaptor =
                ArgumentCaptor.forClass(AiInventoryAnalysisRequest.class);
        verify(analysisService).generateValidatedAnalysis(requestCaptor.capture());
        assertThat(requestCaptor.getValue().productFacts()).singleElement()
                .satisfies(facts -> assertThat(facts.productId()).isEqualTo("SKU-ASK"));
    }

    @Test
    void askReturnsControlledUnsupportedIntent() throws Exception {
        mockMvc.perform(post("/api/admin/ai/inventory/ask")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(askJson("Can you negotiate with suppliers?", null, null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.intent").value("UNSUPPORTED"))
                .andExpect(jsonPath("$.data.supported").value(false))
                .andExpect(jsonPath("$.data.answer").value("Unsupported inventory question intent."))
                .andExpect(jsonPath("$.data.evidence").doesNotExist())
                .andExpect(jsonPath("$.data.limitations[0]")
                        .value("Supported questions cover current stock, low-stock lists, product status, and recent records."));

        verify(analysisService, never()).generateValidatedAnalysis(any());
        verify(adminAuditWriter, never()).write(any());
    }

    @Test
    void askRejectsInvalidRequestWithApiResponseEnvelope() throws Exception {
        mockMvc.perform(post("/api/admin/ai/inventory/ask")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(askJson(" ", null, null, null)))
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
                InventoryChangeType.ADJUST_INCREASE,
                4,
                requestId,
                "Q&A endpoint test record",
                42L,
                "admin",
                InventoryRecordSourceType.ADMIN_ADJUSTMENT,
                requestId);
    }

    private void touchInventory(Long inventoryId, LocalDateTime timestamp) {
        jdbcTemplate.update(
                "update inventory set created_at = ?, updated_at = ? where id = ?",
                Timestamp.valueOf(timestamp),
                Timestamp.valueOf(timestamp),
                inventoryId);
    }

    private String askJson(String question, String productId, Integer limit, Integer recordLimit) throws Exception {
        ObjectNode json = objectMapper.createObjectNode();
        if (question != null) {
            json.put("question", question);
        }
        if (productId != null) {
            json.put("productId", productId);
        }
        if (limit != null) {
            json.put("limit", limit);
        }
        if (recordLimit != null) {
            json.put("recordLimit", recordLimit);
        }
        return objectMapper.writeValueAsString(json);
    }

    private AiInventoryAnalysisResult analysisResult(String summary, List<String> limitations) {
        ObjectNode output = objectMapper.createObjectNode();
        output.put("summary", summary);
        output.put("analysisType", "INVENTORY_QA");
        output.putArray("items");
        ArrayNode limitationNodes = output.putArray("limitations");
        limitations.forEach(limitationNodes::add);
        String outputJson = output.toString();
        return new AiInventoryAnalysisResult(
                AiPromptTemplateId.INVENTORY_QA,
                AiAnalysisType.INVENTORY_QA,
                "inventory-qa-v1",
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
