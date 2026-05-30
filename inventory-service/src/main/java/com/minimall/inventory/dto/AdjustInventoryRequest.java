package com.minimall.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdjustInventoryRequest(
        @NotNull(message = "delta must not be null")
        Integer delta,

        @NotBlank(message = "reason must not be blank")
        @Size(max = 512, message = "reason length must be at most 512")
        String reason,

        @NotBlank(message = "requestId must not be blank")
        @Size(max = 128, message = "requestId length must be at most 128")
        String requestId) {
}
