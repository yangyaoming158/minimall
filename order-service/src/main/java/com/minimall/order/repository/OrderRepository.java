package com.minimall.order.repository;

import com.minimall.order.domain.Order;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderNo(String orderNo);

    Optional<Order> findByOrderNoAndUserId(String orderNo, Long userId);

    Optional<Order> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    Page<Order> findByUserId(Long userId, Pageable pageable);
}