package com.minimall.inventory.dto;

import com.minimall.inventory.domain.AiInventoryEvidenceType;
import java.time.LocalDateTime;
import java.util.List;

public record AiInventorySalesEvidenceResponse(
        AiInventoryEvidenceType evidenceType,
        int days,
        LocalDateTime generatedAt,
        LocalDateTime dataFrom,
        LocalDateTime dataTo,
        List<String> limitations,
        List<AiInventorySalesItemEvidence> products) {

    public AiInventorySalesEvidenceResponse {
        limitations = List.copyOf(limitations == null ? List.of() : limitations);
        products = List.copyOf(products == null ? List.of() : products);
    }
}
