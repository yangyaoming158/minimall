package com.minimall.inventory.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.minimall.inventory.config.AiProviderProperties;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class DeepSeekAiProviderTest {

    private final RestClient.Builder restClientBuilder = RestClient.builder()
            .baseUrl("https://api.deepseek.example");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
    private final AiProviderProperties properties = properties();
    private final DeepSeekAiProvider provider =
            new DeepSeekAiProvider(restClientBuilder.build(), properties);

    @Test
    void postsOpenAiCompatibleChatCompletionRequestAndParsesResponse() {
        server.expect(once(), requestTo("https://api.deepseek.example/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer deepseek-test-key"))
                .andExpect(content().json("""
                        {
                          "model":"deepseek-v4-pro",
                          "messages":[
                            {"role":"system","content":"Return strict JSON only."},
                            {"role":"user","content":"Analyze SKU-AI-1."}
                          ],
                          "stream":false,
                          "temperature":0.0,
                          "response_format":{"type":"json_object"}
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "id":"ds-request-1",
                          "model":"deepseek-v4-pro",
                          "choices":[{"index":0,"message":{"role":"assistant","content":"{\\"items\\":[]}"}}],
                          "usage":{"prompt_tokens":12,"completion_tokens":4,"total_tokens":16}
                        }
                        """, MediaType.APPLICATION_JSON));

        AiProviderResponse response = provider.generate(request());

        assertThat(response.provider()).isEqualTo(AiProviderType.DEEPSEEK);
        assertThat(response.model()).isEqualTo("deepseek-v4-pro");
        assertThat(response.content()).isEqualTo("{\"items\":[]}");
        assertThat(response.requestId()).isEqualTo("ds-request-1");
        assertThat(response.tokenUsage()).isEqualTo(new AiProviderTokenUsage(12, 4, 16));
        server.verify();
    }

    @Test
    void mapsHttpProviderFailureToControlledException() {
        server.expect(once(), requestTo("https://api.deepseek.example/chat/completions"))
                .andRespond(withServerError().body("provider stack trace"));

        assertThatThrownBy(() -> provider.generate(request()))
                .isInstanceOfSatisfying(AiProviderException.class, exception -> {
                    assertThat(exception.getProviderType()).isEqualTo(AiProviderType.DEEPSEEK);
                    assertThat(exception.getProviderErrorType()).isEqualTo(AiProviderErrorType.PROVIDER_ERROR);
                    assertThat(exception.getMessage()).isEqualTo("AI provider returned an error");
                });
        server.verify();
    }

    @Test
    void mapsTimeoutToControlledException() {
        server.expect(once(), requestTo("https://api.deepseek.example/chat/completions"))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> provider.generate(request()))
                .isInstanceOfSatisfying(AiProviderException.class, exception -> {
                    assertThat(exception.getProviderType()).isEqualTo(AiProviderType.DEEPSEEK);
                    assertThat(exception.getProviderErrorType()).isEqualTo(AiProviderErrorType.TIMEOUT);
                    assertThat(exception.getMessage()).isEqualTo("AI provider request timed out");
                });
        server.verify();
    }

    @Test
    void mapsMalformedProviderResponseToControlledException() {
        server.expect(once(), requestTo("https://api.deepseek.example/chat/completions"))
                .andRespond(withSuccess("""
                        {"id":"bad","model":"deepseek-v4-pro","choices":[]}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.generate(request()))
                .isInstanceOfSatisfying(AiProviderException.class, exception -> {
                    assertThat(exception.getProviderType()).isEqualTo(AiProviderType.DEEPSEEK);
                    assertThat(exception.getProviderErrorType()).isEqualTo(AiProviderErrorType.INVALID_RESPONSE);
                    assertThat(exception.getMessage()).isEqualTo("AI provider response was missing choices");
                });
        server.verify();
    }

    @Test
    void rejectsIncompleteExternalProviderConfigurationBeforeNetworkCall() {
        AiProviderProperties incomplete = properties();
        incomplete.setApiKey(" ");
        DeepSeekAiProvider incompleteProvider =
                new DeepSeekAiProvider(restClientBuilder.build(), incomplete);

        assertThatThrownBy(() -> incompleteProvider.generate(request()))
                .isInstanceOfSatisfying(AiProviderException.class, exception -> {
                    assertThat(exception.getProviderType()).isEqualTo(AiProviderType.DEEPSEEK);
                    assertThat(exception.getProviderErrorType()).isEqualTo(AiProviderErrorType.CONFIGURATION_ERROR);
                    assertThat(exception.getMessage()).isEqualTo("AI provider configuration is incomplete");
                });
    }

    private AiProviderRequest request() {
        return new AiProviderRequest(
                "replenishment-v1",
                "inventory-suggestion-v1",
                List.of(
                        AiProviderMessage.system("Return strict JSON only."),
                        AiProviderMessage.user("Analyze SKU-AI-1.")),
                Map.of("productId", "SKU-AI-1"));
    }

    private AiProviderProperties properties() {
        AiProviderProperties properties = new AiProviderProperties();
        properties.setProvider(AiProviderType.DEEPSEEK);
        properties.setModel("deepseek-v4-pro");
        properties.setBaseUrl("https://api.deepseek.example");
        properties.setApiKey("deepseek-test-key");
        properties.setModelStrictJson(true);
        return properties;
    }
}
