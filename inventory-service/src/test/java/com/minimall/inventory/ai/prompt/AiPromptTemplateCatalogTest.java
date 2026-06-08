package com.minimall.inventory.ai.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class AiPromptTemplateCatalogTest {

    private final AiPromptTemplateCatalog catalog = new AiPromptTemplateCatalog(new ObjectMapper());

    @Test
    void loadsAllRegisteredPromptTemplatesFromResources() {
        assertThat(catalog.all())
                .hasSize(AiPromptTemplateId.values().length)
                .extracting(AiPromptTemplate::id)
                .containsExactlyInAnyOrder(AiPromptTemplateId.values());

        assertThat(catalog.all())
                .allSatisfy(template -> {
                    assertThat(template.promptVersion()).endsWith("-v1");
                    assertThat(template.outputSchemaVersion()).isEqualTo("inventory-analysis-output-v1");
                    assertThat(template.systemPrompt()).contains("provided JSON snapshot");
                    assertThat(template.systemPrompt()).contains("Do not claim inventory changed");
                    assertThat(template.systemPrompt()).contains("do not output SQL");
                    assertThat(template.taskPrompt()).isNotBlank();
                });
    }

    @Test
    void exposesReplenishmentPromptMetadataForProviderRequests() {
        AiPromptTemplate template = catalog.get(AiPromptTemplateId.REPLENISHMENT_SUGGESTION);

        assertThat(template.promptVersion()).isEqualTo("replenishment-suggestion-v1");
        assertThat(template.outputSchemaVersion()).isEqualTo("inventory-analysis-output-v1");
        assertThat(template.taskPrompt()).contains("suggestedQuantity");
        assertThat(template.taskPrompt()).contains("PENDING_REVIEW");
    }

    @Test
    void promptVersionsAreUniqueAcrossTemplates() {
        assertThat(catalog.all().stream()
                        .collect(Collectors.groupingBy(AiPromptTemplate::promptVersion, Collectors.counting())))
                .allSatisfy((promptVersion, count) -> assertThat(count)
                        .as(promptVersion)
                        .isEqualTo(1));
    }

    @Test
    void templateRejectsMissingRequiredMetadata() {
        assertThatThrownBy(() -> new AiPromptTemplate(
                        AiPromptTemplateId.INVENTORY_QA,
                        " ",
                        "schema-v1",
                        "system",
                        "task"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("promptVersion");

        assertThatThrownBy(() -> new AiPromptTemplate(
                        AiPromptTemplateId.INVENTORY_QA,
                        "prompt-v1",
                        "",
                        "system",
                        "task"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outputSchemaVersion");
    }

    @Test
    void catalogRejectsNullTemplateId() {
        assertThatThrownBy(() -> catalog.get(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("id");
    }
}
