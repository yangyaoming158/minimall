package com.minimall.inventory.service;

import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.common.core.response.PageResponse;
import com.minimall.inventory.client.order.SalesEvidenceClient;
import com.minimall.inventory.domain.AiInventoryEvidenceType;
import com.minimall.inventory.domain.Inventory;
import com.minimall.inventory.domain.InventoryRecord;
import com.minimall.inventory.domain.InventoryStatus;
import com.minimall.inventory.dto.AiInventoryEvidenceResponse;
import com.minimall.inventory.dto.AiInventoryItemEvidence;
import com.minimall.inventory.dto.AiInventoryRecordEvidence;
import com.minimall.inventory.dto.AiInventorySalesEvidenceResponse;
import com.minimall.inventory.dto.AiInventorySalesItemEvidence;
import com.minimall.inventory.dto.AiSalesEvidenceResponse;
import com.minimall.inventory.repository.InventoryRecordRepository;
import com.minimall.inventory.repository.InventoryRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiInventoryEvidenceFacade {

    static final int MAX_INVENTORY_CANDIDATES = 100;
    static final int MAX_RECORDS_PER_PRODUCT = 20;
    static final int DEFAULT_LOW_STOCK_SALES_DAYS = 7;
    static final int MAX_HOT_PRODUCTS = 100;

    private final InventoryRepository inventoryRepository;
    private final InventoryRecordRepository inventoryRecordRepository;
    private final SalesEvidenceClient salesEvidenceClient;

    public AiInventoryEvidenceFacade(
            InventoryRepository inventoryRepository,
            InventoryRecordRepository inventoryRecordRepository,
            SalesEvidenceClient salesEvidenceClient) {
        this.inventoryRepository = inventoryRepository;
        this.inventoryRecordRepository = inventoryRecordRepository;
        this.salesEvidenceClient = salesEvidenceClient;
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
        List<Inventory> inventories = lowStockInventoryPage(limit).getContent();
        List<InventoryRecord> records = inventories.stream()
                .flatMap(inventory -> recentRecords(inventory.getProductId(), recordLimit).stream())
                .toList();
        return response(AiInventoryEvidenceType.LOW_STOCK_CANDIDATES, inventories, records);
    }

    @Transactional(readOnly = true)
    public AiInventorySalesEvidenceResponse lowStockAnalysisEvidence(int limit, int recordLimit) {
        LocalDateTime generatedAt = LocalDateTime.now();
        EvidenceWindow window = evidenceWindow(DEFAULT_LOW_STOCK_SALES_DAYS, generatedAt);
        Page<Inventory> inventoryPage = lowStockInventoryPage(limit);
        int boundedRecordLimit = bounded(recordLimit, 0, MAX_RECORDS_PER_PRODUCT);
        List<AiInventorySalesItemEvidence> products = new ArrayList<>();
        int rank = 1;
        for (Inventory inventory : inventoryPage.getContent()) {
            AiSalesEvidenceResponse sales = salesEvidenceForProduct(
                    inventory.getProductId(), window.from(), window.to());
            List<AiInventoryRecordEvidence> records = recentRecords(inventory.getProductId(), boundedRecordLimit)
                    .stream()
                    .map(AiInventoryRecordEvidence::from)
                    .toList();
            products.add(new AiInventorySalesItemEvidence(
                    inventory.getProductId(),
                    rank++,
                    AiInventoryItemEvidence.from(inventory),
                    sales,
                    records,
                    itemLimitations(sales, records, inventory)));
        }
        return new AiInventorySalesEvidenceResponse(
                AiInventoryEvidenceType.LOW_STOCK_ANALYSIS,
                DEFAULT_LOW_STOCK_SALES_DAYS,
                generatedAt,
                window.from(),
                window.to(),
                boundedLimitations("Low-stock candidates", inventoryPage.getTotalElements(), products.size()),
                products);
    }

    @Transactional(readOnly = true)
    public AiInventorySalesEvidenceResponse hotProductsEvidence(int days, int limit, int recordLimit) {
        int supportedDays = supportedHotProductDays(days);
        LocalDateTime generatedAt = LocalDateTime.now();
        EvidenceWindow window = evidenceWindow(supportedDays, generatedAt);
        int boundedLimit = bounded(limit, 1, MAX_HOT_PRODUCTS);
        int boundedRecordLimit = bounded(recordLimit, 0, MAX_RECORDS_PER_PRODUCT);
        PageResponse<AiSalesEvidenceResponse> salesPage = salesEvidenceClient.salesByProduct(
                null, window.from(), window.to(), PageRequest.of(0, boundedLimit));
        List<AiSalesEvidenceResponse> salesEvidence = salesContent(salesPage);
        Map<String, Inventory> inventoryByProductId = inventoriesByProductId(salesEvidence);
        List<AiInventorySalesItemEvidence> products = new ArrayList<>();
        int rank = 1;
        for (AiSalesEvidenceResponse sales : salesEvidence) {
            Inventory inventory = inventoryByProductId.get(sales.productId());
            List<AiInventoryRecordEvidence> records = recentRecords(sales.productId(), boundedRecordLimit)
                    .stream()
                    .map(AiInventoryRecordEvidence::from)
                    .toList();
            products.add(new AiInventorySalesItemEvidence(
                    sales.productId(),
                    rank++,
                    inventory == null ? null : AiInventoryItemEvidence.from(inventory),
                    sales,
                    records,
                    itemLimitations(sales, records, inventory)));
        }
        return new AiInventorySalesEvidenceResponse(
                AiInventoryEvidenceType.HOT_PRODUCTS,
                supportedDays,
                generatedAt,
                window.from(),
                window.to(),
                boundedLimitations("Hot-product sales evidence", totalElements(salesPage), products.size()),
                products);
    }

    private Page<Inventory> lowStockInventoryPage(int limit) {
        return inventoryRepository.findLowStock(
                InventoryStatus.ACTIVE,
                PageRequest.of(0, bounded(limit, 1, MAX_INVENTORY_CANDIDATES)));
    }

    private AiSalesEvidenceResponse salesEvidenceForProduct(
            String productId,
            LocalDateTime createdFrom,
            LocalDateTime createdTo) {
        PageResponse<AiSalesEvidenceResponse> page = salesEvidenceClient.salesByProduct(
                productId, createdFrom, createdTo, PageRequest.of(0, 1));
        return salesContent(page).stream()
                .filter(sales -> productId.equals(sales.productId()))
                .findFirst()
                .orElseGet(() -> noSalesEvidence(productId));
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

    private Map<String, Inventory> inventoriesByProductId(List<AiSalesEvidenceResponse> salesEvidence) {
        List<String> productIds = salesEvidence.stream()
                .map(AiSalesEvidenceResponse::productId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (productIds.isEmpty()) {
            return Map.of();
        }
        Map<String, Inventory> inventories = new LinkedHashMap<>();
        inventoryRepository.findByProductIdIn(productIds).forEach(inventory ->
                inventories.put(inventory.getProductId(), inventory));
        return inventories;
    }

    private List<AiSalesEvidenceResponse> salesContent(PageResponse<AiSalesEvidenceResponse> page) {
        if (page == null || page.content() == null) {
            return List.of();
        }
        return page.content().stream()
                .filter(Objects::nonNull)
                .filter(sales -> sales.productId() != null)
                .toList();
    }

    private long totalElements(PageResponse<AiSalesEvidenceResponse> page) {
        return page == null ? 0 : page.totalElements();
    }

    private List<String> boundedLimitations(String evidenceName, long totalElements, int returnedElements) {
        List<String> limitations = new ArrayList<>();
        limitations.add("Sales evidence is limited to paid orders in the selected window.");
        limitations.add("Inventory record evidence is limited to recent records per product.");
        if (returnedElements == 0) {
            limitations.add(evidenceName + " is empty for the selected window.");
        }
        if (totalElements > returnedElements) {
            limitations.add(evidenceName + " is limited to the first " + returnedElements + " products.");
        }
        return limitations;
    }

    private List<String> itemLimitations(
            AiSalesEvidenceResponse sales,
            List<AiInventoryRecordEvidence> records,
            Inventory inventory) {
        List<String> limitations = new ArrayList<>();
        if (sales.soldQuantity() == 0 && sales.orderCount() == 0) {
            limitations.add("No paid sales evidence found in selected window.");
        }
        if (records.isEmpty()) {
            limitations.add("No recent inventory record evidence found.");
        }
        if (inventory == null) {
            limitations.add("Current inventory evidence is unavailable for this product.");
        }
        return limitations;
    }

    private AiSalesEvidenceResponse noSalesEvidence(String productId) {
        return new AiSalesEvidenceResponse(productId, 0, 0, BigDecimal.ZERO);
    }

    private EvidenceWindow evidenceWindow(int days, LocalDateTime generatedAt) {
        return new EvidenceWindow(generatedAt.minusDays(days), generatedAt);
    }

    private int supportedHotProductDays(int days) {
        if (days == 7 || days == 30) {
            return days;
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "hot product evidence days must be 7 or 30");
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

    private record EvidenceWindow(LocalDateTime from, LocalDateTime to) {
    }
}
