package com.minimall.inventory.repository;

import com.minimall.inventory.domain.InventoryChangeType;
import com.minimall.inventory.domain.InventoryRecord;
import com.minimall.inventory.domain.InventoryRecordSourceType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryRecordRepository extends JpaRepository<InventoryRecord, Long> {

    Optional<InventoryRecord> findByOrderNoAndChangeType(String orderNo, InventoryChangeType changeType);

    Optional<InventoryRecord> findBySourceTypeAndRequestId(
            InventoryRecordSourceType sourceType, String requestId);

    boolean existsByOrderNoAndChangeType(String orderNo, InventoryChangeType changeType);

    boolean existsBySourceTypeAndRequestId(InventoryRecordSourceType sourceType, String requestId);

    List<InventoryRecord> findByProductId(String productId);

    List<InventoryRecord> findByProductIdOrderByCreatedAtDescIdDesc(String productId);

    @Query("""
            select record from InventoryRecord record
             where (:createdFrom is null or record.createdAt >= :createdFrom)
               and (:createdTo is null or record.createdAt <= :createdTo)
             order by record.productId asc, record.createdAt asc, record.id asc
            """)
    List<InventoryRecord> findTrendRecords(
            @Param("createdFrom") LocalDateTime createdFrom,
            @Param("createdTo") LocalDateTime createdTo);

    List<InventoryRecord> findByProductIdInAndCreatedAtGreaterThanEqualOrderByProductIdAscCreatedAtAscIdAsc(
            Collection<String> productIds, LocalDateTime createdAt);
}
