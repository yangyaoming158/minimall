package com.minimall.inventory.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.common.core.response.ApiResponse;
import com.minimall.inventory.ai.AiProvider;
import com.minimall.inventory.ai.AiProviderErrorType;
import com.minimall.inventory.ai.AiProviderException;
import com.minimall.inventory.ai.AiProviderMessage;
import com.minimall.inventory.ai.AiProviderRequest;
import com.minimall.inventory.ai.AiProviderRestClientFactory;
import com.minimall.inventory.ai.AiProviderType;
import com.minimall.inventory.ai.DeepSeekAiProvider;
import com.minimall.inventory.ai.MiniMaxAiProvider;
import com.minimall.inventory.ai.MockAiProvider;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

class AiProviderConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestPropertiesConfiguration.class, AiProviderConfiguration.class)
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void defaultsToMockProviderBean() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(AiProviderProperties.class);
            assertThat(context).hasSingleBean(AiProviderRestClientFactory.class);
            assertThat(context).hasSingleBean(AiProvider.class);
            assertThat(context.getBean(AiProvider.class)).isInstanceOf(MockAiProvider.class);
            assertThat(context.getBean(AiProvider.class).providerType()).isEqualTo(AiProviderType.MOCK);
        });
    }

    @Test
    void selectsDeepSeekProviderFromConfiguration() {
        contextRunner
                .withPropertyValues(
                        "minimall.ai.provider=DEEPSEEK",
                        "minimall.ai.model=deepseek-v4-pro",
                        "minimall.ai.base-url=https://api.deepseek.example",
                        "minimall.ai.api-key=deepseek-test-key")
                .run(context -> {
                    assertThat(context).hasSingleBean(AiProvider.class);
                    assertThat(context.getBean(AiProvider.class)).isInstanceOf(DeepSeekAiProvider.class);
                    assertThat(context.getBean(AiProvider.class).providerType()).isEqualTo(AiProviderType.DEEPSEEK);
                });
    }

    @Test
    void selectsMiniMaxProviderFromConfiguration() {
        contextRunner
                .withPropertyValues(
                        "minimall.ai.provider=MINIMAX",
                        "minimall.ai.model=MiniMax-M2.7",
                        "minimall.ai.base-url=https://api.minimax.example",
                        "minimall.ai.api-key=minimax-test-key")
                .run(context -> {
                    assertThat(context).hasSingleBean(AiProvider.class);
                    assertThat(context.getBean(AiProvider.class)).isInstanceOf(MiniMaxAiProvider.class);
                    assertThat(context.getBean(AiProvider.class).providerType()).isEqualTo(AiProviderType.MINIMAX);
                });
    }

    @Test
    void mockDisabledFailsWithStableApiResponseContract() {
        contextRunner
                .withPropertyValues(
                        "minimall.ai.provider=MOCK",
                        "minimall.ai.mock-enabled=false")
                .run(context -> assertThatThrownBy(() -> context.getBean(AiProvider.class).generate(request()))
                        .isInstanceOfSatisfying(AiProviderException.class, exception -> {
                            assertThat(exception.getProviderType()).isEqualTo(AiProviderType.MOCK);
                            assertThat(exception.getProviderErrorType())
                                    .isEqualTo(AiProviderErrorType.CONFIGURATION_ERROR);
                            assertApiResponseFailure(exception, "AI mock provider is disabled");
                        }));
    }

    @Test
    void externalProviderMissingApiKeyFailsWithControlledErrorBeforeNetworkCall() {
        contextRunner
                .withPropertyValues(
                        "minimall.ai.provider=DEEPSEEK",
                        "minimall.ai.model=deepseek-v4-pro",
                        "minimall.ai.base-url=https://api.deepseek.example",
                        "minimall.ai.api-key=")
                .run(context -> assertThatThrownBy(() -> context.getBean(AiProvider.class).generate(request()))
                        .isInstanceOfSatisfying(AiProviderException.class, exception -> {
                            assertThat(exception.getProviderType()).isEqualTo(AiProviderType.DEEPSEEK);
                            assertThat(exception.getProviderErrorType())
                                    .isEqualTo(AiProviderErrorType.CONFIGURATION_ERROR);
                            assertApiResponseFailure(exception, "AI provider configuration is incomplete");
                        }));
    }

    @Test
    void externalProviderMissingBaseUrlStillFailsWithControlledError() {
        contextRunner
                .withPropertyValues(
                        "minimall.ai.provider=MINIMAX",
                        "minimall.ai.model=MiniMax-M2.7",
                        "minimall.ai.base-url=",
                        "minimall.ai.api-key=minimax-test-key")
                .run(context -> assertThatThrownBy(() -> context.getBean(AiProvider.class).generate(request()))
                        .isInstanceOfSatisfying(AiProviderException.class, exception -> {
                            assertThat(exception.getProviderType()).isEqualTo(AiProviderType.MINIMAX);
                            assertThat(exception.getProviderErrorType())
                                    .isEqualTo(AiProviderErrorType.CONFIGURATION_ERROR);
                            assertApiResponseFailure(exception, "AI provider configuration is incomplete");
                        }));
    }

    @Test
    void passesTimeoutConfigurationToExternalProviderRestClientFactory() {
        CapturingAiProviderRestClientFactory restClientFactory = new CapturingAiProviderRestClientFactory();

        contextRunner
                .withBean(AiProviderRestClientFactory.class, () -> restClientFactory)
                .withPropertyValues(
                        "minimall.ai.provider=DEEPSEEK",
                        "minimall.ai.model=deepseek-v4-pro",
                        "minimall.ai.base-url=https://api.deepseek.example",
                        "minimall.ai.api-key=deepseek-test-key",
                        "minimall.ai.request-timeout-ms=9876")
                .run(context -> {
                    assertThat(context.getBean(AiProvider.class)).isInstanceOf(DeepSeekAiProvider.class);
                    assertThat(restClientFactory.capturedTimeout).isEqualTo(Duration.ofMillis(9_876));
                });
    }

    @Test
    void backsOffWhenCustomProviderBeanExists() {
        AiProvider customProvider = new MockAiProvider(new AiProviderProperties(), new ObjectMapper());

        contextRunner
                .withBean(AiProvider.class, () -> customProvider)
                .run(context -> {
                    assertThat(context).hasSingleBean(AiProvider.class);
                    assertThat(context.getBean(AiProvider.class)).isSameAs(customProvider);
                });
    }

    private void assertApiResponseFailure(AiProviderException exception, String message) {
        ApiResponse<Void> response = ApiResponse.failure(exception.getErrorCode(), exception.getMessage());
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR);
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo(ErrorCode.INTERNAL_ERROR.getCode());
        assertThat(response.getMessage()).isEqualTo(message);
    }

    private AiProviderRequest request() {
        return new AiProviderRequest(
                "replenishment-v1",
                "inventory-suggestion-v1",
                List.of(AiProviderMessage.user("Analyze SKU-AI-1.")),
                Map.of("productId", "SKU-AI-1"));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AiProviderProperties.class)
    static class TestPropertiesConfiguration {
    }

    private static final class CapturingAiProviderRestClientFactory extends AiProviderRestClientFactory {

        private Duration capturedTimeout;

        @Override
        public RestClient create(AiProviderProperties properties) {
            capturedTimeout = properties.requestTimeout();
            return super.create(properties);
        }
    }
}
