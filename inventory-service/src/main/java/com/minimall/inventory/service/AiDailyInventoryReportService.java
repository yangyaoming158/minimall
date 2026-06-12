package com.minimall.inventory.service;

import com.minimall.inventory.domain.AiOperationSuggestionStatus;
import com.minimall.inventory.domain.InboundOrderStatus;
import com.minimall.inventory.domain.InventoryStatus;
import com.minimall.inventory.dto.AiDailyInventoryReportResponse;
import com.minimall.inventory.dto.AiDailyReportInboundSummaryResponse;
import com.minimall.inventory.dto.AiDailyReportSuggestionSummaryResponse;
import com.minimall.inventory.dto.AiInventorySalesEvidenceResponse;
import com.minimall.inventory.dto.AiInventorySalesItemEvidence;
import com.minimall.inventory.repository.AiOperationSuggestionRepository;
import com.minimall.inventory.repository.InboundOrderRepository;
import com.minimall.inventory.repository.InventoryRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiDailyInventoryReportService {

    static final int HOT_PRODUCT_DAYS = 7;
    static final int HOT_PRODUCT_LIMIT = 5;

    private final InventoryRepository inventoryRepository;
    private final AiOperationSuggestionRepository suggestionRepository;
    private final InboundOrderRepository inboundOrderRepository;
    private final AiInventoryEvidenceFacade evidenceFacade;

    public AiDailyInventoryReportService(
            InventoryRepository inventoryRepository,
            AiOperationSuggestionRepository suggestionRepository,
            InboundOrderRepository inboundOrderRepository,
            AiInventoryEvidenceFacade evidenceFacade) {
        this.inventoryRepository = inventoryRepository;
        this.suggestionRepository = suggestionRepository;
        this.inboundOrderRepository = inboundOrderRepository;
        this.evidenceFacade = evidenceFacade;
    }

    @Transactional(readOnly = true)
    public AiDailyInventoryReportResponse dailyReport() {
        LocalDateTime generatedAt = LocalDateTime.now();
        LocalDate reportDate = generatedAt.toLocalDate();
        LocalDateTime windowFrom = reportDate.atStartOfDay();
        LocalDateTime windowTo = generatedAt;
        AiInventorySalesEvidenceResponse hotProductsEvidence =
                evidenceFacade.hotProductsEvidence(HOT_PRODUCT_DAYS, HOT_PRODUCT_LIMIT, 0);

        return new AiDailyInventoryReportResponse(
                reportDate,
                generatedAt,
                windowFrom,
                windowTo,
                inventoryRepository.countLowStock(InventoryStatus.ACTIVE),
                HOT_PRODUCT_DAYS,
                HOT_PRODUCT_LIMIT,
                hotProducts(hotProductsEvidence),
                suggestionSummary(windowFrom, windowTo),
                inboundSummary(windowFrom, windowTo),
                limitations(hotProductsEvidence));
    }

    private List<AiInventorySalesItemEvidence> hotProducts(AiInventorySalesEvidenceResponse evidence) {
        return evidence == null ? List.of() : evidence.products();
    }

    private AiDailyReportSuggestionSummaryResponse suggestionSummary(
            LocalDateTime windowFrom,
            LocalDateTime windowTo) {
        return new AiDailyReportSuggestionSummaryResponse(
                suggestionRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(windowFrom, windowTo),
                suggestionRepository.countByStatusAndReviewedAtGreaterThanEqualAndReviewedAtLessThan(
                        AiOperationSuggestionStatus.REJECTED, windowFrom, windowTo),
                suggestionRepository.countByStatusAndReviewedAtGreaterThanEqualAndReviewedAtLessThan(
                        AiOperationSuggestionStatus.CONVERTED_TO_DRAFT, windowFrom, windowTo));
    }

    private AiDailyReportInboundSummaryResponse inboundSummary(
            LocalDateTime windowFrom,
            LocalDateTime windowTo) {
        return new AiDailyReportInboundSummaryResponse(
                inboundOrderRepository.countByStatusAndConfirmedAtGreaterThanEqualAndConfirmedAtLessThan(
                        InboundOrderStatus.APPLIED, windowFrom, windowTo));
    }

    private List<String> limitations(AiInventorySalesEvidenceResponse evidence) {
        List<String> limitations = new ArrayList<>();
        limitations.add("日报统计使用服务所在时区的自然日。");
        limitations.add("低库存数量为当前库存快照，并非当日事件计数。");
        if (evidence != null) {
            limitations.addAll(evidence.limitations());
        }
        return limitations;
    }
}
