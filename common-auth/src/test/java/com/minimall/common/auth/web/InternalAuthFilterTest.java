package com.minimall.common.auth.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minimall.common.auth.constants.AuthHeaders;
import com.minimall.common.core.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class InternalAuthFilterTest {

    private static final String SECRET = "gateway-shared-secret";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void rejectsInternalCallWithoutSecretWhenConfigured() throws Exception {
        InternalAuthFilter filter = new InternalAuthFilter(SECRET);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/inventories/deduct");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("success").asBoolean()).isFalse();
        assertThat(body.get("code").asText()).isEqualTo(ErrorCode.FORBIDDEN.getCode());
    }

    @Test
    void allowsInternalCallWithMatchingSecret() throws Exception {
        InternalAuthFilter filter = new InternalAuthFilter(SECRET);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/inventories/deduct");
        request.addHeader(AuthHeaders.GATEWAY_TOKEN, SECRET);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void doesNotGuardNonInternalPaths() throws Exception {
        InternalAuthFilter filter = new InternalAuthFilter(SECRET);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/products");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void isInertWhenSecretNotConfigured() throws Exception {
        InternalAuthFilter filter = new InternalAuthFilter("");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/inventories/deduct");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isSameAs(request);
    }
}
