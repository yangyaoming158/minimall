package com.minimall.inventory.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minimall.inventory.ai.validation.AiOutputProductFacts;
import com.minimall.inventory.config.AiProviderProperties;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class MockAiProvider implements AiProvider {

    private static final String DEFAULT_MODEL = "mock-ai-provider";
    private static final long MINIMUM_SUGGESTED_QUANTITY = 1L;

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
        String analysisType = stringInput(request, "analysisType", "REPLENISHMENT");
        payload.put("summary", "Mock AI provider response");
        payload.put("analysisType", analysisType);
        payload.put("timeRange", mockTimeRange(request));
        payload.put("provider", "MOCK");
        payload.put("promptVersion", request.promptVersion());
        payload.put("outputSchemaVersion", request.outputSchemaVersion());
        payload.put("items", mockItems(request));
        payload.put("limitations",
                List.of("Mock provider derives deterministic items from input snapshot facts only."));
        return payload;
    }

    /**
     * Derives items deterministically from the request's validator product facts.
     * Every cited value equals the input snapshot, so mock output passes the
     * anti-hallucination validation and the MOCK path can demo analysis and
     * replenishment suggestion generation end-to-end without network access.
     */
    private List<Map<String, Object>> mockItems(AiProviderRequest request) {
        if (!(request.input().get("productFacts") instanceof List<?> factList)) {
            return List.of();
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (Object candidate : factList) {
            if (candidate instanceof AiOutputProductFacts facts) {
                items.add(mockItem(facts));
            }
        }
        return items;
    }

    private Map<String, Object> mockItem(AiOutputProductFacts facts) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("productId", facts.productId());
        putIfPresent(item, "productName", facts.productName());
        putIfPresent(item, "availableStock", facts.availableStock());
        putIfPresent(item, "lockedStock", facts.lockedStock());
        putIfPresent(item, "safetyStock", facts.safetyStock());
        putIfPresent(item, "soldQuantityLast7Days", facts.soldQuantityLast7Days());
        item.put("riskLevel", riskLevel(facts));
        item.put("suggestedQuantity", suggestedQuantity(facts));
        item.put("reason", "Deterministic mock suggestion derived from input snapshot facts.");
        return item;
    }

    private void putIfPresent(Map<String, Object> item, String field, Object value) {
        if (value != null) {
            item.put(field, value);
        }
    }

    private String riskLevel(AiOutputProductFacts facts) {
        if (facts.availableStock() == null || facts.safetyStock() == null) {
            return "MEDIUM";
        }
        return facts.availableStock() <= facts.safetyStock() ? "HIGH" : "LOW";
    }

    private long suggestedQuantity(AiOutputProductFacts facts) {
        if (facts.availableStock() != null && facts.safetyStock() != null) {
            long replenishToDoubleSafety = facts.safetyStock() * 2L - facts.availableStock();
            return Math.max(replenishToDoubleSafety, MINIMUM_SUGGESTED_QUANTITY);
        }
        if (facts.soldQuantityLast7Days() != null) {
            return Math.max(facts.soldQuantityLast7Days(), MINIMUM_SUGGESTED_QUANTITY);
        }
        return MINIMUM_SUGGESTED_QUANTITY;
    }

    private Map<String, String> mockTimeRange(AiProviderRequest request) {
        Object allowedDateValues = request.input().get("allowedDateValues");
        if (allowedDateValues instanceof List<?> values && !values.isEmpty()) {
            String from = Objects.toString(values.get(0), null);
            String to = Objects.toString(values.get(values.size() - 1), null);
            if (from != null && to != null) {
                return Map.of("from", from, "to", to);
            }
        }
        return Map.of();
    }

    private String stringInput(AiProviderRequest request, String key, String fallback) {
        Object value = request.input().get(key);
        if (value == null || value.toString().isBlank()) {
            return fallback;
        }
        return value.toString().trim();
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
