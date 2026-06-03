package com.minimall.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record CreateInboundOrderDraftRequest(
        @NotEmpty(message = "items must not be empty")
        List<@Valid CreateInboundOrderDraftItemRequest> items) {
}
