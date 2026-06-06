package com.minimall.inventory.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.minimall.inventory.config.AiProviderProperties;
import org.springframework.web.client.RestClient;

public class MiniMaxAiProvider extends OpenAiCompatibleAiProvider {

    public MiniMaxAiProvider(RestClient restClient, AiProviderProperties properties) {
        super(AiProviderType.MINIMAX, "/v1/chat/completions", restClient, properties);
    }

    @Override
    protected void validateProviderPayload(JsonNode response) {
        JsonNode baseResp = response.path("base_resp");
        if (!baseResp.isMissingNode() && baseResp.path("status_code").asInt(0) != 0) {
            throw new AiProviderException(
                    AiProviderType.MINIMAX,
                    AiProviderErrorType.PROVIDER_ERROR,
                    "AI provider returned an error");
        }
    }
}
