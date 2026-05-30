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

    @Query(
            value = """
                    select o.productId as productId,
                           sum(o.quantity) as quantitySold,
                           count(o.id) as orderCount,
                           sum(o.totalAmount) as totalAmount
                      from Order o
                     where (:productId is null or o.productId = :productId)
                       and (:status is null or o.status = :status)
                       and (:createdFrom is null or o.createdAt >= :createdFrom)
                       and (:createdTo is null or o.createdAt <= :createdTo)
                     group by o.productId
                     order by sum(o.totalAmount) desc, sum(o.quantity) desc, o.productId asc
                    """,
            countQuery = """
                    select count(distinct o.productId)
                      from Order o
                     where (:productId is null or o.productId = :productId)
                       and (:status is null or o.status = :status)
                       and (:createdFrom is null or o.createdAt >= :createdFrom)
                       and (:createdTo is null or o.createdAt <= :createdTo)
                    """)
    Page<ProductSalesAggregation> aggregateProductSales(
            @Param("productId") String productId,
            @Param("status") OrderStatus status,
            @Param("createdFrom") LocalDateTime createdFrom,
            @Param("createdTo") LocalDateTime createdTo,
            Pageable pageable);

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
