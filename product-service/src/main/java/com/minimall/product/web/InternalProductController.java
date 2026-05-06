package com.minimall.product.web;

import com.minimall.common.core.response.ApiResponse;
import com.minimall.product.dto.InternalProductResponse;
import com.minimall.product.service.ProductService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/products")
public class InternalProductController {

    private final ProductService productService;

    public InternalProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/{productId}")
    public ApiResponse<InternalProductResponse> detail(@PathVariable("productId") String productId) {
        return ApiResponse.success(productService.internalDetail(productId));
    }
}
