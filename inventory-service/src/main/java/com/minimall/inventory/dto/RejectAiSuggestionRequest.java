package com.minimall.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectAiSuggestionRequest(
        @NotBlank
        @Size(max = 512)
        String reason) {
}
