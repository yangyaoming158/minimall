package com.minimall.inventory.repository;

import com.minimall.inventory.domain.AiOperationSuggestion;
import com.minimall.inventory.domain.AiOperationSuggestionStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AiOperationSuggestionRepository
        extends JpaRepository<AiOperationSuggestion, Long>, JpaSpecificationExecutor<AiOperationSuggestion> {

    Optional<AiOperationSuggestion> findBySuggestionNo(String suggestionNo);

    boolean existsBySuggestionNo(String suggestionNo);

    List<AiOperationSuggestion> findByLinkedInboundNo(String linkedInboundNo);

    Page<AiOperationSuggestion> findByStatus(AiOperationSuggestionStatus status, Pageable pageable);

    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(LocalDateTime createdFrom, LocalDateTime createdTo);

    long countByStatusAndReviewedAtGreaterThanEqualAndReviewedAtLessThan(
            AiOperationSuggestionStatus status,
            LocalDateTime reviewedFrom,
            LocalDateTime reviewedTo);
}
