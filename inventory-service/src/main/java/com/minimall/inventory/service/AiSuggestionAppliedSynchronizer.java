package com.minimall.inventory.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minimall.common.core.audit.AdminAuditAction;
import com.minimall.common.core.audit.AdminAuditLogWriteRequest;
import com.minimall.common.core.audit.AdminAuditResourceType;
import com.minimall.common.core.audit.AdminAuditSourceType;
import com.minimall.common.core.audit.AdminAuditWriter;
import com.minimall.inventory.domain.AiOperationSuggestion;
import com.minimall.inventory.domain.AiOperationSuggestionItem;
import com.minimall.inventory.domain.AiOperationSuggestionStatus;
import com.minimall.inventory.dto.AiSuggestionResponse;
import com.minimall.inventory.repository.AiOperationSuggestionItemRepository;
import com.minimall.inventory.repository.AiOperationSuggestionRepository;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Synchronizes AI suggestion status when a linked inbound order is applied.
 * Lives outside {@link AiOperationSuggestionService} because that service
 * depends on {@link InboundOrderDraftService} for draft conversion; the
 * inbound confirmation flow calling back through it would create a circular
 * bean dependency.
 */
@Component
public class AiSuggestionAppliedSynchronizer {

    private final AiOperationSuggestionRepository suggestionRepository;
    private final AiOperationSuggestionItemRepository itemRepository;
    private final AdminAuditWriter adminAuditWriter;
    private final ObjectMapper objectMapper;

    public AiSuggestionAppliedSynchronizer(
            AiOperationSuggestionRepository suggestionRepository,
            AiOperationSuggestionItemRepository itemRepository,
            AdminAuditWriter adminAuditWriter,
            ObjectMapper objectMapper) {
        this.suggestionRepository = suggestionRepository;
        this.itemRepository = itemRepository;
        this.adminAuditWriter = adminAuditWriter;
        this.objectMapper = objectMapper;
    }

    /**
     * Moves suggestions linked to the applied inbound order from
     * CONVERTED_TO_DRAFT to APPLIED. Runs inside the inbound confirmation
     * transaction; repeated confirmation never reaches this method because the
     * confirm flow short-circuits on requestId and non-DRAFT status, and an
     * already-APPLIED suggestion is skipped defensively anyway.
     */
    public void syncOnInboundApplied(String inboundNo, InventoryAdminAuditContext auditContext) {
        for (AiOperationSuggestion suggestion : suggestionRepository.findByLinkedInboundNo(inboundNo)) {
            if (suggestion.getStatus() != AiOperationSuggestionStatus.CONVERTED_TO_DRAFT) {
                continue;
            }
            List<AiOperationSuggestionItem> items =
                    itemRepository.findBySuggestionNoOrderByIdAsc(suggestion.getSuggestionNo());
            AiSuggestionResponse before = AiSuggestionResponse.from(suggestion, items);
            suggestion.markApplied();
            AiOperationSuggestion saved = suggestionRepository.saveAndFlush(suggestion);
            AiSuggestionResponse after = AiSuggestionResponse.from(saved, items);
            writeApplyAudit(auditContext, saved.getSuggestionNo(), inboundNo, before, after);
        }
    }

    private void writeApplyAudit(
            InventoryAdminAuditContext auditContext,
            String suggestionNo,
            String inboundNo,
            AiSuggestionResponse before,
            AiSuggestionResponse after) {
        adminAuditWriter.write(new AdminAuditLogWriteRequest(
                auditContext.adminUserId(),
                auditContext.adminUsername(),
                AdminAuditAction.AI_SUGGESTION_APPLY,
                AdminAuditResourceType.AI_SUGGESTION,
                suggestionNo,
                auditContext.requestId(),
                AdminAuditSourceType.AI_SUGGESTION,
                inboundNo,
                toSnapshot(before),
                toSnapshot(after),
                auditContext.ip(),
                auditContext.userAgent(),
                "AI suggestion " + suggestionNo + " applied via inbound order " + inboundNo));
    }

    private JsonNode toSnapshot(AiSuggestionResponse response) {
        return response == null ? null : objectMapper.valueToTree(response);
    }
}
