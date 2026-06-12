package com.minimall.inventory.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minimall.common.core.exception.ErrorCode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AiProviderContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void requestSerializesStableProviderContract() {
        AiProviderRequest request = new AiProviderRequest(
                " replenishment-v1 ",
                " inventory-suggestion-v1 ",
                List.of(
                        AiProviderMessage.system("Return strict JSON only."),
                        AiProviderMessage.user("Analyze low stock products.")),
                Map.of("productId", "SKU-AI-1", "availableStock", 3));

        JsonNode json = objectMapper.valueToTree(request);

        assertThat(json.get("promptVersion").asText()).isEqualTo("replenishment-v1");
        assertThat(json.get("outputSchemaVersion").asText()).isEqualTo("inventory-suggestion-v1");
        assertThat(json.get("messages")).hasSize(2);
        assertThat(json.get("messages").get(0).get("role").asText()).isEqualTo("SYSTEM");
        assertThat(json.get("messages").get(1).get("content").asText()).isEqualTo("Analyze low stock products.");
        assertThat(json.get("input").get("productId").asText()).isEqualTo("SKU-AI-1");
    }

    @Test
    void requestDefensivelyCopiesMessagesAndInput() {
        List<AiProviderMessage> messages = new ArrayList<>();
        messages.add(AiProviderMessage.user("Analyze SKU-AI-2."));
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("productId", "SKU-AI-2");

        AiProviderRequest request = new AiProviderRequest(
                "prompt-v1",
                "schema-v1",
                messages,
                input);

        messages.clear();
        input.put("extra", "ignored");

        assertThat(request.messages()).hasSize(1);
        assertThat(request.input()).containsEntry("productId", "SKU-AI-2");
        assertThat(request.input()).doesNotContainKey("extra");
        assertThatThrownBy(() -> request.input().put("other", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void requestRejectsMissingRequiredFields() {
        assertThatThrownBy(() -> new AiProviderRequest(" ", "schema-v1", List.of(AiProviderMessage.user("x")), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("promptVersion");
        assertThatThrownBy(() -> new AiProviderRequest("prompt-v1", " ", List.of(AiProviderMessage.user("x")), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outputSchemaVersion");
        assertThatThrownBy(() -> new AiProviderRequest("prompt-v1", "schema-v1", List.of(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("messages");
    }

    @Test
    void responseNormalizesOptionalMetadataAndUsage() {
        AiProviderResponse response = new AiProviderResponse(
                AiProviderType.MOCK,
                " mock-model ",
                "{\"items\":[]}",
                null,
                " request-1 ");

        assertThat(response.provider()).isEqualTo(AiProviderType.MOCK);
        assertThat(response.model()).isEqualTo("mock-model");
        assertThat(response.tokenUsage()).isEqualTo(AiProviderTokenUsage.empty());
        assertThat(response.requestId()).isEqualTo("request-1");
    }

    @Test
    void providerExceptionUsesControlledBusinessError() {
        AiProviderException exception = new AiProviderException(
                AiProviderType.DEEPSEEK,
                AiProviderErrorType.TIMEOUT,
                "AI provider timed out");

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR);
        assertThat(exception.getProviderType()).isEqualTo(AiProviderType.DEEPSEEK);
        assertThat(exception.getProviderErrorType()).isEqualTo(AiProviderErrorType.TIMEOUT);
        assertThat(exception.getMessage()).isEqualTo("AI provider timed out");
    }
}
