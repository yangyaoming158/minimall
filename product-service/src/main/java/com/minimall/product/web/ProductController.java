package com.minimall.product.web;

import com.minimall.common.core.response.ApiResponse;
import com.minimall.common.core.response.PageResponse;
import com.minimall.product.domain.ProductStatus;
import com.minimall.product.dto.CreateProductRequest;
import com.minimall.product.dto.ProductResponse;
import com.minimall.product.dto.UpdateProductRequest;
import com.minimall.product.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    public ApiResponse<ProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
        return ApiResponse.success(productService.create(request));
    }

    @PutMapping("/{productId}")
    public ApiResponse<ProductResponse> update(
            @PathVariable("productId") String productId,
            @Valid @RequestBody UpdateProductRequest request) {
        return ApiResponse.success(productService.update(productId, request));
    }

    @GetMapping
    public ApiResponse<PageResponse<ProductResponse>> list(
            @RequestParam(name = "status", required = false) ProductStatus status,
            Pageable pageable) {
        return ApiResponse.success(productService.list(status, pageable));
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductResponse> detail(@PathVariable("productId") String productId) {
        return ApiResponse.success(productService.detail(productId));
    }

    @PostMapping("/{productId}/on-shelf")
    public ApiResponse<ProductResponse> onShelf(@PathVariable("productId") String productId) {
        return ApiResponse.success(productService.onShelf(productId));
    }

    @PostMapping("/{productId}/off-shelf")
    public ApiResponse<ProductResponse> offShelf(@PathVariable("productId") String productId) {
        return ApiResponse.success(productService.offShelf(productId));
    }
}
