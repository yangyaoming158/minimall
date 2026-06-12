package com.minimall.inventory.dto;

import com.minimall.inventory.domain.AiInventoryQuestionIntent;
import java.time.LocalDateTime;
import java.util.List;

public record AiInventoryAskResponse(
        AiInventoryQuestionIntent intent,
        boolean supported,
        String answer,
        LocalDateTime queryTime,
        AiInventoryEvidenceResponse evidence,
        List<String> limitations) {

    public AiInventoryAskResponse {
        limitations = List.copyOf(limitations == null ? List.of() : limitations);
    }
}
