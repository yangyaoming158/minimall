package com.minimall.inventory.repository;

import com.minimall.inventory.domain.InventoryChangeType;
import com.minimall.inventory.domain.InventoryRecord;
import com.minimall.inventory.domain.InventoryRecordSourceType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryRecordRepository extends JpaRepository<InventoryRecord, Long> {

    Optional<InventoryRecord> findByOrderNoAndChangeType(String orderNo, InventoryChangeType changeType);

    Optional<InventoryRecord> findBySourceTypeAndRequestId(
            InventoryRecordSourceType sourceType, String requestId);

    boolean existsByOrderNoAndChangeType(String orderNo, InventoryChangeType changeType);

    boolean existsBySourceTypeAndRequestId(InventoryRecordSourceType sourceType, String requestId);

    List<InventoryRecord> findByProductId(String productId);

    List<InventoryRecord> findByProductIdOrderByCreatedAtDescIdDesc(String productId);
}
