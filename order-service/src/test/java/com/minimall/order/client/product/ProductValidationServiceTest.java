package com.minimall.order.client.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ProductValidationServiceTest {

    private final ProductClient productClient = mock(ProductClient.class);
    private final ProductValidationService validationService = new ProductValidationService(productClient);

    @Test
    void validateSellableReturnsOnShelfProduct() {
        ProductSnapshot product = new ProductSnapshot(
                "SKU-3002",
                "Mouse",
                new BigDecimal("49.90"),
                ProductStatus.ON_SHELF);
        given(productClient.getProduct("SKU-3002")).willReturn(product);

        ProductSnapshot result = validationService.validateSellable("SKU-3002");

        assertThat(result).isSameAs(product);
    }

    @Test
    void validateSellableRejectsOffShelfProduct() {
        given(productClient.getProduct("SKU-3003")).willReturn(new ProductSnapshot(
                "SKU-3003",
                "Display",
                new BigDecimal("899.00"),
                ProductStatus.OFF_SHELF));

        assertThatThrownBy(() -> validationService.validateSellable("SKU-3003"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
                    assertThat(exception.getMessage()).isEqualTo("Product is off shelf");
                });
    }
}
