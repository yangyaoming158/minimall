package com.minimall.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.minimall.common.auth.constants.AuthHeaders;
import com.minimall.common.auth.jwt.JwtUtils;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.gateway.ratelimit.GatewayRateLimitResult;
import com.minimall.gateway.ratelimit.GatewayRateLimiter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@SpringBootTest
@AutoConfigureWebTestClient
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = {
        "USER_SERVICE_BASE_URL=forward:/__gateway-regression/user",
        "PRODUCT_SERVICE_BASE_URL=forward:/__gateway-regression/product",
        "INVENTORY_SERVICE_BASE_URL=forward:/__gateway-regression/inventory",
        "ORDER_SERVICE_BASE_URL=forward:/__gateway-regression/order",
        "PAYMENT_SERVICE_BASE_URL=forward:/__gateway-regression/payment",
        "minimall.auth.jwt.secret=test-gateway-regression-jwt-secret",
        "minimall.auth.jwt.expire-seconds=3600",
        "minimall.gateway.rate-limit.enabled=true",
        "minimall.gateway.rate-limit.fail-open=false"
})
class GatewayIntegrationRegressionTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private TestGatewayRateLimiter rateLimiter;

    @BeforeEach
    void resetRateLimiter() {
        rateLimiter.reset();
    }

    @Test
    void routesFrontendServicePrefixesAndPropagatesTrustedUserHeaders() {
        String token = jwtUtils.generateToken(42L, "alice");

        expectPublicUserRoutes();
        expectPublicCatalogRead("/api/products", "product");
        expectPublicCatalogRead("/api/inventories/SKU-1", "inventory");
        expectProtectedRoute("/api/orders/my", "order", token);
        expectProtectedRoute("/api/payments/ORDER-1", "payment", token);

        assertThat(rateLimiter.keys()).containsExactly(
                "ip:unknown",
                "ip:unknown",
                "ip:unknown",
                "ip:unknown",
                "user:42",
                "user:42");
    }

    @Test
    void protectedApiRequiresJwtBeforeRateLimiting() {
        webTestClient.get()
                .uri("/api/orders/my")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith("application/json")
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.code").isEqualTo(ErrorCode.UNAUTHORIZED.getCode())
                .jsonPath("$.message").isEqualTo("Missing token");

        assertThat(rateLimiter.keys()).isEmpty();
    }

    @Test
    void corsPreflightBypassesAuthenticationAndRateLimiting() {
        rateLimiter.deny();

        webTestClient.options()
                .uri("/api/orders/my")
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

        assertThat(rateLimiter.keys()).isEmpty();
    }

    @Test
    void rateLimitDenialReturnsApiResponse429() {
        rateLimiter.deny();
        String token = jwtUtils.generateToken(42L, "alice");

        webTestClient.get()
                .uri("/api/payments/ORDER-1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isEqualTo(429)
                .expectHeader().contentTypeCompatibleWith("application/json")
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.code").isEqualTo(ErrorCode.TOO_MANY_REQUESTS.getCode())
                .jsonPath("$.message").isEqualTo(ErrorCode.TOO_MANY_REQUESTS.getMessage());

        assertThat(rateLimiter.keys()).containsExactly("user:42");
    }

    @Test
    void internalPathsReturnForbiddenApiResponseBeforeRoutingOrRateLimiting() {
        expectInternalPathForbidden("/internal/products/SKU-1");
        expectInternalPathForbidden("/internal/inventories/deduct");

        assertThat(rateLimiter.keys()).isEmpty();
    }

    @Test
    void requestLoggingRecordsBrowserFacingPath(CapturedOutput output) {
        String token = jwtUtils.generateToken(42L, "alice");

        webTestClient.get()
                .uri("/api/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk();

        assertThat(output).contains("gateway request method=GET path=/api/products status=200 durationMs=");
    }

    private void expectPublicUserRoutes() {
        webTestClient.post()
                .uri("/api/users/login")
                .header(AuthHeaders.USER_ID, "999")
                .header(AuthHeaders.USERNAME, "mallory")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.service").isEqualTo("user")
                .jsonPath("$.userId").isEqualTo("")
                .jsonPath("$.username").isEqualTo("");

        webTestClient.post()
                .uri("/api/users/register")
                .header(AuthHeaders.USER_ID, "999")
                .header(AuthHeaders.USERNAME, "mallory")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.service").isEqualTo("user")
                .jsonPath("$.userId").isEqualTo("")
                .jsonPath("$.username").isEqualTo("");
    }

    // Catalog reads are public (PRD 4.1): they route through without a token and
    // any spoofed trusted-user headers must be stripped, like the user routes.
    private void expectPublicCatalogRead(String uri, String expectedService) {
        webTestClient.get()
                .uri(uri)
                .header(AuthHeaders.USER_ID, "999")
                .header(AuthHeaders.USERNAME, "mallory")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.service").isEqualTo(expectedService)
                .jsonPath("$.userId").isEqualTo("")
                .jsonPath("$.username").isEqualTo("");
    }

    private void expectProtectedRoute(String uri, String expectedService, String token) {
        webTestClient.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(AuthHeaders.USER_ID, "999")
                .header(AuthHeaders.USERNAME, "mallory")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.service").isEqualTo(expectedService)
                .jsonPath("$.userId").isEqualTo("42")
                .jsonPath("$.username").isEqualTo("alice");
    }

    private void expectInternalPathForbidden(String uri) {
        webTestClient.get()
                .uri(uri)
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().contentTypeCompatibleWith("application/json")
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.code").isEqualTo(ErrorCode.FORBIDDEN.getCode())
                .jsonPath("$.message").isEqualTo("Internal API is not exposed");
    }

    @TestConfiguration
    static class RegressionTestConfiguration {

        @Bean
        @Primary
        TestGatewayRateLimiter regressionGatewayRateLimiter() {
            return new TestGatewayRateLimiter();
        }

        @Bean
        RegressionDownstreamController regressionDownstreamController() {
            return new RegressionDownstreamController();
        }
    }

    static final class TestGatewayRateLimiter implements GatewayRateLimiter {

        private final List<String> keys = new CopyOnWriteArrayList<>();
        private volatile boolean allowed = true;

        @Override
        public Mono<GatewayRateLimitResult> isAllowed(String key) {
            keys.add(key);
            if (allowed) {
                return Mono.just(GatewayRateLimitResult.allowed(9));
            }
            return Mono.just(GatewayRateLimitResult.denied(0));
        }

        void deny() {
            allowed = false;
        }

        void reset() {
            keys.clear();
            allowed = true;
        }

        List<String> keys() {
            return List.copyOf(keys);
        }
    }

    @RestController
    static class RegressionDownstreamController {

        @RequestMapping("/__gateway-regression/{service}")
        Map<String, String> routed(@PathVariable("service") String service, ServerHttpRequest request) {
            return Map.of(
                    "service", service,
                    "userId", headerValue(request, AuthHeaders.USER_ID),
                    "username", headerValue(request, AuthHeaders.USERNAME));
        }

        private String headerValue(ServerHttpRequest request, String headerName) {
            String value = request.getHeaders().getFirst(headerName);
            return value == null ? "" : value;
        }
    }
}
