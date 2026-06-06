package com.minimall.inventory.config;

import com.minimall.inventory.ai.AiProviderType;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "minimall.ai")
public class AiProviderProperties {

    private AiProviderType provider = AiProviderType.MOCK;
    private String model;
    private String baseUrl;
    private String apiKey;
    private long requestTimeoutMs = 5_000L;
    private boolean modelStrictJson = true;
    private boolean mockEnabled = true;

    public AiProviderType getProvider() {
        return provider;
    }

    public void setProvider(AiProviderType provider) {
        if (provider != null) {
            this.provider = provider;
        }
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = trimToNull(model);
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = trimToNull(baseUrl);
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = trimToNull(apiKey);
    }

    public long getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

    public void setRequestTimeoutMs(long requestTimeoutMs) {
        this.requestTimeoutMs = Math.max(1L, requestTimeoutMs);
    }

    public boolean isModelStrictJson() {
        return modelStrictJson;
    }

    public void setModelStrictJson(boolean modelStrictJson) {
        this.modelStrictJson = modelStrictJson;
    }

    public boolean isMockEnabled() {
        return mockEnabled;
    }

    public void setMockEnabled(boolean mockEnabled) {
        this.mockEnabled = mockEnabled;
    }

    public Duration requestTimeout() {
        return Duration.ofMillis(requestTimeoutMs);
    }

    public boolean hasBaseUrl() {
        return baseUrl != null;
    }

    public boolean hasApiKey() {
        return apiKey != null;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
