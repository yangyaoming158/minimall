package com.minimall.order.client.product;

import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

@Component
public class ProductClient {

    private static final String PRODUCT_NOT_FOUND_MESSAGE = "Product not found";
    private static final String PRODUCT_VALIDATION_FAILED_MESSAGE = "Product validation failed";
    private static final String PRODUCT_DETAIL_PATH = "/internal/products/{productId}";

    private final RestTemplate restTemplate;

    @Autowired
    public ProductClient(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${minimall.clients.product-service.base-url:http://127.0.0.1:8102}") String productServiceBaseUrl) {
        this(restTemplateBuilder.rootUri(productServiceBaseUrl).build());
    }

    ProductClient(RestTemplate restTemplate) {
        this.restTemplate = Objects.requireNonNull(restTemplate, "restTemplate must not be null");
    }

    public ProductSnapshot getProduct(String productId) {
        try {
            ResponseEntity<ProductApiResponse> response = restTemplate.exchange(
                    PRODUCT_DETAIL_PATH,
                    HttpMethod.GET,
                    null,
                    ProductApiResponse.class,
                    productId);
            return extractProduct(response.getBody());
        } catch (HttpClientErrorException.NotFound exception) {
            throw new BusinessException(ErrorCode.NOT_FOUND, PRODUCT_NOT_FOUND_MESSAGE, exception);
        } catch (RestClientResponseException | ResourceAccessException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, PRODUCT_VALIDATION_FAILED_MESSAGE, exception);
        } catch (RestClientException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, PRODUCT_VALIDATION_FAILED_MESSAGE, exception);
        }
    }

    private ProductSnapshot extractProduct(ProductApiResponse response) {
        if (response == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, PRODUCT_VALIDATION_FAILED_MESSAGE);
        }
        if (!response.success()) {
            if (ErrorCode.NOT_FOUND.getCode().equals(response.code())) {
                throw new BusinessException(ErrorCode.NOT_FOUND, PRODUCT_NOT_FOUND_MESSAGE);
            }
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, PRODUCT_VALIDATION_FAILED_MESSAGE);
        }
        if (response.data() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, PRODUCT_VALIDATION_FAILED_MESSAGE);
        }
        return response.data();
    }
}
