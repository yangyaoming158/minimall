package com.minimall.inventory.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minimall.inventory.domain.AiOperationSuggestion;
import com.minimall.inventory.domain.AiOperationSuggestionItem;
import com.minimall.inventory.domain.AiOperationSuggestionSource;
import com.minimall.inventory.domain.AiOperationSuggestionStatus;
import com.minimall.inventory.domain.AiOperationSuggestionType;
import com.minimall.inventory.domain.AiSuggestionRiskLevel;
import com.minimall.inventory.domain.AiSuggestionValidationStatus;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class AiOperationSuggestionRepositoryTest {

    @Autowired
    private AiOperationSuggestionRepository suggestionRepository;

    @Autowired
    private AiOperationSuggestionItemRepository itemRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void savesSuggestionAndFindsBySuggestionNo() {
        AiOperationSuggestion saved = suggestionRepository.saveAndFlush(new AiOperationSuggestion(
                " AIS-20260604-001 ",
                AiOperationSuggestionType.REPLENISHMENT,
                AiOperationSuggestionSource.AI_MODEL,
                " Low stock replenishment ",
                " snapshot:low-stock:20260604 ",
                " SKU-A is below safety stock "));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(suggestionRepository.findBySuggestionNo("AIS-20260604-001"))
                .isPresent()
                .get()
                .satisfies(suggestion -> {
                    assertThat(suggestion.getSuggestionNo()).isEqualTo("AIS-20260604-001");
                    assertThat(suggestion.getType()).isEqualTo(AiOperationSuggestionType.REPLENISHMENT);
                    assertThat(suggestion.getStatus()).isEqualTo(AiOperationSuggestionStatus.PENDING_REVIEW);
                    assertThat(suggestion.getSource()).isEqualTo(AiOperationSuggestionSource.AI_MODEL);
                    assertThat(suggestion.getReason()).isEqualTo("Low stock replenishment");
                    assertThat(suggestion.getInputSnapshotRef()).isEqualTo("snapshot:low-stock:20260604");
                    assertThat(suggestion.getInputSummary()).isEqualTo("SKU-A is below safety stock");
                    assertThat(suggestion.getModelProvider()).isNull();
                    assertThat(suggestion.getModelName()).isNull();
                    assertThat(suggestion.getPromptVersion()).isNull();
                    assertThat(suggestion.getOutputSchemaVersion()).isNull();
                    assertThat(suggestion.getValidationStatus()).isNull();
                    assertThat(suggestion.getValidationError()).isNull();
                    assertThat(suggestion.getInputSnapshotJson()).isNull();
                    assertThat(suggestion.getValidatedOutputJson()).isNull();
                    assertThat(suggestion.getRawModelOutputJson()).isNull();
                    assertThat(suggestion.getLinkedInboundNo()).isNull();
                    assertThat(suggestion.getRejectedReason()).isNull();
                });
        assertThat(suggestionRepository.existsBySuggestionNo("AIS-20260604-001")).isTrue();
    }

    @Test
    void persistsAiMetadataAndJsonSnapshots() {
        String inputSnapshotJson =
                "{\"products\":[{\"productId\":\"SKU-A\",\"availableStock\":2,\"safetyStock\":10}]}";
        String validatedOutputJson =
                "{\"analysisType\":\"LOW_STOCK\",\"items\":[{\"productId\":\"SKU-A\",\"suggestedQuantity\":8}]}";
        String rawModelOutputJson =
                "{\"choices\":[{\"message\":{\"content\":\"{\\\"summary\\\":\\\"restock\\\"}\"}}]}";
        AiOperationSuggestion suggestion = new AiOperationSuggestion(
                "AIS-META",
                AiOperationSuggestionType.REPLENISHMENT,
                AiOperationSuggestionSource.AI_MODEL,
                "metadata",
                "snapshot:meta",
                "metadata summary");
        suggestion.recordAiMetadata(
                " deepseek ",
                " deepseek-chat ",
                " prompt-v1 ",
                " ai-suggestion-output-v1 ",
                AiSuggestionValidationStatus.VALID,
                null,
                inputSnapshotJson,
                validatedOutputJson,
                rawModelOutputJson);
        suggestionRepository.saveAndFlush(suggestion);
        entityManager.clear();

        assertThat(suggestionRepository.findBySuggestionNo("AIS-META"))
                .isPresent()
                .get()
                .satisfies(saved -> {
                    assertThat(saved.getModelProvider()).isEqualTo("deepseek");
                    assertThat(saved.getModelName()).isEqualTo("deepseek-chat");
                    assertThat(saved.getPromptVersion()).isEqualTo("prompt-v1");
                    assertThat(saved.getOutputSchemaVersion()).isEqualTo("ai-suggestion-output-v1");
                    assertThat(saved.getValidationStatus()).isEqualTo(AiSuggestionValidationStatus.VALID);
                    assertThat(saved.getValidationError()).isNull();
                    assertThat(saved.getInputSnapshotJson()).isEqualTo(inputSnapshotJson);
                    assertThat(saved.getValidatedOutputJson()).isEqualTo(validatedOutputJson);
                    assertThat(saved.getRawModelOutputJson()).isEqualTo(rawModelOutputJson);
                    assertThat(saved.getStatus()).isEqualTo(AiOperationSuggestionStatus.PENDING_REVIEW);
                });
    }

    @Test
    void persistsInvalidValidationMetadataWithoutChangingSuggestionStatusEnum() {
        AiOperationSuggestion suggestion = new AiOperationSuggestion(
                "AIS-META-INVALID",
                AiOperationSuggestionType.REPLENISHMENT,
                AiOperationSuggestionSource.AI_MODEL,
                "metadata invalid",
                "snapshot:invalid",
                "metadata invalid summary");
        suggestion.recordAiMetadata(
                "mock",
                "mock-model",
                "prompt-v1",
                "ai-suggestion-output-v1",
                AiSuggestionValidationStatus.INVALID,
                " productId SKU-MISSING is not in input snapshot ",
                "{\"products\":[]}",
                null,
                "{\"summary\":\"bad product\"}");
        suggestionRepository.saveAndFlush(suggestion);
        entityManager.clear();

        assertThat(suggestionRepository.findBySuggestionNo("AIS-META-INVALID"))
                .isPresent()
                .get()
                .satisfies(saved -> {
                    assertThat(saved.getValidationStatus()).isEqualTo(AiSuggestionValidationStatus.INVALID);
                    assertThat(saved.getValidationError())
                            .isEqualTo("productId SKU-MISSING is not in input snapshot");
                    assertThat(saved.getValidatedOutputJson()).isNull();
                    assertThat(saved.getRawModelOutputJson()).isEqualTo("{\"summary\":\"bad product\"}");
                    assertThat(saved.getStatus()).isEqualTo(AiOperationSuggestionStatus.PENDING_REVIEW);
                });
    }

    @Test
    void persistsReviewTransitionsAndFindsByStatus() {
        AiOperationSuggestion converted = suggestionRepository.saveAndFlush(new AiOperationSuggestion(
                "AIS-CONVERTED",
                AiOperationSuggestionType.REPLENISHMENT,
                AiOperationSuggestionSource.SYSTEM_GENERATED,
                "Convert to inbound draft",
                "snapshot:converted",
                "converted summary"));
        converted.convertToDraft(" INB-AI-001 ", 1001L, " reviewer ");
        suggestionRepository.saveAndFlush(converted);

        AiOperationSuggestion rejected = suggestionRepository.saveAndFlush(new AiOperationSuggestion(
                "AIS-REJECTED",
                AiOperationSuggestionType.DAILY_REPORT,
                AiOperationSuggestionSource.ADMIN_MANUAL,
                "Report suggestion",
                "snapshot:rejected",
                "rejected summary"));
        rejected.reject(" supplier delay ", 1002L, " ops-admin ");
        suggestionRepository.saveAndFlush(rejected);

        assertThat(suggestionRepository.findBySuggestionNo("AIS-CONVERTED"))
                .isPresent()
                .get()
                .satisfies(suggestion -> {
                    assertThat(suggestion.getStatus()).isEqualTo(AiOperationSuggestionStatus.CONVERTED_TO_DRAFT);
                    assertThat(suggestion.getLinkedInboundNo()).isEqualTo("INB-AI-001");
                    assertThat(suggestion.getReviewedByAdminUserId()).isEqualTo(1001L);
                    assertThat(suggestion.getReviewedByAdminUsername()).isEqualTo("reviewer");
                    assertThat(suggestion.getReviewedAt()).isNotNull();
                });
        assertThat(suggestionRepository.findBySuggestionNo("AIS-REJECTED"))
                .isPresent()
                .get()
                .satisfies(suggestion -> {
                    assertThat(suggestion.getStatus()).isEqualTo(AiOperationSuggestionStatus.REJECTED);
                    assertThat(suggestion.getRejectedReason()).isEqualTo("supplier delay");
                    assertThat(suggestion.getReviewedByAdminUserId()).isEqualTo(1002L);
                    assertThat(suggestion.getReviewedByAdminUsername()).isEqualTo("ops-admin");
                    assertThat(suggestion.getReviewedAt()).isNotNull();
                });

        Page<AiOperationSuggestion> convertedPage =
                suggestionRepository.findByStatus(AiOperationSuggestionStatus.CONVERTED_TO_DRAFT, PageRequest.of(0, 10));
        assertThat(convertedPage.getContent())
                .extracting(AiOperationSuggestion::getSuggestionNo)
                .containsExactly("AIS-CONVERTED");
    }

    @Test
    void savesItemsAndFindsBySuggestionNo() {
        suggestionRepository.saveAndFlush(new AiOperationSuggestion(
                "AIS-ITEMS",
                AiOperationSuggestionType.REPLENISHMENT,
                AiOperationSuggestionSource.AI_MODEL,
                "items",
                "snapshot:items",
                "items summary"));
        itemRepository.saveAll(List.of(
                new AiOperationSuggestionItem(
                        "AIS-ITEMS",
                        " SKU-A ",
                        " Alpha ",
                        2,
                        1,
                        10,
                        35,
                        8,
                        AiSuggestionRiskLevel.HIGH,
                        " replenish SKU-A "),
                new AiOperationSuggestionItem(
                        "AIS-ITEMS",
                        "SKU-B",
                        null,
                        null,
                        null,
                        null,
                        null,
                        3,
                        null,
                        null)));
        itemRepository.flush();

        List<AiOperationSuggestionItem> items = itemRepository.findBySuggestionNoOrderByIdAsc("AIS-ITEMS");

        assertThat(items)
                .extracting(AiOperationSuggestionItem::getProductId)
                .containsExactly("SKU-A", "SKU-B");
        assertThat(items)
                .extracting(AiOperationSuggestionItem::getSuggestedQuantity)
                .containsExactly(8, 3);
        assertThat(items.get(0)).satisfies(item -> {
            assertThat(item.getProductName()).isEqualTo("Alpha");
            assertThat(item.getAvailableStock()).isEqualTo(2);
            assertThat(item.getLockedStock()).isEqualTo(1);
            assertThat(item.getSafetyStock()).isEqualTo(10);
            assertThat(item.getSoldQuantityLast7Days()).isEqualTo(35);
            assertThat(item.getRiskLevel()).isEqualTo(AiSuggestionRiskLevel.HIGH);
            assertThat(item.getReason()).isEqualTo("replenish SKU-A");
        });
        assertThat(items.get(1).getRiskLevel()).isEqualTo(AiSuggestionRiskLevel.MEDIUM);
        assertThat(items)
                .allSatisfy(item -> {
                    assertThat(item.getId()).isNotNull();
                    assertThat(item.getCreatedAt()).isNotNull();
                    assertThat(item.getUpdatedAt()).isNotNull();
                });
        assertThat(itemRepository.findBySuggestionNoInOrderBySuggestionNoAscIdAsc(List.of("AIS-ITEMS"))).hasSize(2);
    }

    @Test
    void duplicateSuggestionNoViolatesUniqueConstraint() {
        suggestionRepository.saveAndFlush(new AiOperationSuggestion(
                "AIS-DUP",
                AiOperationSuggestionType.REPLENISHMENT,
                AiOperationSuggestionSource.AI_MODEL,
                "first",
                "snapshot:first",
                "first summary"));

        assertThatThrownBy(() -> suggestionRepository.saveAndFlush(new AiOperationSuggestion(
                "AIS-DUP",
                AiOperationSuggestionType.DAILY_REPORT,
                AiOperationSuggestionSource.SYSTEM_GENERATED,
                "second",
                "snapshot:second",
                "second summary")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicateProductInOneSuggestionViolatesUniqueConstraint() {
        suggestionRepository.saveAndFlush(new AiOperationSuggestion(
                "AIS-ITEM-DUP",
                AiOperationSuggestionType.REPLENISHMENT,
                AiOperationSuggestionSource.AI_MODEL,
                "items",
                "snapshot:items",
                "items summary"));
        itemRepository.saveAndFlush(new AiOperationSuggestionItem(
                "AIS-ITEM-DUP",
                "SKU-DUP",
                "Duplicate",
                1,
                0,
                5,
                3,
                4,
                AiSuggestionRiskLevel.LOW,
                "first"));

        assertThatThrownBy(() -> itemRepository.saveAndFlush(new AiOperationSuggestionItem(
                "AIS-ITEM-DUP",
                "SKU-DUP",
                "Duplicate",
                1,
                0,
                5,
                3,
                6,
                AiSuggestionRiskLevel.MEDIUM,
                "second")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsInvalidItemQuantities() {
        assertThatThrownBy(() -> new AiOperationSuggestionItem(
                "AIS-INVALID",
                "SKU-INVALID",
                "Invalid",
                1,
                0,
                5,
                3,
                0,
                AiSuggestionRiskLevel.LOW,
                "invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("suggestedQuantity must be positive");
        assertThatThrownBy(() -> new AiOperationSuggestionItem(
                "AIS-INVALID",
                "SKU-INVALID",
                "Invalid",
                -1,
                0,
                5,
                3,
                1,
                AiSuggestionRiskLevel.LOW,
                "invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("availableStock must be non-negative");
    }

    @Test
    void enumsExposeStableContractValues() {
        assertThat(AiOperationSuggestionStatus.values())
                .containsExactly(
                        AiOperationSuggestionStatus.PENDING_REVIEW,
                        AiOperationSuggestionStatus.CONVERTED_TO_DRAFT,
                        AiOperationSuggestionStatus.REJECTED,
                        AiOperationSuggestionStatus.APPLIED);
        assertThat(AiOperationSuggestionType.values())
                .containsExactly(
                        AiOperationSuggestionType.REPLENISHMENT,
                        AiOperationSuggestionType.DAILY_REPORT);
        assertThat(AiOperationSuggestionSource.values())
                .containsExactly(
                        AiOperationSuggestionSource.AI_MODEL,
                        AiOperationSuggestionSource.SYSTEM_GENERATED,
                        AiOperationSuggestionSource.ADMIN_MANUAL);
        assertThat(AiSuggestionRiskLevel.values())
                .containsExactly(
                        AiSuggestionRiskLevel.LOW,
                        AiSuggestionRiskLevel.MEDIUM,
                        AiSuggestionRiskLevel.HIGH);
        assertThat(AiSuggestionValidationStatus.values())
                .containsExactly(
                        AiSuggestionValidationStatus.VALID,
                        AiSuggestionValidationStatus.INVALID);
    }
}
