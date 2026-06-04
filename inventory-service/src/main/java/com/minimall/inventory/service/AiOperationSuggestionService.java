package com.minimall.inventory.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minimall.common.core.audit.AdminAuditAction;
import com.minimall.common.core.audit.AdminAuditLogWriteRequest;
import com.minimall.common.core.audit.AdminAuditResourceType;
import com.minimall.common.core.audit.AdminAuditSourceType;
import com.minimall.common.core.audit.AdminAuditWriter;
import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.common.core.response.PageResponse;
import com.minimall.inventory.domain.AiOperationSuggestion;
import com.minimall.inventory.domain.AiOperationSuggestionItem;
import com.minimall.inventory.domain.AiOperationSuggestionStatus;
import com.minimall.inventory.dto.AiSuggestionResponse;
import com.minimall.inventory.dto.CreateInboundOrderDraftItemRequest;
import com.minimall.inventory.dto.InboundOrderResponse;
import com.minimall.inventory.dto.RejectAiSuggestionRequest;
import com.minimall.inventory.repository.AiOperationSuggestionItemRepository;
import com.minimall.inventory.repository.AiOperationSuggestionRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiOperationSuggestionService {

    private final AiOperationSuggestionRepository suggestionRepository;
    private final AiOperationSuggestionItemRepository itemRepository;
    private final InboundOrderDraftService inboundOrderDraftService;
    private final AdminAuditWriter adminAuditWriter;
    private final ObjectMapper objectMapper;

    public AiOperationSuggestionService(
            AiOperationSuggestionRepository suggestionRepository,
            AiOperationSuggestionItemRepository itemRepository,
            InboundOrderDraftService inboundOrderDraftService,
            AdminAuditWriter adminAuditWriter,
            ObjectMapper objectMapper) {
        this.suggestionRepository = suggestionRepository;
        this.itemRepository = itemRepository;
        this.inboundOrderDraftService = inboundOrderDraftService;
        this.adminAuditWriter = adminAuditWriter;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<AiSuggestionResponse> list(
            AiOperationSuggestionStatus status, Pageable pageable) {
        Page<AiOperationSuggestion> suggestions = status == null
                ? suggestionRepository.findAll(defaultSort(pageable))
                : suggestionRepository.findByStatus(status, defaultSort(pageable));
        Map<String, List<AiOperationSuggestionItem>> itemsBySuggestionNo =
                itemsBySuggestionNo(suggestions.getContent());
        return PageResponse.from(suggestions.map(suggestion -> AiSuggestionResponse.from(
                suggestion,
                itemsBySuggestionNo.getOrDefault(suggestion.getSuggestionNo(), List.of()))));
    }

    @Transactional(readOnly = true)
    public AiSuggestionResponse detail(String suggestionNo) {
        AiOperationSuggestion suggestion = getBySuggestionNo(suggestionNo);
        return response(suggestion);
    }

    @Transactional
    public AiSuggestionResponse reject(
            String suggestionNo,
            RejectAiSuggestionRequest request,
            InventoryAdminAuditContext auditContext) {
        AiOperationSuggestion suggestion = getBySuggestionNo(suggestionNo);
        if (suggestion.getStatus() == AiOperationSuggestionStatus.REJECTED) {
            return response(suggestion);
        }
        if (suggestion.getStatus() != AiOperationSuggestionStatus.PENDING_REVIEW) {
            throw new BusinessException(ErrorCode.CONFLICT, "Only pending AI suggestions can be rejected");
        }

        List<AiOperationSuggestionItem> items =
                itemRepository.findBySuggestionNoOrderByIdAsc(suggestion.getSuggestionNo());
        AiSuggestionResponse before = AiSuggestionResponse.from(suggestion, items);
        suggestion.reject(request.reason(), auditContext.adminUserId(), auditContext.adminUsername());
        AiOperationSuggestion saved = suggestionRepository.saveAndFlush(suggestion);
        AiSuggestionResponse after = AiSuggestionResponse.from(saved, items);
        writeRejectAudit(auditContext, saved.getSuggestionNo(), before, after);
        return after;
    }

    @Transactional
    public AiSuggestionResponse convertToInboundDraft(
            String suggestionNo,
            InventoryAdminAuditContext auditContext) {
        AiOperationSuggestion suggestion = getBySuggestionNo(suggestionNo);
        if (suggestion.getStatus() == AiOperationSuggestionStatus.CONVERTED_TO_DRAFT) {
            return response(suggestion);
        }
        if (suggestion.getStatus() != AiOperationSuggestionStatus.PENDING_REVIEW) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "Only pending AI suggestions can be converted to inbound draft");
        }

        List<AiOperationSuggestionItem> items =
                itemRepository.findBySuggestionNoOrderByIdAsc(suggestion.getSuggestionNo());
        if (items.isEmpty()) {
            throw new BusinessException(ErrorCode.CONFLICT, "AI suggestion has no items to convert");
        }
        InboundOrderResponse inboundDraft = inboundOrderDraftService.createDraftFromAiSuggestion(
                suggestion.getSuggestionNo(), toInboundDraftItems(items), auditContext);
        suggestion.convertToDraft(
                inboundDraft.inboundNo(),
                auditContext.adminUserId(),
                auditContext.adminUsername());
        AiOperationSuggestion saved = suggestionRepository.saveAndFlush(suggestion);
        return AiSuggestionResponse.from(saved, items);
    }

    private AiOperationSuggestion getBySuggestionNo(String suggestionNo) {
        return suggestionRepository.findBySuggestionNo(suggestionNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "AI suggestion not found"));
    }

    private AiSuggestionResponse response(AiOperationSuggestion suggestion) {
        return AiSuggestionResponse.from(
                suggestion, itemRepository.findBySuggestionNoOrderByIdAsc(suggestion.getSuggestionNo()));
    }

    private Map<String, List<AiOperationSuggestionItem>> itemsBySuggestionNo(
            List<AiOperationSuggestion> suggestions) {
        if (suggestions.isEmpty()) {
            return Map.of();
        }
        List<String> suggestionNos = suggestions.stream()
                .map(AiOperationSuggestion::getSuggestionNo)
                .toList();
        return itemRepository.findBySuggestionNoInOrderBySuggestionNoAscIdAsc(suggestionNos).stream()
                .collect(Collectors.groupingBy(AiOperationSuggestionItem::getSuggestionNo));
    }

    private List<CreateInboundOrderDraftItemRequest> toInboundDraftItems(List<AiOperationSuggestionItem> items) {
        return items.stream()
                .map(item -> new CreateInboundOrderDraftItemRequest(
                        item.getProductId(),
                        item.getSuggestedQuantity()))
                .toList();
    }

    private void writeRejectAudit(
            InventoryAdminAuditContext auditContext,
            String suggestionNo,
            AiSuggestionResponse before,
            AiSuggestionResponse after) {
        adminAuditWriter.write(new AdminAuditLogWriteRequest(
                auditContext.adminUserId(),
                auditContext.adminUsername(),
                AdminAuditAction.AI_SUGGESTION_REJECT,
                AdminAuditResourceType.AI_SUGGESTION,
                suggestionNo,
                auditContext.requestId(),
                AdminAuditSourceType.AI_SUGGESTION,
                suggestionNo,
                toSnapshot(before),
                toSnapshot(after),
                auditContext.ip(),
                auditContext.userAgent(),
                "Reject AI suggestion " + suggestionNo + " (" + after.rejectedReason() + ")"));
    }

    private JsonNode toSnapshot(AiSuggestionResponse response) {
        return response == null ? null : objectMapper.valueToTree(response);
    }

    private Pageable defaultSort(Pageable pageable) {
        if (pageable.isUnpaged() || pageable.getSort().isSorted()) {
            return pageable;
        }
        return PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }
}
