package com.minimall.order.client.inventory;

import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.order.client.InternalTokenInterceptor;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpClientErrorException.Conflict;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

@Component
public class InventoryClient {

    private static final String INVENTORY_NOT_FOUND_MESSAGE = "Inventory not found";
    private static final String INSUFFICIENT_INVENTORY_MESSAGE = "Insufficient inventory";
    private static final String INSUFFICIENT_LOCKED_INVENTORY_MESSAGE = "Insufficient locked inventory";
    private static final String INVENTORY_DEDUCT_FAILED_MESSAGE = "Inventory deduct failed";
    private static final String INVENTORY_RELEASE_FAILED_MESSAGE = "取消失败，请稍后重试";
    private static final String INVENTORY_DEDUCT_PATH = "/internal/inventories/deduct";
    private static final String INVENTORY_RELEASE_PATH = "/internal/inventories/release";

    private final RestTemplate restTemplate;

    @Autowired
    public InventoryClient(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${minimall.clients.inventory-service.base-url:http://127.0.0.1:8103}") String inventoryServiceBaseUrl,
            @Value("${minimall.auth.internal.secret:}") String internalSecret) {
        this(restTemplateBuilder
                .rootUri(inventoryServiceBaseUrl)
                .additionalInterceptors(new InternalTokenInterceptor(internalSecret))
                .build());
    }

    InventoryClient(RestTemplate restTemplate) {
        this.restTemplate = Objects.requireNonNull(restTemplate, "restTemplate must not be null");
    }

    public InventorySnapshot deduct(InventoryDeductRequest request) {
        try {
            ResponseEntity<InventoryApiResponse> response = restTemplate.postForEntity(
                    INVENTORY_DEDUCT_PATH,
                    request,
                    InventoryApiResponse.class);
            return extractInventory(response.getBody(), INVENTORY_DEDUCT_FAILED_MESSAGE, INSUFFICIENT_INVENTORY_MESSAGE);
        } catch (Conflict exception) {
            throw new BusinessException(ErrorCode.CONFLICT, INSUFFICIENT_INVENTORY_MESSAGE, exception);
        } catch (HttpClientErrorException.NotFound exception) {
            throw new BusinessException(ErrorCode.NOT_FOUND, INVENTORY_NOT_FOUND_MESSAGE, exception);
        } catch (RestClientResponseException | ResourceAccessException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, INVENTORY_DEDUCT_FAILED_MESSAGE, exception);
        } catch (RestClientException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, INVENTORY_DEDUCT_FAILED_MESSAGE, exception);
        }
    }

    public InventorySnapshot release(InventoryDeductRequest request) {
        try {
            ResponseEntity<InventoryApiResponse> response = restTemplate.postForEntity(
                    INVENTORY_RELEASE_PATH,
                    request,
                    InventoryApiResponse.class);
            return extractInventory(response.getBody(), INVENTORY_RELEASE_FAILED_MESSAGE, INSUFFICIENT_LOCKED_INVENTORY_MESSAGE);
        } catch (Conflict exception) {
            throw new BusinessException(ErrorCode.CONFLICT, INSUFFICIENT_LOCKED_INVENTORY_MESSAGE, exception);
        } catch (HttpClientErrorException.BadRequest exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, INSUFFICIENT_LOCKED_INVENTORY_MESSAGE, exception);
        } catch (HttpClientErrorException.NotFound exception) {
            throw new BusinessException(ErrorCode.NOT_FOUND, INVENTORY_NOT_FOUND_MESSAGE, exception);
        } catch (RestClientResponseException | ResourceAccessException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, INVENTORY_RELEASE_FAILED_MESSAGE, exception);
        } catch (RestClientException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, INVENTORY_RELEASE_FAILED_MESSAGE, exception);
        }
    }

    private InventorySnapshot extractInventory(
            InventoryApiResponse response,
            String failureMessage,
            String stockConflictMessage) {
        if (response == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, failureMessage);
        }
        if (!response.success()) {
            if (ErrorCode.CONFLICT.getCode().equals(response.code())) {
                throw new BusinessException(ErrorCode.CONFLICT, stockConflictMessage);
            }
            if (ErrorCode.BAD_REQUEST.getCode().equals(response.code())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, stockConflictMessage);
            }
            if (ErrorCode.NOT_FOUND.getCode().equals(response.code())) {
                throw new BusinessException(ErrorCode.NOT_FOUND, INVENTORY_NOT_FOUND_MESSAGE);
            }
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, failureMessage);
        }
        if (response.data() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, failureMessage);
        }
        return response.data();
    }
}
