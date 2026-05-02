package com.minimall.common.auth.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minimall.common.auth.config.JwtProperties;
import com.minimall.common.auth.constants.AuthHeaders;
import com.minimall.common.auth.context.UserContext;
import com.minimall.common.auth.context.UserContextHolder;
import com.minimall.common.auth.jwt.JwtUtils;
import com.minimall.common.core.exception.ErrorCode;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class UserContextFilterTest {

    private static final String SECRET = "test-secret-with-enough-entropy-for-hmac";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void readsUserContextFromPropagationHeadersAndClearsAfterRequest() throws Exception {
        UserContextFilter filter = new UserContextFilter(null);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean contextWasAvailableInChain = new AtomicBoolean(false);
        request.addHeader(AuthHeaders.USER_ID, "1001");
        request.addHeader(AuthHeaders.USERNAME, "alice");

        filter.doFilter(request, response, chainAssertingContext(1001L, "alice", contextWasAvailableInChain));

        assertThat(contextWasAvailableInChain).isTrue();
        assertThat(UserContextHolder.hasContext()).isFalse();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void prefersPropagationHeadersOverJwt() throws Exception {
        JwtUtils jwtUtils = new JwtUtils(properties());
        UserContextFilter filter = new UserContextFilter(jwtUtils);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean contextWasAvailableInChain = new AtomicBoolean(false);
        request.addHeader(AuthHeaders.USER_ID, "1001");
        request.addHeader(AuthHeaders.USERNAME, "alice");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + jwtUtils.generateToken(2002L, "bob"));

        filter.doFilter(request, response, chainAssertingContext(1001L, "alice", contextWasAvailableInChain));

        assertThat(contextWasAvailableInChain).isTrue();
        assertThat(UserContextHolder.hasContext()).isFalse();
    }

    @Test
    void readsUserContextFromJwtAndClearsAfterRequest() throws Exception {
        JwtUtils jwtUtils = new JwtUtils(properties());
        UserContextFilter filter = new UserContextFilter(jwtUtils);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean contextWasAvailableInChain = new AtomicBoolean(false);
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + jwtUtils.generateToken(1001L, "alice"));

        filter.doFilter(request, response, chainAssertingContext(1001L, "alice", contextWasAvailableInChain));

        assertThat(contextWasAvailableInChain).isTrue();
        assertThat(UserContextHolder.hasContext()).isFalse();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void returnsUnauthorizedApiResponseForInvalidJwt() throws Exception {
        UserContextFilter filter = new UserContextFilter(new JwtUtils(properties()));
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer invalid-token");

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(UserContextHolder.hasContext()).isFalse();
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("success").asBoolean()).isFalse();
        assertThat(body.get("code").asText()).isEqualTo(ErrorCode.UNAUTHORIZED.getCode());
        assertThat(body.get("message").asText()).isEqualTo("Invalid or expired token");
    }

    @Test
    void returnsUnauthorizedApiResponseForInvalidPropagationHeaders() throws Exception {
        UserContextFilter filter = new UserContextFilter(null);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(AuthHeaders.USER_ID, "not-a-number");
        request.addHeader(AuthHeaders.USERNAME, "alice");

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(UserContextHolder.hasContext()).isFalse();
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("code").asText()).isEqualTo(ErrorCode.UNAUTHORIZED.getCode());
        assertThat(body.get("message").asText()).isEqualTo("Invalid user propagation headers");
    }

    @Test
    void proceedsWithoutUserContextWhenNoAuthDataExists() throws Exception {
        UserContextFilter filter = new UserContextFilter(null);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainWasInvoked = new AtomicBoolean(false);

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            chainWasInvoked.set(true);
            assertThat(UserContextHolder.hasContext()).isFalse();
        });

        assertThat(chainWasInvoked).isTrue();
        assertThat(UserContextHolder.hasContext()).isFalse();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    private static MockFilterChain chainAssertingContext(Long userId, String username, AtomicBoolean assertionReached) {
        return new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response)
                    throws IOException, ServletException {
                UserContext userContext = UserContextHolder.require();
                assertThat(userContext.getUserId()).isEqualTo(userId);
                assertThat(userContext.getUsername()).isEqualTo(username);
                assertionReached.set(true);
                super.doFilter(request, response);
            }
        };
    }

    private static JwtProperties properties() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(SECRET);
        properties.setExpireSeconds(3600);
        return properties;
    }
}
