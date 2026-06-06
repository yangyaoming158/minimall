package com.minimall.inventory.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.minimall.inventory.ai.AiProviderType;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class AiProviderPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void defaultsToMockWithoutSecretsOrExternalHost() {
        contextRunner.run(context -> {
            AiProviderProperties properties = context.getBean(AiProviderProperties.class);

            assertThat(properties.getProvider()).isEqualTo(AiProviderType.MOCK);
            assertThat(properties.getModel()).isNull();
            assertThat(properties.getBaseUrl()).isNull();
            assertThat(properties.getApiKey()).isNull();
            assertThat(properties.getRequestTimeoutMs()).isEqualTo(5_000L);
            assertThat(properties.requestTimeout()).isEqualTo(Duration.ofMillis(5_000L));
            assertThat(properties.isModelStrictJson()).isTrue();
            assertThat(properties.isMockEnabled()).isTrue();
            assertThat(properties.hasBaseUrl()).isFalse();
            assertThat(properties.hasApiKey()).isFalse();
        });
    }

    @Test
    void bindsEnvironmentDrivenValues() {
        contextRunner
                .withPropertyValues(
                        "minimall.ai.provider=DEEPSEEK",
                        "minimall.ai.model=deepseek-chat",
                        "minimall.ai.base-url=https://api.deepseek.example",
                        "minimall.ai.api-key=test-api-key",
                        "minimall.ai.request-timeout-ms=12000",
                        "minimall.ai.model-strict-json=false",
                        "minimall.ai.mock-enabled=false")
                .run(context -> {
                    AiProviderProperties properties = context.getBean(AiProviderProperties.class);

                    assertThat(properties.getProvider()).isEqualTo(AiProviderType.DEEPSEEK);
                    assertThat(properties.getModel()).isEqualTo("deepseek-chat");
                    assertThat(properties.getBaseUrl()).isEqualTo("https://api.deepseek.example");
                    assertThat(properties.getApiKey()).isEqualTo("test-api-key");
                    assertThat(properties.getRequestTimeoutMs()).isEqualTo(12_000L);
                    assertThat(properties.requestTimeout()).isEqualTo(Duration.ofMillis(12_000L));
                    assertThat(properties.isModelStrictJson()).isFalse();
                    assertThat(properties.isMockEnabled()).isFalse();
                    assertThat(properties.hasBaseUrl()).isTrue();
                    assertThat(properties.hasApiKey()).isTrue();
                });
    }

    @Test
    void normalizesBlankValuesAndClampsTimeout() {
        contextRunner
                .withPropertyValues(
                        "minimall.ai.model= ",
                        "minimall.ai.base-url= ",
                        "minimall.ai.api-key= ",
                        "minimall.ai.request-timeout-ms=0")
                .run(context -> {
                    AiProviderProperties properties = context.getBean(AiProviderProperties.class);

                    assertThat(properties.getModel()).isNull();
                    assertThat(properties.getBaseUrl()).isNull();
                    assertThat(properties.getApiKey()).isNull();
                    assertThat(properties.getRequestTimeoutMs()).isEqualTo(1L);
                    assertThat(properties.requestTimeout()).isEqualTo(Duration.ofMillis(1L));
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AiProviderProperties.class)
    static class TestConfiguration {
    }
}
