package com.minimall.inventory.dto;

import com.minimall.inventory.domain.AiInventoryEvidenceType;
import java.time.LocalDateTime;
import java.util.List;

public record AiInventoryEvidenceResponse(
        AiInventoryEvidenceType evidenceType,
        LocalDateTime generatedAt,
        LocalDateTime dataFrom,
        LocalDateTime dataTo,
        List<AiInventoryItemEvidence> inventories,
        List<AiInventoryRecordEvidence> records) {

    public AiInventoryEvidenceResponse {
        inventories = List.copyOf(inventories == null ? List.of() : inventories);
        records = List.copyOf(records == null ? List.of() : records);
    }
}
