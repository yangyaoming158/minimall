package com.minimall.inventory.ai;

import com.minimall.inventory.config.AiProviderProperties;
import org.springframework.web.client.RestClient;

public class DeepSeekAiProvider extends OpenAiCompatibleAiProvider {

    public DeepSeekAiProvider(RestClient restClient, AiProviderProperties properties) {
        super(AiProviderType.DEEPSEEK, "/chat/completions", restClient, properties);
    }
}
