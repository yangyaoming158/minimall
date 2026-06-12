package com.minimall.inventory.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record AiDailyInventoryReportResponse(
        LocalDate reportDate,
        LocalDateTime generatedAt,
        LocalDateTime windowFrom,
        LocalDateTime windowTo,
        long lowStockCount,
        int hotProductDays,
        int hotProductLimit,
        List<AiInventorySalesItemEvidence> hotProducts,
        AiDailyReportSuggestionSummaryResponse suggestions,
        AiDailyReportInboundSummaryResponse inboundOrders,
        List<String> limitations) {

    public AiDailyInventoryReportResponse {
        hotProducts = List.copyOf(hotProducts == null ? List.of() : hotProducts);
        limitations = List.copyOf(limitations == null ? List.of() : limitations);
    }
}
