package com.minimall.inventory.repository;

import com.minimall.inventory.domain.InventoryChangeType;
import com.minimall.inventory.domain.InventoryRecord;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryRecordRepository extends JpaRepository<InventoryRecord, Long> {

    Optional<InventoryRecord> findByOrderNoAndChangeType(String orderNo, InventoryChangeType changeType);

    boolean existsByOrderNoAndChangeType(String orderNo, InventoryChangeType changeType);

    List<InventoryRecord> findByProductId(String productId);
}
