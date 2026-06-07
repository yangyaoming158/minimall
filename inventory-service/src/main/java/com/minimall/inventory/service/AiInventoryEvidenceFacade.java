package com.minimall.inventory.service;

import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.inventory.domain.AiInventoryEvidenceType;
import com.minimall.inventory.domain.Inventory;
import com.minimall.inventory.domain.InventoryRecord;
import com.minimall.inventory.domain.InventoryStatus;
import com.minimall.inventory.dto.AiInventoryEvidenceResponse;
import com.minimall.inventory.dto.AiInventoryItemEvidence;
import com.minimall.inventory.dto.AiInventoryRecordEvidence;
import com.minimall.inventory.repository.InventoryRecordRepository;
import com.minimall.inventory.repository.InventoryRepository;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiInventoryEvidenceFacade {

    static final int MAX_INVENTORY_CANDIDATES = 100;
    static final int MAX_RECORDS_PER_PRODUCT = 20;

    private final InventoryRepository inventoryRepository;
    private final InventoryRecordRepository inventoryRecordRepository;

    public AiInventoryEvidenceFacade(
            InventoryRepository inventoryRepository,
            InventoryRecordRepository inventoryRecordRepository) {
        this.inventoryRepository = inventoryRepository;
        this.inventoryRecordRepository = inventoryRecordRepository;
    }

    @Transactional(readOnly = true)
    public AiInventoryEvidenceResponse currentInventory(String productId, int recordLimit) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Inventory not found"));
        List<InventoryRecord> records = recentRecords(inventory.getProductId(), recordLimit);
        return response(AiInventoryEvidenceType.CURRENT_INVENTORY, List.of(inventory), records);
    }

    @Transactional(readOnly = true)
    public AiInventoryEvidenceResponse lowStockCandidates(int limit, int recordLimit) {
        List<Inventory> inventories = inventoryRepository
                .findLowStock(InventoryStatus.ACTIVE, PageRequest.of(0, bounded(limit, 1, MAX_INVENTORY_CANDIDATES)))
                .getContent();
        List<InventoryRecord> records = inventories.stream()
                .flatMap(inventory -> recentRecords(inventory.getProductId(), recordLimit).stream())
                .toList();
        return response(AiInventoryEvidenceType.LOW_STOCK_CANDIDATES, inventories, records);
    }

    private List<InventoryRecord> recentRecords(String productId, int recordLimit) {
        int limit = bounded(recordLimit, 0, MAX_RECORDS_PER_PRODUCT);
        if (limit == 0) {
            return List.of();
        }
        return inventoryRecordRepository.findByProductIdOrderByCreatedAtDescIdDesc(productId).stream()
                .limit(limit)
                .toList();
    }

    private AiInventoryEvidenceResponse response(
            AiInventoryEvidenceType type,
            List<Inventory> inventories,
            List<InventoryRecord> records) {
        LocalDateTime generatedAt = LocalDateTime.now();
        TimeRange range = timeRange(inventories, records, generatedAt);
        return new AiInventoryEvidenceResponse(
                type,
                generatedAt,
                range.from(),
                range.to(),
                inventories.stream().map(AiInventoryItemEvidence::from).toList(),
                records.stream().map(AiInventoryRecordEvidence::from).toList());
    }

    private TimeRange timeRange(List<Inventory> inventories, List<InventoryRecord> records, LocalDateTime fallback) {
        List<LocalDateTime> timestamps = Stream.concat(
                        inventories.stream().flatMap(inventory -> Stream.of(
                                inventory.getCreatedAt(), inventory.getUpdatedAt())),
                        records.stream().flatMap(record -> Stream.of(record.getCreatedAt(), record.getUpdatedAt())))
                .filter(Objects::nonNull)
                .toList();
        if (timestamps.isEmpty()) {
            return new TimeRange(fallback, fallback);
        }
        return new TimeRange(
                timestamps.stream().min(Comparator.naturalOrder()).orElse(fallback),
                timestamps.stream().max(Comparator.naturalOrder()).orElse(fallback));
    }

    private int bounded(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record TimeRange(LocalDateTime from, LocalDateTime to) {
    }
}
