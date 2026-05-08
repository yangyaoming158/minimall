package com.minimall.order.client.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.test.web.client.MockRestServiceServer;

class ProductClientTest {

    private final RestTemplate restTemplate = restTemplate();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    private final ProductClient productClient = new ProductClient(restTemplate);

    @Test
    void getProductReturnsInternalProductSnapshot() {
        server.expect(once(), requestTo("http://product-service/internal/products/SKU-3001"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"success":true,"code":"00000","message":"Success","data":{"productId":"SKU-3001","name":"Keyboard","price":129.90,"status":"ON_SHELF"}}
                        """, MediaType.APPLICATION_JSON));

        ProductSnapshot product = productClient.getProduct("SKU-3001");

        assertThat(product.productId()).isEqualTo("SKU-3001");
        assertThat(product.name()).isEqualTo("Keyboard");
        assertThat(product.price()).isEqualByComparingTo(new BigDecimal("129.90"));
        assertThat(product.status()).isEqualTo(ProductStatus.ON_SHELF);
        server.verify();
    }

    @Test
    void getProductMapsHttpNotFoundToStableBusinessException() {
        server.expect(once(), requestTo("http://product-service/internal/products/MISSING"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withResourceNotFound());

        assertThatThrownBy(() -> productClient.getProduct("MISSING"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
                    assertThat(exception.getMessage()).isEqualTo("Product not found");
                });
        server.verify();
    }

    @Test
    void getProductMapsUnsuccessfulNotFoundBodyToStableBusinessException() {
        server.expect(once(), requestTo("http://product-service/internal/products/MISSING-BODY"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"success":false,"code":"40400","message":"downstream detail","data":null}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> productClient.getProduct("MISSING-BODY"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
                    assertThat(exception.getMessage()).isEqualTo("Product not found");
                });
        server.verify();
    }

    @Test
    void getProductMapsDownstreamFailureWithoutLeakingDetails() {
        server.expect(once(), requestTo("http://product-service/internal/products/SKU-ERR"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError().body("database stack trace"));

        assertThatThrownBy(() -> productClient.getProduct("SKU-ERR"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR);
                    assertThat(exception.getMessage()).isEqualTo("Product validation failed");
                });
        server.verify();
    }

    private RestTemplate restTemplate() {
        RestTemplate template = new RestTemplate();
        template.setUriTemplateHandler(new DefaultUriBuilderFactory("http://product-service"));
        return template;
    }
}
