package com.minimall.product.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minimall.common.core.audit.AdminAuditAction;
import com.minimall.common.core.audit.AdminAuditLogWriteRequest;
import com.minimall.common.core.audit.AdminAuditResourceType;
import com.minimall.common.core.audit.AdminAuditWriter;
import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.common.core.response.PageResponse;
import com.minimall.product.domain.Product;
import com.minimall.product.domain.ProductStatus;
import com.minimall.product.dto.CreateProductRequest;
import com.minimall.product.dto.InternalProductResponse;
import com.minimall.product.dto.ProductResponse;
import com.minimall.product.dto.UpdateProductRequest;
import com.minimall.product.repository.ProductRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Service
public class ProductService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProductService.class);
    private static final String PRODUCT_DETAIL_CACHE_KEY_PREFIX = "product:detail:";
    private static final String PRODUCT_EXISTS_MESSAGE = "Product already exists";
    private static final String PRODUCT_NOT_FOUND_MESSAGE = "Product not found";
    private static final String PRODUCT_ALREADY_ON_SHELF_MESSAGE = "Product is already on shelf";
    private static final String PRODUCT_ALREADY_OFF_SHELF_MESSAGE = "Product is already off shelf";

    private final ProductRepository productRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AdminAuditWriter adminAuditWriter;
    private final Duration detailCacheTtl;

    public ProductService(
            ProductRepository productRepository,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            AdminAuditWriter adminAuditWriter,
            @Value("${minimall.product.cache.detail-ttl-seconds:300}") long detailCacheTtlSeconds) {
        this.productRepository = productRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.adminAuditWriter = adminAuditWriter;
        this.detailCacheTtl = Duration.ofSeconds(Math.max(1, detailCacheTtlSeconds));
    }

    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        if (productRepository.existsByProductId(request.productId())) {
            throw new BusinessException(ErrorCode.CONFLICT, PRODUCT_EXISTS_MESSAGE);
        }

        Product product = new Product(
                request.productId(),
                request.name(),
                request.description(),
                request.imageUrl(),
                request.price());
        try {
            return ProductResponse.from(productRepository.saveAndFlush(product));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, PRODUCT_EXISTS_MESSAGE, exception);
        }
    }

    @Transactional
    public ProductResponse create(CreateProductRequest request, ProductAdminAuditContext auditContext) {
        if (productRepository.existsByProductId(request.productId())) {
            throw new BusinessException(ErrorCode.CONFLICT, PRODUCT_EXISTS_MESSAGE);
        }

        Product product = new Product(
                request.productId(),
                request.name(),
                request.description(),
                request.imageUrl(),
                request.price());
        try {
            ProductResponse response = ProductResponse.from(productRepository.saveAndFlush(product));
            writeAudit(
                    auditContext,
                    AdminAuditAction.PRODUCT_CREATE,
                    response.productId(),
                    null,
                    response,
                    "Create product " + response.productId());
            return response;
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, PRODUCT_EXISTS_MESSAGE, exception);
        }
    }

    @Transactional
    public ProductResponse update(String productId, UpdateProductRequest request) {
        Product product = getProduct(productId);
        product.updateDetails(request.name(), request.description(), request.imageUrl(), request.price());
        Product savedProduct = productRepository.save(product);
        evictProductDetailAfterCommit(productId);
        return ProductResponse.from(savedProduct);
    }

    @Transactional
    public ProductResponse update(String productId, UpdateProductRequest request, ProductAdminAuditContext auditContext) {
        Product product = getProduct(productId);
        ProductResponse before = ProductResponse.from(product);
        product.updateDetails(request.name(), request.description(), request.imageUrl(), request.price());
        Product savedProduct = productRepository.save(product);
        ProductResponse after = ProductResponse.from(savedProduct);
        writeAudit(
                auditContext,
                AdminAuditAction.PRODUCT_UPDATE,
                productId,
                before,
                after,
                "Update product " + productId);
        evictProductDetailAfterCommit(productId);
        return after;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> list(ProductStatus status, Pageable pageable) {
        if (status == null) {
            return PageResponse.from(productRepository.findAll(pageable).map(ProductResponse::from));
        }
        return PageResponse.from(productRepository.findByStatus(status, pageable).map(ProductResponse::from));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> adminList(String keyword, ProductStatus status, Pageable pageable) {
        return PageResponse.from(productRepository.findAll(adminProductSpecification(keyword, status), pageable)
                .map(ProductResponse::from));
    }

    @Transactional(readOnly = true)
    public ProductResponse detail(String productId) {
        return getCachedProductDetail(productId);
    }

    @Transactional(readOnly = true)
    public InternalProductResponse internalDetail(String productId) {
        ProductResponse product = getCachedProductDetail(productId);
        return new InternalProductResponse(product.productId(), product.name(), product.price(), product.status());
    }

    @Transactional
    public ProductResponse onShelf(String productId) {
        Product product = getProduct(productId);
        if (product.getStatus() == ProductStatus.ON_SHELF) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, PRODUCT_ALREADY_ON_SHELF_MESSAGE);
        }
        product.onShelf();
        Product savedProduct = productRepository.save(product);
        evictProductDetailAfterCommit(productId);
        return ProductResponse.from(savedProduct);
    }

    @Transactional
    public ProductResponse onShelf(String productId, ProductAdminAuditContext auditContext) {
        Product product = getProduct(productId);
        if (product.getStatus() == ProductStatus.ON_SHELF) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, PRODUCT_ALREADY_ON_SHELF_MESSAGE);
        }
        ProductResponse before = ProductResponse.from(product);
        product.onShelf();
        Product savedProduct = productRepository.save(product);
        ProductResponse after = ProductResponse.from(savedProduct);
        writeAudit(
                auditContext,
                AdminAuditAction.PRODUCT_ON_SHELF,
                productId,
                before,
                after,
                "Put product " + productId + " on shelf");
        evictProductDetailAfterCommit(productId);
        return after;
    }

    @Transactional
    public ProductResponse offShelf(String productId) {
        Product product = getProduct(productId);
        if (product.getStatus() == ProductStatus.OFF_SHELF) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, PRODUCT_ALREADY_OFF_SHELF_MESSAGE);
        }
        product.offShelf();
        Product savedProduct = productRepository.save(product);
        evictProductDetailAfterCommit(productId);
        return ProductResponse.from(savedProduct);
    }

    @Transactional
    public ProductResponse offShelf(String productId, ProductAdminAuditContext auditContext) {
        Product product = getProduct(productId);
        if (product.getStatus() == ProductStatus.OFF_SHELF) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, PRODUCT_ALREADY_OFF_SHELF_MESSAGE);
        }
        ProductResponse before = ProductResponse.from(product);
        product.offShelf();
        Product savedProduct = productRepository.save(product);
        ProductResponse after = ProductResponse.from(savedProduct);
        writeAudit(
                auditContext,
                AdminAuditAction.PRODUCT_OFF_SHELF,
                productId,
                before,
                after,
                "Take product " + productId + " off shelf");
        evictProductDetailAfterCommit(productId);
        return after;
    }

    @Transactional
    public ProductResponse updateStatus(String productId, ProductStatus status) {
        return switch (status) {
            case ON_SHELF -> onShelf(productId);
            case OFF_SHELF -> offShelf(productId);
        };
    }

    @Transactional
    public ProductResponse updateStatus(
            String productId,
            ProductStatus status,
            ProductAdminAuditContext auditContext) {
        return switch (status) {
            case ON_SHELF -> onShelf(productId, auditContext);
            case OFF_SHELF -> offShelf(productId, auditContext);
        };
    }

    private void writeAudit(
            ProductAdminAuditContext auditContext,
            AdminAuditAction action,
            String productId,
            ProductResponse before,
            ProductResponse after,
            String summary) {
        adminAuditWriter.write(new AdminAuditLogWriteRequest(
                auditContext.adminUserId(),
                auditContext.adminUsername(),
                action,
                AdminAuditResourceType.PRODUCT,
                productId,
                auditContext.requestId(),
                null,
                null,
                toSnapshot(before),
                toSnapshot(after),
                auditContext.ip(),
                auditContext.userAgent(),
                summary));
    }

    private JsonNode toSnapshot(ProductResponse response) {
        return response == null ? null : objectMapper.valueToTree(response);
    }

    private Specification<Product> adminProductSpecification(String keyword, ProductStatus status) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            if (StringUtils.hasText(keyword)) {
                String pattern = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("productId")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), pattern)));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private ProductResponse getCachedProductDetail(String productId) {
        String cacheKey = productDetailCacheKey(productId);
        ProductResponse cachedProduct = readProductDetailCache(cacheKey);
        if (cachedProduct != null) {
            return cachedProduct;
        }

        ProductResponse product = ProductResponse.from(getProduct(productId));
        writeProductDetailCache(cacheKey, product);
        return product;
    }

    private ProductResponse readProductDetailCache(String cacheKey) {
        try {
            String cachedProduct = redisTemplate.opsForValue().get(cacheKey);
            if (!StringUtils.hasText(cachedProduct)) {
                return null;
            }
            return objectMapper.readValue(cachedProduct, ProductResponse.class);
        } catch (JsonProcessingException exception) {
            evictProductDetailByKey(cacheKey);
            LOGGER.warn("Failed to deserialize product detail cache: {}", cacheKey, exception);
            return null;
        } catch (RuntimeException exception) {
            LOGGER.warn("Failed to read product detail cache: {}", cacheKey, exception);
            return null;
        }
    }

    private void writeProductDetailCache(String cacheKey, ProductResponse product) {
        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(product), detailCacheTtl);
        } catch (JsonProcessingException exception) {
            LOGGER.warn("Failed to serialize product detail cache: {}", cacheKey, exception);
        } catch (RuntimeException exception) {
            LOGGER.warn("Failed to write product detail cache: {}", cacheKey, exception);
        }
    }

    private void evictProductDetailAfterCommit(String productId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evictProductDetail(productId);
                }
            });
            return;
        }
        evictProductDetail(productId);
    }

    private void evictProductDetail(String productId) {
        evictProductDetailByKey(productDetailCacheKey(productId));
    }

    private void evictProductDetailByKey(String cacheKey) {
        try {
            redisTemplate.delete(cacheKey);
        } catch (RuntimeException exception) {
            LOGGER.warn("Failed to evict product detail cache: {}", cacheKey, exception);
        }
    }

    private String productDetailCacheKey(String productId) {
        return PRODUCT_DETAIL_CACHE_KEY_PREFIX + productId;
    }

    private Product getProduct(String productId) {
        return productRepository.findByProductId(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, PRODUCT_NOT_FOUND_MESSAGE));
    }
}
