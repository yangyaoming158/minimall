package com.minimall.inventory.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.minimall.inventory.config.AiProviderProperties;
import java.net.SocketTimeoutException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

abstract class OpenAiCompatibleAiProvider implements AiProvider {

    private final AiProviderType providerType;
    private final String chatCompletionPath;
    private final RestClient restClient;
    private final AiProviderProperties properties;

    OpenAiCompatibleAiProvider(
            AiProviderType providerType,
            String chatCompletionPath,
            RestClient restClient,
            AiProviderProperties properties) {
        this.providerType = Objects.requireNonNull(providerType, "providerType must not be null");
        this.chatCompletionPath = requireText(chatCompletionPath, "chatCompletionPath");
        this.restClient = Objects.requireNonNull(restClient, "restClient must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @Override
    public AiProviderType providerType() {
        return providerType;
    }

    @Override
    public AiProviderResponse generate(AiProviderRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        validateConfiguration();
        try {
            JsonNode response = restClient.post()
                    .uri(chatCompletionPath)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                    .body(requestBody(request))
                    .retrieve()
                    .body(JsonNode.class);
            return parseResponse(response);
        } catch (AiProviderException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw providerHttpException(exception);
        } catch (ResourceAccessException exception) {
            throw new AiProviderException(
                    providerType,
                    timeoutLike(exception) ? AiProviderErrorType.TIMEOUT : AiProviderErrorType.REQUEST_FAILED,
                    timeoutLike(exception) ? "AI provider request timed out" : "AI provider request failed",
                    exception);
        } catch (RestClientException exception) {
            throw new AiProviderException(
                    providerType,
                    AiProviderErrorType.REQUEST_FAILED,
                    "AI provider request failed",
                    exception);
        } catch (IllegalArgumentException exception) {
            throw new AiProviderException(
                    providerType,
                    AiProviderErrorType.INVALID_RESPONSE,
                    "AI provider response was invalid",
                    exception);
        }
    }

    protected Map<String, Object> requestBody(AiProviderRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("messages", messages(request.messages()));
        body.put("stream", false);
        body.put("temperature", properties.getTemperature());
        if (properties.isModelStrictJson()) {
            body.put("response_format", Map.of("type", "json_object"));
        }
        return body;
    }

    protected void validateProviderPayload(JsonNode response) {
    }

    private List<Map<String, String>> messages(List<AiProviderMessage> messages) {
        return messages.stream()
                .map(message -> {
                    Map<String, String> mapped = new LinkedHashMap<>();
                    mapped.put("role", message.role().name().toLowerCase(Locale.ROOT));
                    mapped.put("content", message.content());
                    return mapped;
                })
                .toList();
    }

    private AiProviderResponse parseResponse(JsonNode response) {
        if (response == null || response.isNull()) {
            throw invalidResponse("AI provider returned an empty response");
        }
        validateProviderPayload(response);
        JsonNode choices = response.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw invalidResponse("AI provider response was missing choices");
        }
        String content = textOrNull(choices.get(0).path("message").path("content"));
        if (content == null) {
            throw invalidResponse("AI provider response was missing content");
        }
        JsonNode usage = response.path("usage");
        return new AiProviderResponse(
                providerType,
                firstText(response.path("model"), properties.getModel()),
                content,
                new AiProviderTokenUsage(
                        nonNegativeInt(usage.path("prompt_tokens")),
                        nonNegativeInt(usage.path("completion_tokens")),
                        nonNegativeInt(usage.path("total_tokens"))),
                textOrNull(response.path("id")));
    }

    private AiProviderException providerHttpException(RestClientResponseException exception) {
        return new AiProviderException(
                providerType,
                AiProviderErrorType.PROVIDER_ERROR,
                "AI provider returned an error",
                exception);
    }

    private void validateConfiguration() {
        if (properties.getModel() == null || !properties.hasBaseUrl() || !properties.hasApiKey()) {
            throw new AiProviderException(
                    providerType,
                    AiProviderErrorType.CONFIGURATION_ERROR,
                    "AI provider configuration is incomplete");
        }
    }

    private AiProviderException invalidResponse(String message) {
        return new AiProviderException(providerType, AiProviderErrorType.INVALID_RESPONSE, message);
    }

    private boolean timeoutLike(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains("timed out")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String firstText(JsonNode primary, String fallback) {
        String value = textOrNull(primary);
        return value == null ? fallback : value;
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText();
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private int nonNegativeInt(JsonNode node) {
        if (node == null || !node.canConvertToInt()) {
            return 0;
        }
        return Math.max(0, node.asInt());
    }

    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
