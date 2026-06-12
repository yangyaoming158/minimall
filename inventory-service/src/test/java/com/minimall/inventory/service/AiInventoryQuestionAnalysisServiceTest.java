package com.minimall.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.inventory.ai.AiProvider;
import com.minimall.inventory.ai.AiProviderMessageRole;
import com.minimall.inventory.ai.AiProviderRequest;
import com.minimall.inventory.ai.AiProviderResponse;
import com.minimall.inventory.ai.AiProviderTokenUsage;
import com.minimall.inventory.ai.AiProviderType;
import com.minimall.inventory.ai.analysis.AiInventoryAnalysisService;
import com.minimall.inventory.ai.prompt.AiPromptTemplateCatalog;
import com.minimall.inventory.ai.validation.AiModelOutputValidator;
import com.minimall.inventory.ai.validation.AiOutputValidationException;
import com.minimall.inventory.domain.AiInventoryEvidenceType;
import com.minimall.inventory.domain.AiInventoryQuestionIntent;
import com.minimall.inventory.domain.InventoryStatus;
import com.minimall.inventory.domain.StockState;
import com.minimall.inventory.dto.AiInventoryAskRequest;
import com.minimall.inventory.dto.AiInventoryAskResponse;
import com.minimall.inventory.dto.AiInventoryEvidenceResponse;
import com.minimall.inventory.dto.AiInventoryItemEvidence;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiInventoryQuestionAnalysisServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void supportedQuestionReturnsOnlyValidatedAiAnswer() {
        StubAiProvider provider = new StubAiProvider(validQaOutput("SKU-QA-AI"));
        AiInventoryEvidenceFacade evidenceFacade = mock(AiInventoryEvidenceFacade.class);
        given(evidenceFacade.currentInventory("SKU-QA-AI", 0)).willReturn(evidence("SKU-QA-AI"));
        AiInventoryQuestionService service = new AiInventoryQuestionService(
                evidenceFacade,
                analysisService(provider),
                mock(com.minimall.inventory.repository.InventoryRepository.class));

        AiInventoryAskResponse response = service.answer(new AiInventoryAskRequest(
                "What is the current stock?",
                "SKU-QA-AI",
                null,
                0));

        assertThat(response.intent()).isEqualTo(AiInventoryQuestionIntent.CURRENT_STOCK);
        assertThat(response.supported()).isTrue();
        assertThat(response.answer()).isEqualTo("SKU-QA-AI has 9 available and 2 locked.");
        assertThat(response.evidence().inventories()).singleElement()
                .satisfies(item -> assertThat(item.productId()).isEqualTo("SKU-QA-AI"));
        assertThat(response.limitations()).contains(
                "库存问答为只读查询，不会锁定、扣减或调整库存。",
                "Answer is based on current inventory evidence only.");
        then(evidenceFacade).should().currentInventory("SKU-QA-AI", 0);
        assertThat(provider.lastRequest.promptVersion()).isEqualTo("inventory-qa-v2");
        assertThat(provider.lastRequest.outputSchemaVersion()).isEqualTo("inventory-analysis-output-v1");
        assertThat(provider.lastRequest.messages()).hasSize(2);
        assertThat(provider.lastRequest.messages().get(0).role()).isEqualTo(AiProviderMessageRole.SYSTEM);
        assertThat(provider.lastRequest.messages().get(1).content()).contains(
                "Answer the administrator inventory question",
                "Input snapshot JSON:",
                "SKU-QA-AI");
        assertThat(provider.lastRequest.input()).containsEntry("templateId", "INVENTORY_QA");
        assertThat(provider.lastRequest.input()).containsEntry("analysisType", "INVENTORY_QA");
    }

    @Test
    void invalidProviderOutputIsRejectedBeforeAnswerIsReturned() {
        StubAiProvider provider = new StubAiProvider(validQaOutput("SKU-MISSING"));
        AiInventoryEvidenceFacade evidenceFacade = mock(AiInventoryEvidenceFacade.class);
        given(evidenceFacade.currentInventory("SKU-QA-AI", 0)).willReturn(evidence("SKU-QA-AI"));
        AiInventoryQuestionService service = new AiInventoryQuestionService(
                evidenceFacade,
                analysisService(provider),
                mock(com.minimall.inventory.repository.InventoryRepository.class));

        assertThatThrownBy(() -> service.answer(new AiInventoryAskRequest(
                        "What is the current stock?",
                        "SKU-QA-AI",
                        null,
                        0)))
                .isInstanceOfSatisfying(AiOutputValidationException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(exception.getMessage()).contains("$.items[0].productId SKU-MISSING is not in input snapshot");
                });
    }

    private AiInventoryAnalysisService analysisService(StubAiProvider provider) {
        return new AiInventoryAnalysisService(
                provider,
                new AiPromptTemplateCatalog(objectMapper),
                new AiModelOutputValidator(objectMapper),
                objectMapper);
    }

    private AiInventoryEvidenceResponse evidence(String productId) {
        LocalDateTime dataFrom = LocalDateTime.of(2026, 6, 8, 10, 0);
        LocalDateTime dataTo = LocalDateTime.of(2026, 6, 8, 10, 5);
        return new AiInventoryEvidenceResponse(
                AiInventoryEvidenceType.CURRENT_INVENTORY,
                dataTo,
                dataFrom,
                dataTo,
                List.of(new AiInventoryItemEvidence(
                        productId,
                        9,
                        2,
                        5,
                        InventoryStatus.ACTIVE,
                        StockState.IN_STOCK,
                        false,
                        dataFrom,
                        dataTo)),
                List.of());
    }

    private String validQaOutput(String productId) {
        return """
                {
                  "summary": "%s has 9 available and 2 locked.",
                  "analysisType": "INVENTORY_QA",
                  "timeRange": {
                    "from": "2026-06-08T10:00",
                    "to": "2026-06-08T10:05"
                  },
                  "items": [
                    {
                      "productId": "%s",
                      "availableStock": 9,
                      "lockedStock": 2,
                      "safetyStock": 5,
                      "reason": "Current inventory evidence shows 9 available and 2 locked."
                    }
                  ],
                  "limitations": [
                    "Answer is based on current inventory evidence only."
                  ]
                }
                """.formatted(productId, productId);
    }

    private static final class StubAiProvider implements AiProvider {

        private final String content;
        private AiProviderRequest lastRequest;

        private StubAiProvider(String content) {
            this.content = content;
        }

        @Override
        public AiProviderType providerType() {
            return AiProviderType.MOCK;
        }

        @Override
        public AiProviderResponse generate(AiProviderRequest request) {
            lastRequest = request;
            return new AiProviderResponse(
                    AiProviderType.MOCK,
                    "mock-qa-model",
                    content,
                    new AiProviderTokenUsage(10, 20, 30),
                    "mock-qa-request");
        }
    }
}
