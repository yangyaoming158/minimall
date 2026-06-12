package com.minimall.inventory.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.minimall.inventory.config.AiProviderProperties;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class MiniMaxAiProviderTest {

    private final RestClient.Builder restClientBuilder = RestClient.builder()
            .baseUrl("https://api.minimax.example");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
    private final MiniMaxAiProvider provider =
            new MiniMaxAiProvider(restClientBuilder.build(), properties());

    @Test
    void postsOpenAiCompatibleChatCompletionRequestAndParsesResponse() {
        server.expect(once(), requestTo("https://api.minimax.example/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer minimax-test-key"))
                .andExpect(content().json("""
                        {
                          "model":"MiniMax-M2.7",
                          "messages":[
                            {"role":"system","content":"Return strict JSON only."},
                            {"role":"user","content":"Analyze SKU-AI-2."}
                          ],
                          "stream":false,
                          "temperature":0.0,
                          "response_format":{"type":"json_object"}
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "id":"mm-request-1",
                          "model":"MiniMax-M2.7",
                          "object":"chat.completion",
                          "choices":[{"index":0,"message":{"role":"assistant","content":"{\\"items\\":[]}"}}],
                          "usage":{"prompt_tokens":8,"completion_tokens":3,"total_tokens":11},
                          "base_resp":{"status_code":0,"status_msg":""}
                        }
                        """, MediaType.APPLICATION_JSON));

        AiProviderResponse response = provider.generate(request());

        assertThat(response.provider()).isEqualTo(AiProviderType.MINIMAX);
        assertThat(response.model()).isEqualTo("MiniMax-M2.7");
        assertThat(response.content()).isEqualTo("{\"items\":[]}");
        assertThat(response.requestId()).isEqualTo("mm-request-1");
        assertThat(response.tokenUsage()).isEqualTo(new AiProviderTokenUsage(8, 3, 11));
        server.verify();
    }

    @Test
    void mapsMiniMaxBaseResponseFailureToControlledException() {
        server.expect(once(), requestTo("https://api.minimax.example/v1/chat/completions"))
                .andRespond(withSuccess("""
                        {
                          "id":"mm-error",
                          "model":"MiniMax-M2.7",
                          "choices":[{"index":0,"message":{"role":"assistant","content":""}}],
                          "base_resp":{"status_code":1001,"status_msg":"provider detail"}
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.generate(request()))
                .isInstanceOfSatisfying(AiProviderException.class, exception -> {
                    assertThat(exception.getProviderType()).isEqualTo(AiProviderType.MINIMAX);
                    assertThat(exception.getProviderErrorType()).isEqualTo(AiProviderErrorType.PROVIDER_ERROR);
                    assertThat(exception.getMessage()).isEqualTo("AI provider returned an error");
                });
        server.verify();
    }

    private AiProviderRequest request() {
        return new AiProviderRequest(
                "replenishment-v1",
                "inventory-suggestion-v1",
                List.of(
                        AiProviderMessage.system("Return strict JSON only."),
                        AiProviderMessage.user("Analyze SKU-AI-2.")),
                Map.of("productId", "SKU-AI-2"));
    }

    private AiProviderProperties properties() {
        AiProviderProperties properties = new AiProviderProperties();
        properties.setProvider(AiProviderType.MINIMAX);
        properties.setModel("MiniMax-M2.7");
        properties.setBaseUrl("https://api.minimax.example");
        properties.setApiKey("minimax-test-key");
        properties.setModelStrictJson(true);
        return properties;
    }
}
