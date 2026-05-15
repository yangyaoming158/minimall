package com.minimall.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.minimall.common.auth.constants.AuthHeaders;
import com.minimall.common.auth.jwt.JwtUtils;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.gateway.ratelimit.GatewayRateLimitResult;
import com.minimall.gateway.ratelimit.GatewayRateLimiter;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@SpringBootTest
@AutoConfigureWebTestClient
@TestPropertySource(properties = {
        "USER_SERVICE_BASE_URL=forward:/__gateway-auth-test",
        "PRODUCT_SERVICE_BASE_URL=forward:/__gateway-auth-test",
        "INVENTORY_SERVICE_BASE_URL=forward:/__gateway-auth-test",
        "ORDER_SERVICE_BASE_URL=forward:/__gateway-auth-test",
        "PAYMENT_SERVICE_BASE_URL=forward:/__gateway-auth-test",
        "minimall.auth.jwt.secret=test-gateway-auth-jwt-secret",
        "minimall.auth.jwt.expire-seconds=3600"
})
class GatewayAuthenticationFilterTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private JwtUtils jwtUtils;

    @Test
    void missingJwtReturnsUnauthorizedApiResponse() {
        webTestClient.get()
                .uri("/api/order/orders/my")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith("application/json")
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.code").isEqualTo(ErrorCode.UNAUTHORIZED.getCode())
                .jsonPath("$.message").isEqualTo("Missing token");
    }

    @Test
    void invalidJwtReturnsUnauthorizedApiResponse() {
        webTestClient.get()
                .uri("/api/order/orders/my")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith("application/json")
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.code").isEqualTo(ErrorCode.UNAUTHORIZED.getCode())
                .jsonPath("$.message").isEqualTo("Invalid or expired token");
    }

    @Test
    void validJwtInjectsTrustedUserHeadersAndStripsSpoofedHeaders() {
        String token = jwtUtils.generateToken(42L, "alice");

        webTestClient.get()
                .uri("/api/order/orders/my")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(AuthHeaders.USER_ID, "999")
                .header(AuthHeaders.USERNAME, "mallory")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.userId").isEqualTo("42")
                .jsonPath("$.username").isEqualTo("alice");
    }

    @Test
    void publicLoginPathBypassesJwtAndStripsSpoofedHeaders() {
        webTestClient.post()
                .uri("/api/user/users/login")
                .header(AuthHeaders.USER_ID, "999")
                .header(AuthHeaders.USERNAME, "mallory")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.userId").isEqualTo("")
                .jsonPath("$.username").isEqualTo("");
    }

    @Test
    void optionsRequestBypassesJwt() {
        webTestClient.options()
                .uri("/api/order/orders/my")
                .header(AuthHeaders.USER_ID, "999")
                .header(AuthHeaders.USERNAME, "mallory")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void apiPreflightRequestReturnsCorsHeadersWithoutJwt() {
        webTestClient.options()
                .uri("/api/order/orders/my")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization, Content-Type")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173")
                .expectHeader().value(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                        value -> assertThat(value).contains("GET"))
                .expectHeader().value(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        value -> assertThat(value).contains("Authorization"))
                .expectHeader().value(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        value -> assertThat(value).contains("Content-Type"));
    }

    @TestConfiguration
    static class TestDownstreamConfiguration {

        @Bean
        TestDownstreamController testDownstreamController() {
            return new TestDownstreamController();
        }

        @Bean
        @Primary
        GatewayRateLimiter allowAllGatewayRateLimiter() {
            return key -> Mono.just(GatewayRateLimitResult.allowed(99));
        }
    }

    @RestController
    static class TestDownstreamController {

        @RequestMapping("/__gateway-auth-test")
        Map<String, String> headers(ServerHttpRequest request) {
            return Map.of(
                    "userId", headerValue(request, AuthHeaders.USER_ID),
                    "username", headerValue(request, AuthHeaders.USERNAME));
        }

        private String headerValue(ServerHttpRequest request, String headerName) {
            String value = request.getHeaders().getFirst(headerName);
            return value == null ? "" : value;
        }
    }

}
