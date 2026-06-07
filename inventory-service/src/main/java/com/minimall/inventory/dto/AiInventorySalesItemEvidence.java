package com.minimall.inventory.dto;

import java.util.List;

public record AiInventorySalesItemEvidence(
        String productId,
        int rank,
        AiInventoryItemEvidence inventory,
        AiSalesEvidenceResponse sales,
        List<AiInventoryRecordEvidence> records,
        List<String> limitations) {

    public AiInventorySalesItemEvidence {
        records = List.copyOf(records == null ? List.of() : records);
        limitations = List.copyOf(limitations == null ? List.of() : limitations);
    }
}
