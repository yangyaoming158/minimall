package com.minimall.inventory.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minimall.inventory.config.AiProviderProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class MockAiProvider implements AiProvider {

    private static final String DEFAULT_MODEL = "mock-ai-provider";

    private final AiProviderProperties properties;
    private final ObjectMapper objectMapper;

    public MockAiProvider(AiProviderProperties properties, ObjectMapper objectMapper) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public AiProviderType providerType() {
        return AiProviderType.MOCK;
    }

    @Override
    public AiProviderResponse generate(AiProviderRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (!properties.isMockEnabled()) {
            throw new AiProviderException(
                    AiProviderType.MOCK,
                    AiProviderErrorType.CONFIGURATION_ERROR,
                    "AI mock provider is disabled");
        }
        try {
            String content = objectMapper.writeValueAsString(mockPayload(request));
            return new AiProviderResponse(
                    AiProviderType.MOCK,
                    properties.getModel() == null ? DEFAULT_MODEL : properties.getModel(),
                    content,
                    usage(request, content),
                    requestId(request));
        } catch (JsonProcessingException exception) {
            throw new AiProviderException(
                    AiProviderType.MOCK,
                    AiProviderErrorType.INVALID_RESPONSE,
                    "AI mock provider response was invalid",
                    exception);
        }
    }

    private Map<String, Object> mockPayload(AiProviderRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("provider", "MOCK");
        payload.put("promptVersion", request.promptVersion());
        payload.put("outputSchemaVersion", request.outputSchemaVersion());
        payload.put("reason", "Mock AI provider response");
        payload.put("items", java.util.List.of());
        payload.put("input", request.input());
        return payload;
    }

    private AiProviderTokenUsage usage(AiProviderRequest request, String content) {
        int promptSize = request.messages().stream()
                .mapToInt(message -> message.content().length())
                .sum();
        int completionSize = content.length();
        return new AiProviderTokenUsage(promptSize, completionSize, promptSize + completionSize);
    }

    private String requestId(AiProviderRequest request) {
        return "mock-" + Integer.toUnsignedString(Objects.hash(
                request.promptVersion(),
                request.outputSchemaVersion(),
                request.messages(),
                request.input()), 16);
    }
}
