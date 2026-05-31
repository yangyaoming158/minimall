package com.minimall.order.client;

import com.minimall.common.auth.constants.AuthHeaders;
import java.io.IOException;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Stamps outbound service-to-service calls with the shared internal secret so
 * the callee's {@code InternalAuthFilter} accepts them. {@code /internal/**}
 * endpoints are not reachable through the gateway, so this header is the only
 * way a trusted internal client authenticates itself. No-op when the secret is
 * unconfigured (legacy/local behaviour).
 */
public class InternalTokenInterceptor implements ClientHttpRequestInterceptor {

    private final String internalSecret;

    public InternalTokenInterceptor(String internalSecret) {
        this.internalSecret = internalSecret == null || internalSecret.isBlank() ? null : internalSecret;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        if (internalSecret != null) {
            request.getHeaders().set(AuthHeaders.GATEWAY_TOKEN, internalSecret);
        }
        return execution.execute(request, body);
    }
}
