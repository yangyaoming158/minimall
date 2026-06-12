package com.minimall.inventory.client.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.minimall.common.auth.constants.AuthHeaders;
import com.minimall.common.auth.context.AuthRole;
import com.minimall.common.auth.context.UserContext;
import com.minimall.common.auth.context.UserContextHolder;
import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.common.core.response.PageResponse;
import com.minimall.inventory.dto.AiSalesEvidenceResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;

class OrderServiceSalesEvidenceClientTest {

    private final RestTemplate restTemplate = restTemplate();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    private final OrderServiceSalesEvidenceClient client = new OrderServiceSalesEvidenceClient(restTemplate);

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
        restTemplate.setInterceptors(List.of());
    }

    @Test
    void salesByProductCallsAdminOperationStatsWithFiltersAndReturnsEvidence() {
        UserContextHolder.set(UserContext.of(900L, "admin", AuthRole.ADMIN));
        restTemplate.setInterceptors(List.of(
                new OrderServiceSalesEvidenceClient.AdminPropagationInterceptor("internal-secret")));
        server.expect(once(), requestTo(
                        "http://order-service/api/admin/operation-stats/sales-by-product?page=0&size=10&productId=SKU-AI-SALES&createdFrom=2026-06-01T00:00:00&createdTo=2026-06-07T23:59:00"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(AuthHeaders.GATEWAY_TOKEN, "internal-secret"))
                .andExpect(header(AuthHeaders.USER_ID, "900"))
                .andExpect(header(AuthHeaders.USERNAME, "admin"))
                .andExpect(header(AuthHeaders.USER_ROLE, "ADMIN"))
                .andRespond(withSuccess("""
                        {"success":true,"code":"0","message":"success","data":{"content":[{"productId":"SKU-AI-SALES","soldQuantity":7,"orderCount":3,"totalAmount":159.50}],"page":0,"size":10,"totalElements":1,"totalPages":1}}
                        """, MediaType.APPLICATION_JSON));

        PageResponse<AiSalesEvidenceResponse> response = client.salesByProduct(
                " SKU-AI-SALES ",
                LocalDateTime.of(2026, 6, 1, 0, 0),
                LocalDateTime.of(2026, 6, 7, 23, 59),
                PageRequest.of(0, 10));

        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.content()).singleElement().satisfies(item -> {
            assertThat(item.productId()).isEqualTo("SKU-AI-SALES");
            assertThat(item.soldQuantity()).isEqualTo(7);
            assertThat(item.orderCount()).isEqualTo(3);
            assertThat(item.totalAmount()).isEqualByComparingTo(new BigDecimal("159.50"));
        });
        server.verify();
    }

    @Test
    void salesByProductUsesBoundedDefaultPagingAndOmitsBlankProductId() {
        server.expect(once(), requestTo(
                        "http://order-service/api/admin/operation-stats/sales-by-product?page=0&size=20"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"success":true,"code":"0","message":"success","data":{"content":[],"page":0,"size":20,"totalElements":0,"totalPages":0}}
                        """, MediaType.APPLICATION_JSON));

        PageResponse<AiSalesEvidenceResponse> response = client.salesByProduct(" ", null, null, null);

        assertThat(response.content()).isEmpty();
        assertThat(response.size()).isEqualTo(20);
        server.verify();
    }

    @Test
    void salesByProductRejectsInvalidDateRangeBeforeCallingOrderService() {
        assertThatThrownBy(() -> client.salesByProduct(
                        "SKU",
                        LocalDateTime.of(2026, 6, 8, 0, 0),
                        LocalDateTime.of(2026, 6, 7, 0, 0),
                        PageRequest.of(0, 10)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
                    assertThat(exception.getMessage()).isEqualTo("createdFrom must be before or equal to createdTo");
                });
        server.verify();
    }

    @Test
    void salesByProductMapsForbiddenBodyToStableBusinessException() {
        server.expect(once(), requestTo(
                        "http://order-service/api/admin/operation-stats/sales-by-product?page=0&size=10&productId=SKU-FORBIDDEN"))
                .andRespond(withSuccess("""
                        {"success":false,"code":"40300","message":"downstream detail","data":null}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.salesByProduct("SKU-FORBIDDEN", null, null, PageRequest.of(0, 10)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
                    assertThat(exception.getMessage()).isEqualTo("Forbidden");
                });
        server.verify();
    }

    @Test
    void salesByProductMapsHttpUnauthorizedToStableBusinessException() {
        server.expect(once(), requestTo(
                        "http://order-service/api/admin/operation-stats/sales-by-product?page=0&size=10&productId=SKU-UNAUTH"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.salesByProduct("SKU-UNAUTH", null, null, PageRequest.of(0, 10)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
                    assertThat(exception.getMessage()).isEqualTo("Unauthorized");
                });
        server.verify();
    }

    @Test
    void salesByProductMapsDownstreamFailureWithoutLeakingDetails() {
        server.expect(once(), requestTo(
                        "http://order-service/api/admin/operation-stats/sales-by-product?page=0&size=10&productId=SKU-ERR"))
                .andRespond(withServerError().body("database stack trace"));

        assertThatThrownBy(() -> client.salesByProduct("SKU-ERR", null, null, PageRequest.of(0, 10)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR);
                    assertThat(exception.getMessage()).isEqualTo("Sales evidence query failed");
                });
        server.verify();
    }

    private RestTemplate restTemplate() {
        return restTemplateBuilder().build();
    }

    private org.springframework.boot.web.client.RestTemplateBuilder restTemplateBuilder() {
        return new org.springframework.boot.web.client.RestTemplateBuilder()
                .rootUri("http://order-service")
                .uriTemplateHandler(new DefaultUriBuilderFactory("http://order-service"));
    }
}
