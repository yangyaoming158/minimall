package com.minimall.inventory.ai.prompt;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class AiPromptTemplateCatalog {

    private final Map<AiPromptTemplateId, AiPromptTemplate> templates;

    public AiPromptTemplateCatalog(ObjectMapper objectMapper) {
        this.templates = loadTemplates(Objects.requireNonNull(objectMapper, "objectMapper must not be null"));
    }

    public AiPromptTemplate get(AiPromptTemplateId id) {
        Objects.requireNonNull(id, "id must not be null");
        AiPromptTemplate template = templates.get(id);
        if (template == null) {
            throw new IllegalArgumentException("AI prompt template is not registered: " + id);
        }
        return template;
    }

    public Collection<AiPromptTemplate> all() {
        return templates.values();
    }

    private Map<AiPromptTemplateId, AiPromptTemplate> loadTemplates(ObjectMapper objectMapper) {
        EnumMap<AiPromptTemplateId, AiPromptTemplate> loaded = new EnumMap<>(AiPromptTemplateId.class);
        for (AiPromptTemplateId id : AiPromptTemplateId.values()) {
            loaded.put(id, loadTemplate(objectMapper, id));
        }
        return Collections.unmodifiableMap(loaded);
    }

    private AiPromptTemplate loadTemplate(ObjectMapper objectMapper, AiPromptTemplateId id) {
        ClassPathResource resource = new ClassPathResource(id.resourcePath());
        try (InputStream inputStream = resource.getInputStream()) {
            AiPromptTemplate template = objectMapper.readValue(inputStream, AiPromptTemplate.class);
            if (template.id() != id) {
                throw new IllegalStateException(
                        "AI prompt template resource " + id.resourcePath() + " declared id " + template.id());
            }
            return template;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load AI prompt template resource: " + id.resourcePath(),
                    exception);
        }
    }
}
