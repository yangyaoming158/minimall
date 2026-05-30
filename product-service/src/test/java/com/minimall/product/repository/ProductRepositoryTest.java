package com.minimall.product.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minimall.product.domain.Product;
import com.minimall.product.domain.ProductStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @Test
    void savesProductAndFindsByProductId() {
        Product saved = productRepository.saveAndFlush(
                new Product(
                        "SKU-1001",
                        "Wireless Mouse",
                        "Quiet wireless mouse",
                        "https://cdn.example.com/sku-1001.png",
                        new BigDecimal("39.90")));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(productRepository.findByProductId("SKU-1001"))
                .isPresent()
                .get()
                .satisfies(product -> {
                    assertThat(product.getName()).isEqualTo("Wireless Mouse");
                    assertThat(product.getImageUrl()).isEqualTo("https://cdn.example.com/sku-1001.png");
                    assertThat(product.getPrice()).isEqualByComparingTo("39.90");
                    assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SHELF);
                });
        assertThat(productRepository.existsByProductId("SKU-1001")).isTrue();
    }

    @Test
    void persistsStatusAsEnumValue() {
        Product product = new Product("SKU-1002", "Desk Lamp", null, new BigDecimal("79.00"));
        product.setStatus(ProductStatus.OFF_SHELF);
        productRepository.saveAndFlush(product);

        assertThat(productRepository.findByProductId("SKU-1002"))
                .isPresent()
                .get()
                .extracting(Product::getStatus)
                .isEqualTo(ProductStatus.OFF_SHELF);
    }

    @Test
    void normalizesBlankImageUrlToNull() {
        Product saved = productRepository.saveAndFlush(
                new Product("SKU-1003", "Notebook", null, " ", new BigDecimal("9.90")));

        assertThat(productRepository.findByProductId(saved.getProductId()))
                .isPresent()
                .get()
                .extracting(Product::getImageUrl)
                .isNull();
    }

    @Test
    void duplicateProductIdViolatesUniqueConstraint() {
        productRepository.saveAndFlush(new Product("SKU-DUP", "First", null, new BigDecimal("10.00")));

        assertThatThrownBy(() -> productRepository.saveAndFlush(
                new Product("SKU-DUP", "Second", null, new BigDecimal("12.00"))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
