package com.minimall.inventory.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        assertThat(first.tokenUsage().totalTokens()).isGreaterThan(0);

        JsonNode content = objectMapper.readTree(first.content());
        assertThat(content.get("provider").asText()).isEqualTo("MOCK");
        assertThat(content.get("promptVersion").asText()).isEqualTo("replenishment-v1");
        assertThat(content.get("outputSchemaVersion").asText()).isEqualTo("inventory-suggestion-v1");
        assertThat(content.get("items")).isEmpty();
        assertThat(content.get("input").get("productId").asText()).isEqualTo("SKU-MOCK-1");
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
}
