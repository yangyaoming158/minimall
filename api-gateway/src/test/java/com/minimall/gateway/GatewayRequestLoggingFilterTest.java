package com.minimall.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.minimall.gateway.web.GatewayRequestLoggingFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

@ExtendWith(OutputCaptureExtension.class)
class GatewayRequestLoggingFilterTest {

    private final GatewayRequestLoggingFilter filter = new GatewayRequestLoggingFilter();

    @Test
    void logsRequestSummaryWithoutSensitiveHeaders(CapturedOutput output) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/order/orders/my")
                .header(HttpHeaders.AUTHORIZATION, "Bearer secret-token")
                .build());

        filter.filter(exchange, filteredExchange -> {
            filteredExchange.getResponse().setStatusCode(HttpStatus.CREATED);
            return Mono.empty();
        }).block();

        assertThat(output).contains("gateway request method=GET path=/api/order/orders/my status=201 durationMs=");
        assertThat(output).doesNotContain(HttpHeaders.AUTHORIZATION);
        assertThat(output).doesNotContain("secret-token");
    }

    @Test
    void logsOkWhenResponseStatusIsImplicit(CapturedOutput output) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/user/users/login")
                .build());

        filter.filter(exchange, filteredExchange -> Mono.empty()).block();

        assertThat(output).contains("gateway request method=POST path=/api/user/users/login status=200 durationMs=");
    }
}
