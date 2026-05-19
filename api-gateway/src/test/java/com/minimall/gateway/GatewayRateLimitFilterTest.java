package com.minimall.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.minimall.common.auth.jwt.JwtUtils;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.gateway.config.GatewayRateLimitProperties;
import com.minimall.gateway.ratelimit.GatewayRateLimitResult;
import com.minimall.gateway.ratelimit.GatewayRateLimiter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@SpringBootTest
@AutoConfigureWebTestClient
@TestPropertySource(properties = {
        "USER_SERVICE_BASE_URL=forward:/__gateway-rate-limit-test",
        "PRODUCT_SERVICE_BASE_URL=forward:/__gateway-rate-limit-test",
        "INVENTORY_SERVICE_BASE_URL=forward:/__gateway-rate-limit-test",
        "ORDER_SERVICE_BASE_URL=forward:/__gateway-rate-limit-test",
        "PAYMENT_SERVICE_BASE_URL=forward:/__gateway-rate-limit-test",
        "minimall.auth.jwt.secret=test-gateway-rate-limit-jwt-secret",
        "minimall.auth.jwt.expire-seconds=3600",
        "minimall.gateway.rate-limit.enabled=true",
        "minimall.gateway.rate-limit.replenish-rate=7",
        "minimall.gateway.rate-limit.burst-capacity=11",
        "minimall.gateway.rate-limit.requested-tokens=2",
        "minimall.gateway.rate-limit.key-prefix=test:gateway:rate-limit",
        "minimall.gateway.rate-limit.fail-open=false"
})
class GatewayRateLimitFilterTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private GatewayRateLimitProperties properties;

    @Autowired
    private TestGatewayRateLimiter rateLimiter;

    @BeforeEach
    void resetRateLimiter() {
        rateLimiter.reset();
    }

    @Test
    void bindsRateLimitConfiguration() {
        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getReplenishRate()).isEqualTo(7);
        assertThat(properties.getBurstCapacity()).isEqualTo(11);
        assertThat(properties.getRequestedTokens()).isEqualTo(2);
        assertThat(properties.getKeyPrefix()).isEqualTo("test:gateway:rate-limit");
        assertThat(properties.isFailOpen()).isFalse();
    }

    @Test
    void allowedAuthenticatedRequestUsesUserRateLimitKey() {
        String token = jwtUtils.generateToken(42L, "alice");

        webTestClient.get()
                .uri("/api/orders/my")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk();

        assertThat(rateLimiter.keys()).containsExactly("user:42");
    }

    @Test
    void rateLimitedRequestReturnsTooManyRequestsApiResponse() {
        rateLimiter.deny();
        String token = jwtUtils.generateToken(42L, "alice");

        webTestClient.get()
                .uri("/api/orders/my")
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
    void publicRequestFallsBackToIpRateLimitKey() {
        webTestClient.post()
                .uri("/api/users/login")
                .exchange()
                .expectStatus().isOk();

        assertThat(rateLimiter.keys()).containsExactly("ip:unknown");
    }

    @Test
    void preflightRequestBypassesRateLimiter() {
        rateLimiter.deny();

        webTestClient.options()
                .uri("/api/orders/my")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization, Content-Type")
                .exchange()
                .expectStatus().isOk();

        assertThat(rateLimiter.keys()).isEmpty();
    }

    @TestConfiguration
    static class TestRateLimitConfiguration {

        @Bean
        @Primary
        TestGatewayRateLimiter testGatewayRateLimiter() {
            return new TestGatewayRateLimiter();
        }

        @Bean
        TestDownstreamController testDownstreamController() {
            return new TestDownstreamController();
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
    static class TestDownstreamController {

        @RequestMapping("/__gateway-rate-limit-test")
        Map<String, String> ok() {
            return Map.of("status", "ok");
        }
    }
}
