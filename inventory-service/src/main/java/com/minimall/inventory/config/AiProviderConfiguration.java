package com.minimall.inventory.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minimall.inventory.ai.AiProvider;
import com.minimall.inventory.ai.AiProviderRestClientFactory;
import com.minimall.inventory.ai.DeepSeekAiProvider;
import com.minimall.inventory.ai.MiniMaxAiProvider;
import com.minimall.inventory.ai.MockAiProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class AiProviderConfiguration {

    @Bean
    @ConditionalOnMissingBean
    AiProviderRestClientFactory aiProviderRestClientFactory() {
        return new AiProviderRestClientFactory();
    }

    @Bean
    @ConditionalOnMissingBean(AiProvider.class)
    AiProvider aiProvider(
            AiProviderProperties properties,
            ObjectMapper objectMapper,
            AiProviderRestClientFactory restClientFactory) {
        return switch (properties.getProvider()) {
            case MOCK -> new MockAiProvider(properties, objectMapper);
            case DEEPSEEK -> new DeepSeekAiProvider(restClient(restClientFactory, properties), properties);
            case MINIMAX -> new MiniMaxAiProvider(restClient(restClientFactory, properties), properties);
        };
    }

    private RestClient restClient(AiProviderRestClientFactory restClientFactory, AiProviderProperties properties) {
        return restClientFactory.create(properties);
    }
}
