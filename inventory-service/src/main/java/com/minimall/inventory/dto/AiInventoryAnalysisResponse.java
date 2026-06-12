package com.minimall.inventory.dto;

import com.minimall.inventory.ai.validation.AiAnalysisType;
import java.time.LocalDateTime;
import java.util.List;

public record AiInventoryAnalysisResponse(
        AiAnalysisType analysisType,
        String summary,
        LocalDateTime queryTime,
        AiInventorySalesEvidenceResponse evidence,
        List<AiInventoryAnalysisItemResponse> items,
        List<String> limitations) {

    public AiInventoryAnalysisResponse {
        items = List.copyOf(items == null ? List.of() : items);
        limitations = List.copyOf(limitations == null ? List.of() : limitations);
    }
}
