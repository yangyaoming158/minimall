package com.minimall.inventory.dto;

import com.minimall.inventory.domain.AiOperationSuggestion;
import com.minimall.inventory.domain.AiOperationSuggestionItem;
import com.minimall.inventory.domain.AiOperationSuggestionSource;
import com.minimall.inventory.domain.AiOperationSuggestionStatus;
import com.minimall.inventory.domain.AiOperationSuggestionType;
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
