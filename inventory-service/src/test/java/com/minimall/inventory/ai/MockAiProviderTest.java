package com.minimall.inventory.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minimall.inventory.ai.validation.AiAnalysisType;
import com.minimall.inventory.ai.validation.AiModelOutputValidator;
import com.minimall.inventory.ai.validation.AiOutputProductFacts;
import com.minimall.inventory.ai.validation.AiOutputValidationContext;
import com.minimall.inventory.ai.validation.AiValidatedOutput;
import com.minimall.inventory.config.AiProviderProperties;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MockAiProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void returnsDeterministicLocalJsonWithoutNetwork() throws Exception {
        MockAiProvider provider = new MockAiProvider(new AiProviderProperties(), objectMapper);

        AiProviderResponse first = provider.generate(request());
        AiProviderResponse second = provider.generate(request());

        assertThat(first.provider()).isEqualTo(AiProviderType.MOCK);
        assertThat(first.model()).isEqualTo("mock-ai-provider");
        assertThat(first.requestId()).isEqualTo(second.requestId());
        assertThat(first.content()).isEqualTo(second.content());
        assertThat(first.tokenUsage().totalTokens()).isGreaterThan(0);

        JsonNode content = objectMapper.readTree(first.content());
        assertThat(content.get("summary").asText()).isEqualTo("Mock AI provider response");
        assertThat(content.get("analysisType").asText()).isEqualTo("REPLENISHMENT");
        assertThat(content.get("provider").asText()).isEqualTo("MOCK");
        assertThat(content.get("promptVersion").asText()).isEqualTo("replenishment-v1");
        assertThat(content.get("outputSchemaVersion").asText()).isEqualTo("inventory-suggestion-v1");
        assertThat(content.get("items")).isEmpty();
        assertThat(content.get("limitations")).hasSize(1);
    }

    @Test
    void derivesDeterministicItemsFromProductFacts() throws Exception {
        MockAiProvider provider = new MockAiProvider(new AiProviderProperties(), objectMapper);

        JsonNode content = objectMapper.readTree(provider.generate(factsRequest()).content());
        JsonNode items = content.get("items");

        assertThat(items).hasSize(3);

        JsonNode lowStockItem = items.get(0);
        assertThat(lowStockItem.get("productId").asText()).isEqualTo("SKU-MOCK-LOW");
        assertThat(lowStockItem.get("productName").asText()).isEqualTo("Low Product");
        assertThat(lowStockItem.get("availableStock").asInt()).isEqualTo(2);
        assertThat(lowStockItem.get("lockedStock").asInt()).isEqualTo(1);
        assertThat(lowStockItem.get("safetyStock").asInt()).isEqualTo(5);
        assertThat(lowStockItem.get("soldQuantityLast7Days").asLong()).isEqualTo(9);
        assertThat(lowStockItem.get("riskLevel").asText()).isEqualTo("HIGH");
        // safetyStock * 2 - availableStock = 10 - 2 = 8
        assertThat(lowStockItem.get("suggestedQuantity").asLong()).isEqualTo(8);
        assertThat(lowStockItem.get("reason").asText()).isNotBlank();

        JsonNode healthyItem = items.get(1);
        assertThat(healthyItem.get("productId").asText()).isEqualTo("SKU-MOCK-HEALTHY");
        assertThat(healthyItem.get("productName")).isNull();
        assertThat(healthyItem.get("riskLevel").asText()).isEqualTo("LOW");
        // safetyStock * 2 - availableStock = 6 - 20 < 1, clamps to minimum 1
        assertThat(healthyItem.get("suggestedQuantity").asLong()).isEqualTo(1);

        JsonNode sparseItem = items.get(2);
        assertThat(sparseItem.get("productId").asText()).isEqualTo("SKU-MOCK-SPARSE");
        assertThat(sparseItem.get("availableStock")).isNull();
        assertThat(sparseItem.get("safetyStock")).isNull();
        assertThat(sparseItem.get("riskLevel").asText()).isEqualTo("MEDIUM");
        // No stock facts: falls back to soldQuantityLast7Days
        assertThat(sparseItem.get("suggestedQuantity").asLong()).isEqualTo(7);
    }

    @Test
    void derivedItemsPassAntiHallucinationValidation() {
        MockAiProvider provider = new MockAiProvider(new AiProviderProperties(), objectMapper);
        AiModelOutputValidator validator = new AiModelOutputValidator(objectMapper);
        AiProviderResponse response = provider.generate(factsRequest());

        AiValidatedOutput validated = validator.validate(
                response.content(),
                AiOutputValidationContext.of(
                        AiAnalysisType.REPLENISHMENT,
                        productFacts(),
                        List.of("2026-06-10T00:00:00", "2026-06-11T00:00:00")));

        assertThat(validated.analysisType()).isEqualTo(AiAnalysisType.REPLENISHMENT);
        assertThat(validated.output().get("items")).hasSize(3);
    }

    @Test
    void rejectsMockProviderWhenDisabled() {
        AiProviderProperties properties = new AiProviderProperties();
        properties.setMockEnabled(false);
        MockAiProvider provider = new MockAiProvider(properties, objectMapper);

        assertThatThrownBy(() -> provider.generate(request()))
                .isInstanceOfSatisfying(AiProviderException.class, exception -> {
                    assertThat(exception.getProviderType()).isEqualTo(AiProviderType.MOCK);
                    assertThat(exception.getProviderErrorType()).isEqualTo(AiProviderErrorType.CONFIGURATION_ERROR);
                    assertThat(exception.getMessage()).isEqualTo("AI mock provider is disabled");
                });
    }

    @Test
    void usesConfiguredMockModelNameWhenPresent() {
        AiProviderProperties properties = new AiProviderProperties();
        properties.setModel("local-demo-model");
        MockAiProvider provider = new MockAiProvider(properties, objectMapper);

        assertThat(provider.generate(request()).model()).isEqualTo("local-demo-model");
    }

    private AiProviderRequest request() {
        return new AiProviderRequest(
                "replenishment-v1",
                "inventory-suggestion-v1",
                List.of(AiProviderMessage.user("Analyze SKU-MOCK-1.")),
                Map.of("productId", "SKU-MOCK-1"));
    }

    private AiProviderRequest factsRequest() {
        return new AiProviderRequest(
                "replenishment-v1",
                "inventory-suggestion-v1",
                List.of(AiProviderMessage.user("Generate replenishment suggestions.")),
                Map.of(
                        "analysisType", "REPLENISHMENT",
                        "productFacts", productFacts(),
                        "allowedDateValues", List.of("2026-06-10T00:00:00", "2026-06-11T00:00:00")));
    }

    private List<AiOutputProductFacts> productFacts() {
        return List.of(
                new AiOutputProductFacts("SKU-MOCK-LOW", "Low Product", 2, 1, 5, 9L),
                new AiOutputProductFacts("SKU-MOCK-HEALTHY", null, 20, 0, 3, 2L),
                new AiOutputProductFacts("SKU-MOCK-SPARSE", null, null, null, null, 7L));
    }
}
