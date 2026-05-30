package com.minimall.product.web;

import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.common.core.response.ApiResponse;
import com.minimall.common.core.response.PageResponse;
import com.minimall.product.domain.ProductStatus;
import com.minimall.product.dto.CreateProductRequest;
import com.minimall.product.dto.ProductResponse;
import com.minimall.product.dto.UpdateProductRequest;
import com.minimall.product.dto.UpdateProductStatusRequest;
import com.minimall.product.service.ProductService;
import jakarta.validation.Valid;
import java.util.Locale;
import org.springframework.data.domain.Pageable;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/products")
public class AdminProductController {

    private final ProductService productService;

    public AdminProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public ApiResponse<PageResponse<ProductResponse>> list(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "status", required = false) String status,
            Pageable pageable) {
        AdminAccess.requireAdmin();
        return ApiResponse.success(productService.adminList(keyword, parseOptionalStatus(status), pageable));
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductResponse> detail(@PathVariable("productId") String productId) {
        AdminAccess.requireAdmin();
        return ApiResponse.success(productService.detail(productId));
    }

    @PostMapping
    public ApiResponse<ProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
        AdminAccess.requireAdmin();
        return ApiResponse.success(productService.create(request));
    }

    @PutMapping("/{productId}")
    public ApiResponse<ProductResponse> update(
            @PathVariable("productId") String productId,
            @Valid @RequestBody UpdateProductRequest request) {
        AdminAccess.requireAdmin();
        return ApiResponse.success(productService.update(productId, request));
    }

    @PutMapping("/{productId}/status")
    public ApiResponse<ProductResponse> updateStatus(
            @PathVariable("productId") String productId,
            @Valid @RequestBody UpdateProductStatusRequest request) {
        AdminAccess.requireAdmin();
        return ApiResponse.success(productService.updateStatus(productId, parseRequiredStatus(request.status())));
    }

    private ProductStatus parseOptionalStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        return parseRequiredStatus(status);
    }

    private ProductStatus parseRequiredStatus(String status) {
        try {
            return ProductStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid status", exception);
        }
    }
}
