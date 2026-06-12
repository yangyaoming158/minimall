package com.minimall.inventory.dto;

public record AiDailyReportSuggestionSummaryResponse(
        long generatedSuggestions,
        long rejectedSuggestions,
        long convertedDrafts) {
}
