package com.minimall.inventory.dto;

import com.minimall.inventory.domain.AiOperationSuggestion;
import com.minimall.inventory.domain.AiOperationSuggestionItem;
import com.minimall.inventory.domain.AiOperationSuggestionSource;
import com.minimall.inventory.domain.AiOperationSuggestionStatus;
import com.minimall.inventory.domain.AiOperationSuggestionType;
import com.minimall.inventory.domain.AiSuggestionValidationStatus;
import java.time.LocalDateTime;
import java.util.List;

public record AiSuggestionResponse(
        String suggestionNo,
        AiOperationSuggestionType type,
        AiOperationSuggestionStatus status,
        AiOperationSuggestionSource source,
        String reason,
        String inputSnapshotRef,
        String inputSummary,
        String modelProvider,
        String modelName,
        String promptVersion,
        String outputSchemaVersion,
        AiSuggestionValidationStatus validationStatus,
        String validationError,
        String inputSnapshotJson,
        String validatedOutputJson,
        String rawModelOutputJson,
        String linkedInboundNo,
        String rejectedReason,
        Long reviewedByAdminUserId,
        String reviewedByAdminUsername,
        LocalDateTime reviewedAt,
        int itemCount,
        int totalSuggestedQuantity,
        List<AiSuggestionItemResponse> items,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static AiSuggestionResponse from(AiOperationSuggestion suggestion, List<AiOperationSuggestionItem> items) {
        return from(suggestion, items, false);
    }

    public static AiSuggestionResponse detailFrom(
            AiOperationSuggestion suggestion,
            List<AiOperationSuggestionItem> items) {
        return from(suggestion, items, true);
    }

    private static AiSuggestionResponse from(
            AiOperationSuggestion suggestion,
            List<AiOperationSuggestionItem> items,
            boolean includeSnapshotJson) {
        List<AiSuggestionItemResponse> itemResponses = items.stream()
                .map(AiSuggestionItemResponse::from)
                .toList();
        int totalSuggestedQuantity = items.stream()
                .mapToInt(AiOperationSuggestionItem::getSuggestedQuantity)
                .sum();
        return new AiSuggestionResponse(
                suggestion.getSuggestionNo(),
                suggestion.getType(),
                suggestion.getStatus(),
                suggestion.getSource(),
                suggestion.getReason(),
                suggestion.getInputSnapshotRef(),
                suggestion.getInputSummary(),
                suggestion.getModelProvider(),
                suggestion.getModelName(),
                suggestion.getPromptVersion(),
                suggestion.getOutputSchemaVersion(),
                suggestion.getValidationStatus(),
                suggestion.getValidationError(),
                includeSnapshotJson ? suggestion.getInputSnapshotJson() : null,
                includeSnapshotJson ? suggestion.getValidatedOutputJson() : null,
                includeSnapshotJson ? suggestion.getRawModelOutputJson() : null,
                suggestion.getLinkedInboundNo(),
                suggestion.getRejectedReason(),
                suggestion.getReviewedByAdminUserId(),
                suggestion.getReviewedByAdminUsername(),
                suggestion.getReviewedAt(),
                itemResponses.size(),
                totalSuggestedQuantity,
                itemResponses,
                suggestion.getCreatedAt(),
                suggestion.getUpdatedAt());
    }
}
