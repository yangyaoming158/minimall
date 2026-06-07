package com.minimall.inventory.client.order;

import com.minimall.common.core.response.PageResponse;
import com.minimall.inventory.dto.AiSalesEvidenceResponse;
import java.time.LocalDateTime;
import org.springframework.data.domain.Pageable;

public interface SalesEvidenceClient {

    PageResponse<AiSalesEvidenceResponse> salesByProduct(
            String productId,
            LocalDateTime createdFrom,
            LocalDateTime createdTo,
            Pageable pageable);
}
