package com.minimall.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.inventory.ai.AiProviderTokenUsage;
import com.minimall.inventory.ai.AiProviderType;
import com.minimall.inventory.ai.analysis.AiInventoryAnalysisRequest;
import com.minimall.inventory.ai.analysis.AiInventoryAnalysisResult;
import com.minimall.inventory.ai.analysis.AiInventoryAnalysisService;
import com.minimall.inventory.ai.prompt.AiPromptTemplateId;
import com.minimall.inventory.ai.validation.AiAnalysisType;
import com.minimall.inventory.ai.validation.AiOutputValidationException;
import com.minimall.inventory.client.order.SalesEvidenceClient;
import com.minimall.inventory.domain.AiInventoryEvidenceType;
import com.minimall.inventory.domain.AiInventoryQuestionIntent;
import com.minimall.inventory.domain.AiSuggestionValidationStatus;
import com.minimall.inventory.domain.Inventory;
import com.minimall.inventory.domain.InventoryChangeType;
import com.minimall.inventory.domain.InventoryRecord;
import com.minimall.inventory.domain.InventoryRecordSourceType;
import com.minimall.inventory.dto.AiInventoryAskRequest;
import com.minimall.inventory.dto.AiInventoryAskResponse;
import com.minimall.inventory.repository.InventoryRecordRepository;
import com.minimall.inventory.repository.InventoryRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@Import({AiInventoryEvidenceFacade.class, AiInventoryQuestionService.class})
class AiInventoryQuestionServiceTest {

    @Autowired
    private AiInventoryQuestionService questionService;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private InventoryRecordRepository inventoryRecordRepository;

    @MockBean
    private SalesEvidenceClient salesEvidenceClient;

    @MockBean
    private AiInventoryAnalysisService analysisService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        inventoryRecordRepository.deleteAll();
        inventoryRepository.deleteAll();
        given(analysisService.generateValidatedAnalysis(any()))
                .willReturn(analysisResult("AI inventory answer", List.of("AI limitation")));
    }

    @Test
    void answersCurrentStockWithReadOnlyEvidence() {
        inventoryRepository.saveAndFlush(inventory("SKU-QA-STOCK", 9, 2, 5));
        long inventoryCount = inventoryRepository.count();
        long recordCount = inventoryRecordRepository.count();

        AiInventoryAskResponse response = questionService.answer(new AiInventoryAskRequest(
                "What is the current stock for this SKU?",
                " SKU-QA-STOCK ",
                null,
                0));

        assertThat(response.intent()).isEqualTo(AiInventoryQuestionIntent.CURRENT_STOCK);
        assertThat(response.supported()).isTrue();
        assertThat(response.queryTime()).isNotNull();
        assertThat(response.answer()).isEqualTo("AI inventory answer");
        assertThat(response.evidence().evidenceType()).isEqualTo(AiInventoryEvidenceType.CURRENT_INVENTORY);
        assertThat(response.evidence().inventories()).singleElement().satisfies(item -> {
            assertThat(item.productId()).isEqualTo("SKU-QA-STOCK");
            assertThat(item.availableStock()).isEqualTo(9);
            assertThat(item.lockedStock()).isEqualTo(2);
        });
        assertThat(response.limitations())
                .contains(
                        "库存问答为只读查询，不会锁定、扣减或调整库存。",
                        "AI limitation");
        assertThat(inventoryRepository.count()).isEqualTo(inventoryCount);
        assertThat(inventoryRecordRepository.count()).isEqualTo(recordCount);
        then(salesEvidenceClient).should(never())
                .salesByProduct(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        ArgumentCaptor<AiInventoryAnalysisRequest> requestCaptor =
                ArgumentCaptor.forClass(AiInventoryAnalysisRequest.class);
        then(analysisService).should().generateValidatedAnalysis(requestCaptor.capture());
        AiInventoryAnalysisRequest analysisRequest = requestCaptor.getValue();
        assertThat(analysisRequest.templateId()).isEqualTo(AiPromptTemplateId.INVENTORY_QA);
        assertThat(analysisRequest.expectedAnalysisType()).isEqualTo(AiAnalysisType.INVENTORY_QA);
        assertThat(analysisRequest.productFacts()).singleElement().satisfies(facts -> {
            assertThat(facts.productId()).isEqualTo("SKU-QA-STOCK");
            assertThat(facts.availableStock()).isEqualTo(9);
            assertThat(facts.lockedStock()).isEqualTo(2);
            assertThat(facts.safetyStock()).isEqualTo(5);
        });
        assertThat(analysisRequest.allowedDateValues()).isNotEmpty();
        assertThat(analysisRequest.inputSnapshot()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> snapshot = (Map<String, Object>) analysisRequest.inputSnapshot();
        assertThat(snapshot).containsEntry("question", "What is the current stock for this SKU?");
        assertThat(snapshot).containsEntry("productId", "SKU-QA-STOCK");
        assertThat(snapshot).containsEntry("detectedIntent", "CURRENT_STOCK");
        assertThat(snapshot).containsEntry("recordLimit", 0);
    }

    @Test
    void answersLowStockListWithBackendComputedCandidates() {
        inventoryRepository.saveAndFlush(inventory("SKU-QA-LOW", 1, 0, 5));
        inventoryRepository.saveAndFlush(inventory("SKU-QA-OK", 20, 0, 5));

        AiInventoryAskResponse response = questionService.answer(new AiInventoryAskRequest(
                "List low stock products",
                null,
                10,
                0));

        assertThat(response.intent()).isEqualTo(AiInventoryQuestionIntent.LOW_STOCK_LIST);
        assertThat(response.supported()).isTrue();
        assertThat(response.answer()).isEqualTo("AI inventory answer");
        assertThat(response.evidence().evidenceType()).isEqualTo(AiInventoryEvidenceType.LOW_STOCK_CANDIDATES);
        assertThat(response.evidence().inventories())
                .extracting(item -> item.productId())
                .containsExactly("SKU-QA-LOW");
        assertThat(response.evidence().records()).isEmpty();
        assertThat(response.limitations())
                .contains("问答证据仅使用后端计算的低库存候选。", "AI limitation");
    }

    @Test
    void answersProductStatusFromCurrentInventoryEvidence() {
        inventoryRepository.saveAndFlush(inventory("SKU-QA-STATUS", 0, 0, 3));

        AiInventoryAskResponse response = questionService.answer(new AiInventoryAskRequest(
                "Show health status for product",
                "SKU-QA-STATUS",
                null,
                0));

        assertThat(response.intent()).isEqualTo(AiInventoryQuestionIntent.PRODUCT_STATUS);
        assertThat(response.supported()).isTrue();
        assertThat(response.answer()).isEqualTo("AI inventory answer");
        assertThat(response.limitations()).contains("商品状态由当前库存证据推导。");
    }

    @Test
    void answersRecentRecordsWithBoundedRecordEvidence() {
        inventoryRepository.saveAndFlush(inventory("SKU-QA-RECORDS", 12, 0, 5));
        inventoryRecordRepository.saveAndFlush(record("SKU-QA-RECORDS", "REQ-QA-1"));

        AiInventoryAskResponse response = questionService.answer(new AiInventoryAskRequest(
                "Explain recent inventory records",
                "SKU-QA-RECORDS",
                null,
                5));

        assertThat(response.intent()).isEqualTo(AiInventoryQuestionIntent.RECENT_RECORDS);
        assertThat(response.supported()).isTrue();
        assertThat(response.answer()).isEqualTo("AI inventory answer");
        assertThat(response.evidence().records()).singleElement().satisfies(record -> {
            assertThat(record.productId()).isEqualTo("SKU-QA-RECORDS");
            assertThat(record.requestId()).isEqualTo("REQ-QA-1");
        });
        assertThat(response.limitations())
                .contains("流水证据仅限该商品最近的记录。");
    }

    @Test
    void unsupportedQuestionReturnsControlledUnsupportedIntent() {
        AiInventoryAskResponse response = questionService.answer(new AiInventoryAskRequest(
                "Can you negotiate with suppliers?",
                null,
                null,
                null));

        assertThat(response.intent()).isEqualTo(AiInventoryQuestionIntent.UNSUPPORTED);
        assertThat(response.supported()).isFalse();
        assertThat(response.evidence()).isNull();
        assertThat(response.answer()).isEqualTo("暂不支持该类库存问题。");
        assertThat(response.limitations()).singleElement()
                .isEqualTo("支持的问题类型：当前库存、低库存清单、商品状态、近期流水。");
        then(analysisService).should(never()).generateValidatedAnalysis(any());
    }

    @Test
    void extractsProductIdFromQuestionTextWhenFieldIsMissing() {
        inventoryRepository.saveAndFlush(inventory("PH3-AI-LOW-TEA", 4, 1, 12));

        AiInventoryAskResponse response = questionService.answer(new AiInventoryAskRequest(
                "PH3-AI-LOW-TEA 当前库存多少",
                null,
                null,
                0));

        assertThat(response.intent()).isEqualTo(AiInventoryQuestionIntent.CURRENT_STOCK);
        assertThat(response.supported()).isTrue();
        assertThat(response.evidence().inventories()).singleElement()
                .satisfies(item -> assertThat(item.productId()).isEqualTo("PH3-AI-LOW-TEA"));
    }

    @Test
    void ignoresQuestionTokensThatAreNotExistingProducts() {
        inventoryRepository.saveAndFlush(inventory("PH3-AI-LOW-TEA", 4, 1, 12));

        AiInventoryAskResponse response = questionService.answer(new AiInventoryAskRequest(
                "show current stock for PH3-AI-LOW-TEA please",
                null,
                null,
                0));

        // earlier non-product tokens ("show", "current", ...) must not be
        // trusted; the verified inventory row wins.
        assertThat(response.evidence().inventories()).singleElement()
                .satisfies(item -> assertThat(item.productId()).isEqualTo("PH3-AI-LOW-TEA"));
    }

    @Test
    void productSpecificIntentRequiresProductId() {
        assertThatThrownBy(() -> questionService.answer(new AiInventoryAskRequest(
                        "What is the current stock?",
                        null,
                        null,
                        null)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
                    assertThat(exception.getMessage()).contains("无法识别商品 ID");
                });
        then(analysisService).should(never()).generateValidatedAnalysis(any());
    }

    @Test
    void blankQuestionUsesControlledValidationError() {
        assertThatThrownBy(() -> questionService.answer(new AiInventoryAskRequest(
                        " ",
                        null,
                        null,
                        null)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(exception.getMessage()).isEqualTo("question must not be blank");
                });
        then(analysisService).should(never()).generateValidatedAnalysis(any());
    }

    @Test
    void invalidAiOutputUsesControlledValidationErrorBeforeReturningAnswer() {
        inventoryRepository.saveAndFlush(inventory("SKU-QA-INVALID-AI", 9, 0, 5));
        given(analysisService.generateValidatedAnalysis(any()))
                .willThrow(new AiOutputValidationException("output must be valid JSON"));

        assertThatThrownBy(() -> questionService.answer(new AiInventoryAskRequest(
                        "What is the current stock?",
                        "SKU-QA-INVALID-AI",
                        null,
                        0)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(exception.getMessage()).contains("AI output validation failed");
                });
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
                "Q&A test record",
                42L,
                "admin",
                InventoryRecordSourceType.ADMIN_ADJUSTMENT,
                requestId);
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
}
