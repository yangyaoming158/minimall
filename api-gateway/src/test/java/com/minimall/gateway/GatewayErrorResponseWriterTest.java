package com.minimall.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.gateway.web.GatewayErrorResponseWriter;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class GatewayErrorResponseWriterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GatewayErrorResponseWriter writer = new GatewayErrorResponseWriter(objectMapper);

    @Test
    void writesForbiddenApiResponse() throws Exception {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/order/orders/my").build());

        writer.forbidden(exchange, "Forbidden").block();

        assertResponse(exchange, HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "Forbidden");
    }

    @Test
    void writesTooManyRequestsApiResponse() throws Exception {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/order/orders/my").build());

        writer.tooManyRequests(exchange).block();

        assertResponse(exchange, HttpStatus.TOO_MANY_REQUESTS, ErrorCode.TOO_MANY_REQUESTS,
                ErrorCode.TOO_MANY_REQUESTS.getMessage());
    }

    private void assertResponse(
            MockServerWebExchange exchange,
            HttpStatus expectedStatus,
            ErrorCode expectedCode,
            String expectedMessage) throws Exception {
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(expectedStatus);
        assertThat(exchange.getResponse().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);

        String body = exchange.getResponse().getBodyAsString().block();
        JsonNode json = objectMapper.readTree(body);
        assertThat(json.get("success").asBoolean()).isFalse();
        assertThat(json.get("code").asText()).isEqualTo(expectedCode.getCode());
        assertThat(json.get("message").asText()).isEqualTo(expectedMessage);
        assertThat(json.get("data").isNull()).isTrue();
    }
}
