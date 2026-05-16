package com.minimall.order.repository;

import com.minimall.order.domain.Order;
import com.minimall.order.domain.OrderStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderNo(String orderNo);

    Optional<Order> findByOrderNoAndUserId(String orderNo, Long userId);

    Optional<Order> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    Page<Order> findByUserId(Long userId, Pageable pageable);

    @Query("""
            select o from Order o
            where o.status = :status
              and o.expireAt <= :now
            order by o.expireAt asc, o.id asc
            """)
    List<Order> findExpiredOrders(
            @Param("status") OrderStatus status,
            @Param("now") LocalDateTime now,
            Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Order o
               set o.status = :targetStatus,
                   o.closedAt = :closedAt,
                   o.updatedAt = :closedAt
             where o.id = :id
               and o.status = :expectedStatus
            """)
    int updateStatusIfCurrent(
            @Param("id") Long id,
            @Param("expectedStatus") OrderStatus expectedStatus,
            @Param("targetStatus") OrderStatus targetStatus,
            @Param("closedAt") LocalDateTime closedAt);
}
