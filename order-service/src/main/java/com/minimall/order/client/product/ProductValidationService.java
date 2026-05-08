package com.minimall.order.client.product;

import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;
import org.springframework.stereotype.Service;

@Service
public class ProductValidationService {

    private static final String PRODUCT_OFF_SHELF_MESSAGE = "Product is off shelf";

    private final ProductClient productClient;

    public ProductValidationService(ProductClient productClient) {
        this.productClient = productClient;
    }

    public ProductSnapshot validateSellable(String productId) {
        ProductSnapshot product = productClient.getProduct(productId);
        if (product.status() != ProductStatus.ON_SHELF) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, PRODUCT_OFF_SHELF_MESSAGE);
        }
        return product;
    }
}
