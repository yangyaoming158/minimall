package com.minimall.inventory.ai.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.inventory.ai.AiProvider;
import com.minimall.inventory.ai.AiProviderMessageRole;
import com.minimall.inventory.ai.AiProviderRequest;
import com.minimall.inventory.ai.AiProviderResponse;
import com.minimall.inventory.ai.AiProviderTokenUsage;
import com.minimall.inventory.ai.AiProviderType;
import com.minimall.inventory.ai.prompt.AiPromptTemplateCatalog;
import com.minimall.inventory.ai.prompt.AiPromptTemplateId;
import com.minimall.inventory.ai.validation.AiAnalysisType;
import com.minimall.inventory.ai.validation.AiModelOutputValidator;
import com.minimall.inventory.ai.validation.AiOutputProductFacts;
import com.minimall.inventory.ai.validation.AiOutputValidationException;
import com.minimall.inventory.domain.AiSuggestionValidationStatus;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AiInventoryAnalysisServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void generatesOnlyValidatedAnalysisResult() {
        StubAiProvider provider = new StubAiProvider(validReplenishmentOutput());
        AiInventoryAnalysisService service = service(provider);

        AiInventoryAnalysisResult result = service.generateValidatedAnalysis(request());

        assertThat(result.templateId()).isEqualTo(AiPromptTemplateId.REPLENISHMENT_SUGGESTION);
        assertThat(result.analysisType()).isEqualTo(AiAnalysisType.REPLENISHMENT);
        assertThat(result.promptVersion()).isEqualTo("replenishment-suggestion-v2");
        assertThat(result.outputSchemaVersion()).isEqualTo("inventory-analysis-output-v1");
        assertThat(result.provider()).isEqualTo(AiProviderType.MOCK);
        assertThat(result.model()).isEqualTo("mock-analysis-model");
        assertThat(result.providerRequestId()).isEqualTo("mock-request-1");
        assertThat(result.validationStatus()).isEqualTo(AiSuggestionValidationStatus.VALID);
        assertThat(result.inputSnapshotJson()).contains("SKU-LOW-1", "2026-06-01T00:00:00");
        assertThat(result.rawModelOutputJson()).contains("Restock SKU-LOW-1");
        assertThat(result.validatedOutputJson()).contains("\"analysisType\":\"REPLENISHMENT\"");
        assertThat(result.output().path("items").get(0).path("productId").asText()).isEqualTo("SKU-LOW-1");

        assertThat(provider.lastRequest.promptVersion()).isEqualTo("replenishment-suggestion-v2");
        assertThat(provider.lastRequest.outputSchemaVersion()).isEqualTo("inventory-analysis-output-v1");
        assertThat(provider.lastRequest.messages()).hasSize(2);
        assertThat(provider.lastRequest.messages().get(0).role()).isEqualTo(AiProviderMessageRole.SYSTEM);
        assertThat(provider.lastRequest.messages().get(1).content()).contains(
                "Generate a reviewable replenishment recommendation",
                "Input snapshot JSON:",
                "SKU-LOW-1");
        assertThat(provider.lastRequest.input()).containsEntry("analysisType", "REPLENISHMENT");
        assertThat(provider.lastRequest.input()).containsEntry("templateId", "REPLENISHMENT_SUGGESTION");
    }

    @ParameterizedTest
    @MethodSource("invalidOutputs")
    void rejectsInvalidProviderOutputBeforeReturningTrustedAnalysis(
            String rawOutput,
            String messageFragment) {
        AiInventoryAnalysisService service = service(new StubAiProvider(rawOutput));

        assertThatThrownBy(() -> service.generateValidatedAnalysis(request()))
                .isInstanceOfSatisfying(AiOutputValidationException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(exception.getMessage()).contains(messageFragment);
                });
    }

    private static Stream<Arguments> invalidOutputs() {
        String valid = validReplenishmentOutput();
        return Stream.of(
                Arguments.of("not-json", "output must be valid JSON"),
                Arguments.of(valid.replace("\"REPLENISHMENT\"", "\"EXECUTE_SQL\""),
                        "unsupported analysisType: EXECUTE_SQL"),
                Arguments.of(valid.replace("\"SKU-LOW-1\"", "\"SKU-MISSING\""),
                        "$.items[0].productId SKU-MISSING is not in input snapshot"),
                Arguments.of(valid.replace("\"availableStock\": 2", "\"availableStock\": 99"),
                        "$.items[0].availableStock for SKU-LOW-1 does not match input snapshot"),
                Arguments.of(valid.replace("\"lockedStock\": 1", "\"lockedStock\": -1"),
                        "$.items[0].lockedStock must be non-negative"),
                Arguments.of(valid.replace("\"suggestedQuantity\": 8", "\"suggestedQuantity\": 0"),
                        "$.items[0].suggestedQuantity must be a positive integer"),
                Arguments.of(valid.replace("Restock SKU-LOW-1", "SELECT * FROM inventory"),
                        "SQL content is not allowed"),
                Arguments.of(valid.replace("Restock SKU-LOW-1", "Inventory has been changed for SKU-LOW-1"),
                        "stock-change claims are not allowed"));
    }

    private AiInventoryAnalysisService service(StubAiProvider provider) {
        return new AiInventoryAnalysisService(
                provider,
                new AiPromptTemplateCatalog(objectMapper),
                new AiModelOutputValidator(objectMapper),
                objectMapper);
    }

    private AiInventoryAnalysisRequest request() {
        return new AiInventoryAnalysisRequest(
                AiPromptTemplateId.REPLENISHMENT_SUGGESTION,
                AiAnalysisType.REPLENISHMENT,
                Map.of(
                        "generatedAt", "2026-06-08T00:00:00",
                        "dataFrom", "2026-06-01T00:00:00",
                        "dataTo", "2026-06-08T00:00:00",
                        "products", List.of(Map.of(
                                "productId", "SKU-LOW-1",
                                "productName", "Low Stock Product",
                                "availableStock", 2,
                                "lockedStock", 1,
                                "safetyStock", 10,
                                "soldQuantityLast7Days", 12))),
                List.of(new AiOutputProductFacts(
                        "SKU-LOW-1",
                        "Low Stock Product",
                        2,
                        1,
                        10,
                        12L)),
                List.of("2026-06-01T00:00:00", "2026-06-08T00:00:00"));
    }

    private static String validReplenishmentOutput() {
        return """
                {
                  "summary": "Restock SKU-LOW-1",
                  "analysisType": "REPLENISHMENT",
                  "timeRange": {
                    "from": "2026-06-01T00:00:00",
                    "to": "2026-06-08T00:00:00"
                  },
                  "items": [
                    {
                      "productId": "SKU-LOW-1",
                      "productName": "Low Stock Product",
                      "availableStock": 2,
                      "lockedStock": 1,
                      "safetyStock": 10,
                      "soldQuantityLast7Days": 12,
                      "suggestedQuantity": 8,
                      "riskLevel": "HIGH",
                      "reason": "Current stock is below safety stock with recent paid sales evidence."
                    }
                  ],
                  "limitations": [
                    "Sales evidence is limited to paid orders in the selected window."
                  ]
                }
                """;
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
                    "mock-analysis-model",
                    content,
                    new AiProviderTokenUsage(10, 20, 30),
                    "mock-request-1");
        }
    }
}
