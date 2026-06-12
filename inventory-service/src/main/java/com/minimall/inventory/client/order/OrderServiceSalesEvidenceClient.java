package com.minimall.inventory.client.order;

import com.minimall.common.auth.constants.AuthHeaders;
import com.minimall.common.auth.context.UserContext;
import com.minimall.common.auth.context.UserContextHolder;
import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.common.core.response.PageResponse;
import com.minimall.inventory.dto.AiSalesEvidenceResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class OrderServiceSalesEvidenceClient implements SalesEvidenceClient {

    private static final String SALES_BY_PRODUCT_PATH = "/api/admin/operation-stats/sales-by-product";
    private static final String SALES_EVIDENCE_QUERY_FAILED_MESSAGE = "Sales evidence query failed";
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final RestTemplate restTemplate;

    @Autowired
    public OrderServiceSalesEvidenceClient(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${minimall.clients.order-service.base-url:http://127.0.0.1:8104}") String orderServiceBaseUrl,
            @Value("${minimall.auth.internal.secret:}") String internalSecret) {
        this(restTemplateBuilder
                .rootUri(orderServiceBaseUrl)
                .additionalInterceptors(new AdminPropagationInterceptor(internalSecret))
                .build());
    }

    OrderServiceSalesEvidenceClient(RestTemplate restTemplate) {
        this.restTemplate = Objects.requireNonNull(restTemplate, "restTemplate must not be null");
    }

    @Override
    public PageResponse<AiSalesEvidenceResponse> salesByProduct(
            String productId,
            LocalDateTime createdFrom,
            LocalDateTime createdTo,
            Pageable pageable) {
        validateCreatedRange(createdFrom, createdTo);
        try {
            ResponseEntity<SalesEvidenceApiResponse> response = restTemplate.exchange(
                    requestPath(productId, createdFrom, createdTo, pageable),
                    HttpMethod.GET,
                    null,
                    SalesEvidenceApiResponse.class);
            return extractSalesEvidence(response.getBody());
        } catch (HttpClientErrorException.BadRequest exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, SALES_EVIDENCE_QUERY_FAILED_MESSAGE, exception);
        } catch (HttpClientErrorException.Unauthorized exception) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.getMessage(), exception);
        } catch (HttpClientErrorException.Forbidden exception) {
            throw new BusinessException(ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.getMessage(), exception);
        } catch (RestClientResponseException | ResourceAccessException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, SALES_EVIDENCE_QUERY_FAILED_MESSAGE, exception);
        } catch (RestClientException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, SALES_EVIDENCE_QUERY_FAILED_MESSAGE, exception);
        }
    }

    private String requestPath(
            String productId,
            LocalDateTime createdFrom,
            LocalDateTime createdTo,
            Pageable pageable) {
        Pageable boundedPageable = boundedPageable(pageable);
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(SALES_BY_PRODUCT_PATH)
                .queryParam("page", boundedPageable.getPageNumber())
                .queryParam("size", boundedPageable.getPageSize());
        if (StringUtils.hasText(productId)) {
            builder.queryParam("productId", productId.trim());
        }
        if (createdFrom != null) {
            builder.queryParam("createdFrom", DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(createdFrom));
        }
        if (createdTo != null) {
            builder.queryParam("createdTo", DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(createdTo));
        }
        return builder.encode().toUriString();
    }

    private Pageable boundedPageable(Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            return PageRequest.of(0, DEFAULT_PAGE_SIZE);
        }
        int page = Math.max(0, pageable.getPageNumber());
        int size = Math.max(1, Math.min(pageable.getPageSize(), MAX_PAGE_SIZE));
        return PageRequest.of(page, size);
    }

    private void validateCreatedRange(LocalDateTime createdFrom, LocalDateTime createdTo) {
        if (createdFrom != null && createdTo != null && createdFrom.isAfter(createdTo)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "createdFrom must be before or equal to createdTo");
        }
    }

    private PageResponse<AiSalesEvidenceResponse> extractSalesEvidence(SalesEvidenceApiResponse response) {
        if (response == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, SALES_EVIDENCE_QUERY_FAILED_MESSAGE);
        }
        if (!response.success()) {
            if (ErrorCode.BAD_REQUEST.getCode().equals(response.code())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, SALES_EVIDENCE_QUERY_FAILED_MESSAGE);
            }
            if (ErrorCode.UNAUTHORIZED.getCode().equals(response.code())) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.getMessage());
            }
            if (ErrorCode.FORBIDDEN.getCode().equals(response.code())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.getMessage());
            }
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, SALES_EVIDENCE_QUERY_FAILED_MESSAGE);
        }
        if (response.data() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, SALES_EVIDENCE_QUERY_FAILED_MESSAGE);
        }
        return response.data();
    }

    private record SalesEvidenceApiResponse(
            boolean success,
            String code,
            String message,
            PageResponse<AiSalesEvidenceResponse> data) {
    }

    static final class AdminPropagationInterceptor implements ClientHttpRequestInterceptor {

        private final String internalSecret;

        AdminPropagationInterceptor(String internalSecret) {
            this.internalSecret = StringUtils.hasText(internalSecret) ? internalSecret : null;
        }

        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
                throws IOException {
            if (internalSecret != null) {
                request.getHeaders().set(AuthHeaders.GATEWAY_TOKEN, internalSecret);
            }
            UserContextHolder.get().ifPresent(userContext -> addUserHeaders(request, userContext));
            return execution.execute(request, body);
        }

        private void addUserHeaders(HttpRequest request, UserContext userContext) {
            request.getHeaders().set(AuthHeaders.USER_ID, String.valueOf(userContext.getUserId()));
            request.getHeaders().set(AuthHeaders.USERNAME, userContext.getUsername());
            request.getHeaders().set(AuthHeaders.USER_ROLE, userContext.getRole().name());
        }
    }
}
