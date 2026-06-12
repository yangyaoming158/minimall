package com.minimall.inventory.repository;

import com.minimall.inventory.domain.AiOperationSuggestionItem;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiOperationSuggestionItemRepository extends JpaRepository<AiOperationSuggestionItem, Long> {

    List<AiOperationSuggestionItem> findBySuggestionNoOrderByIdAsc(String suggestionNo);

    List<AiOperationSuggestionItem> findBySuggestionNoInOrderBySuggestionNoAscIdAsc(Collection<String> suggestionNos);
}
