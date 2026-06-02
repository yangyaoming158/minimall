package com.minimall.inventory.service;

import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.common.core.response.PageResponse;
import com.minimall.inventory.domain.Inventory;
import com.minimall.inventory.domain.InventoryChangeType;
import com.minimall.inventory.domain.InventoryRecord;
import com.minimall.inventory.domain.InventoryRecordSourceType;
import com.minimall.inventory.domain.InventoryStatus;
import com.minimall.inventory.domain.StockState;
import com.minimall.inventory.dto.AdminInventoryResponse;
import com.minimall.inventory.dto.InventoryRecordResponse;
import com.minimall.inventory.dto.InventoryResponse;
import com.minimall.inventory.dto.InventoryTrendResponse;
import com.minimall.inventory.repository.InventoryRecordRepository;
import com.minimall.inventory.repository.InventoryRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class InventoryQueryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryRecordRepository inventoryRecordRepository;

    public InventoryQueryService(
            InventoryRepository inventoryRepository,
            InventoryRecordRepository inventoryRecordRepository) {
        this.inventoryRepository = inventoryRepository;
        this.inventoryRecordRepository = inventoryRecordRepository;
    }

    @Transactional(readOnly = true)
    public InventoryResponse detail(String productId) {
        return toResponse(getInventory(productId));
    }

    @Transactional(readOnly = true)
    public AdminInventoryResponse adminDetail(String productId) {
        return AdminInventoryResponse.from(getInventory(productId));
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminInventoryResponse> adminList(
            String keyword, StockState stockState, Boolean lowStock, Pageable pageable) {
        Specification<Inventory> specification = adminInventorySpecification(keyword, stockState, lowStock);
        return PageResponse.from(
                inventoryRepository.findAll(specification, pageable).map(AdminInventoryResponse::from));
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminInventoryResponse> lowStock(Pageable pageable) {
        return PageResponse.from(inventoryRepository.findLowStock(InventoryStatus.ACTIVE, pageable)
                .map(AdminInventoryResponse::from));
    }

    @Transactional(readOnly = true)
    public List<InventoryRecordResponse> records(String productId) {
        getInventory(productId);
        return inventoryRecordRepository.findByProductIdOrderByCreatedAtDescIdDesc(productId).stream()
                .map(InventoryRecordResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryTrendResponse> inventoryTrends(
            LocalDateTime createdFrom,
            LocalDateTime createdTo,
            Pageable pageable) {
        validateCreatedRange(createdFrom, createdTo);

        List<InventoryRecord> records = inventoryRecordRepository.findTrendRecords(createdFrom, createdTo);
        if (records.isEmpty()) {
            return page(List.of(), pageable);
        }

        Set<String> productIds = records.stream()
                .map(InventoryRecord::getProductId)
                .collect(Collectors.toSet());
        Map<String, Integer> currentStockByProductId = inventoryRepository.findByProductIdIn(productIds).stream()
                .collect(Collectors.toMap(Inventory::getProductId, Inventory::getAvailableStock));

        LocalDate earliestBucketDate = records.stream()
                .map(record -> record.getCreatedAt().toLocalDate())
                .min(LocalDate::compareTo)
                .orElseThrow();
        LocalDateTime earliestFutureStart = earliestBucketDate.plusDays(1).atStartOfDay();
        List<InventoryRecord> futureRecords =
                inventoryRecordRepository
                        .findByProductIdInAndCreatedAtGreaterThanEqualOrderByProductIdAscCreatedAtAscIdAsc(
                                productIds, earliestFutureStart);

        Map<TrendBucketKey, TrendAccumulator> buckets = new LinkedHashMap<>();
        records.stream()
                .sorted(Comparator
                        .comparing(InventoryRecord::getProductId)
                        .thenComparing(record -> record.getCreatedAt().toLocalDate()))
                .forEach(record -> buckets
                        .computeIfAbsent(
                                new TrendBucketKey(record.getProductId(), record.getCreatedAt().toLocalDate()),
                                ignored -> new TrendAccumulator())
                        .add(record));

        List<InventoryTrendResponse> content = buckets.entrySet().stream()
                .map(entry -> toTrendResponse(
                        entry.getKey(), entry.getValue(), currentStockByProductId, futureRecords))
                .toList();

        return page(content, pageable);
    }

    private Inventory getInventory(String productId) {
        return inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Inventory not found"));
    }

    private InventoryResponse toResponse(Inventory inventory) {
        return new InventoryResponse(
                inventory.getProductId(),
                inventory.getAvailableStock(),
                inventory.getLockedStock(),
                inventory.stockState());
    }

    private InventoryTrendResponse toTrendResponse(
            TrendBucketKey key,
            TrendAccumulator accumulator,
            Map<String, Integer> currentStockByProductId,
            List<InventoryRecord> futureRecords) {
        LocalDateTime nextBucketStart = key.bucketDate().plusDays(1).atStartOfDay();
        long futureDelta = futureRecords.stream()
                .filter(record -> key.productId().equals(record.getProductId()))
                .filter(record -> !record.getCreatedAt().isBefore(nextBucketStart))
                .mapToLong(this::availableStockDelta)
                .sum();
        long currentStock = currentStockByProductId.getOrDefault(key.productId(), 0);
        return new InventoryTrendResponse(
                key.productId(),
                key.bucketDate(),
                accumulator.inboundQuantity(),
                accumulator.outboundQuantity(),
                accumulator.adjustmentQuantity(),
                currentStock - futureDelta);
    }

    private PageResponse<InventoryTrendResponse> page(List<InventoryTrendResponse> content, Pageable pageable) {
        if (pageable.isUnpaged()) {
            return new PageResponse<>(content, 0, content.size(), content.size(), content.isEmpty() ? 0 : 1);
        }
        int start = Math.toIntExact(Math.min(pageable.getOffset(), content.size()));
        int end = Math.min(start + pageable.getPageSize(), content.size());
        return PageResponse.from(new PageImpl<>(content.subList(start, end), pageable, content.size()));
    }

    private void validateCreatedRange(LocalDateTime createdFrom, LocalDateTime createdTo) {
        if (createdFrom != null && createdTo != null && createdFrom.isAfter(createdTo)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "createdFrom must be before or equal to createdTo");
        }
    }

    private long inboundQuantity(InventoryRecord record) {
        if (record.getChangeType() == InventoryChangeType.RELEASE) {
            return record.getQuantity();
        }
        if (record.getChangeType() == InventoryChangeType.ADJUST_INCREASE
                && record.getSourceType() == InventoryRecordSourceType.INBOUND_ORDER) {
            return record.getQuantity();
        }
        return 0L;
    }

    private long outboundQuantity(InventoryRecord record) {
        return record.getChangeType() == InventoryChangeType.DEDUCT ? record.getQuantity() : 0L;
    }

    private long adjustmentQuantity(InventoryRecord record) {
        if (record.getSourceType() == InventoryRecordSourceType.INBOUND_ORDER) {
            return 0L;
        }
        if (record.getChangeType() == InventoryChangeType.ADJUST_INCREASE) {
            return record.getQuantity();
        }
        if (record.getChangeType() == InventoryChangeType.ADJUST_DECREASE) {
            return -record.getQuantity();
        }
        return 0L;
    }

    private long availableStockDelta(InventoryRecord record) {
        return switch (record.getChangeType()) {
            case DEDUCT, ADJUST_DECREASE -> -record.getQuantity();
            case RELEASE, ADJUST_INCREASE -> record.getQuantity();
        };
    }

    private Specification<Inventory> adminInventorySpecification(
            String keyword, StockState stockState, Boolean lowStock) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(keyword)) {
                predicates.add(criteriaBuilder.like(root.get("productId"), "%" + keyword.trim() + "%"));
            }
            if (stockState != null) {
                switch (stockState) {
                    case IN_STOCK -> {
                        predicates.add(criteriaBuilder.equal(root.get("status"), InventoryStatus.ACTIVE));
                        predicates.add(criteriaBuilder.greaterThan(root.get("availableStock"), 0));
                    }
                    case OUT_OF_STOCK -> {
                        predicates.add(criteriaBuilder.equal(root.get("status"), InventoryStatus.ACTIVE));
                        predicates.add(criteriaBuilder.equal(root.get("availableStock"), 0));
                    }
                    case INACTIVE ->
                        predicates.add(criteriaBuilder.equal(root.get("status"), InventoryStatus.INACTIVE));
                }
            }
            if (Boolean.TRUE.equals(lowStock)) {
                predicates.add(criteriaBuilder.equal(root.get("status"), InventoryStatus.ACTIVE));
                predicates.add(criteriaBuilder.greaterThan(root.get("safetyStock"), 0));
                predicates.add(criteriaBuilder.lessThanOrEqualTo(
                        root.get("availableStock"), root.get("safetyStock")));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private record TrendBucketKey(String productId, LocalDate bucketDate) {
    }

    private final class TrendAccumulator {

        private long inboundQuantity;
        private long outboundQuantity;
        private long adjustmentQuantity;

        private void add(InventoryRecord record) {
            inboundQuantity += InventoryQueryService.this.inboundQuantity(record);
            outboundQuantity += InventoryQueryService.this.outboundQuantity(record);
            adjustmentQuantity += InventoryQueryService.this.adjustmentQuantity(record);
        }

        private long inboundQuantity() {
            return inboundQuantity;
        }

        private long outboundQuantity() {
            return outboundQuantity;
        }

        private long adjustmentQuantity() {
            return adjustmentQuantity;
        }
    }
}
