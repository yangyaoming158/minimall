package com.minimall.product.repository;

import com.minimall.product.domain.Product;
import com.minimall.product.domain.ProductStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByProductId(String productId);

    boolean existsByProductId(String productId);

    Page<Product> findByStatus(ProductStatus status, Pageable pageable);
}
