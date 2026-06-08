package com.minimall.inventory.ai.validation;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record AiOutputValidationContext(
        AiAnalysisType expectedAnalysisType,
        Set<String> knownProductIds,
        Map<String, AiOutputProductFacts> productFactsById,
        Set<String> allowedDateValues) {

    public AiOutputValidationContext {
        Objects.requireNonNull(expectedAnalysisType, "expectedAnalysisType must not be null");
        productFactsById = copyProductFacts(productFactsById);
        knownProductIds = copyKnownProductIds(knownProductIds, productFactsById);
        allowedDateValues = copyTextSet(allowedDateValues);
    }

    public static AiOutputValidationContext of(
            AiAnalysisType expectedAnalysisType,
            Collection<AiOutputProductFacts> productFacts,
            Collection<String> allowedDateValues) {
        Map<String, AiOutputProductFacts> factsById = new LinkedHashMap<>();
        if (productFacts != null) {
            for (AiOutputProductFacts facts : productFacts) {
                if (facts != null) {
                    factsById.put(facts.productId(), facts);
                }
            }
        }
        return new AiOutputValidationContext(expectedAnalysisType, factsById.keySet(), factsById,
                allowedDateValues == null ? Set.of() : new LinkedHashSet<>(allowedDateValues));
    }

    public AiOutputProductFacts factsFor(String productId) {
        return productFactsById.get(productId);
    }

    private static Set<String> copyKnownProductIds(
            Set<String> knownProductIds,
            Map<String, AiOutputProductFacts> productFactsById) {
        LinkedHashSet<String> copy = new LinkedHashSet<>();
        if (knownProductIds != null) {
            knownProductIds.stream()
                    .map(AiOutputValidationContext::normalize)
                    .filter(value -> value != null)
                    .forEach(copy::add);
        }
        copy.addAll(productFactsById.keySet());
        return Collections.unmodifiableSet(copy);
    }

    private static Map<String, AiOutputProductFacts> copyProductFacts(
            Map<String, AiOutputProductFacts> productFactsById) {
        LinkedHashMap<String, AiOutputProductFacts> copy = new LinkedHashMap<>();
        if (productFactsById != null) {
            productFactsById.values().stream()
                    .filter(Objects::nonNull)
                    .forEach(facts -> copy.put(facts.productId(), facts));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Set<String> copyTextSet(Collection<String> values) {
        LinkedHashSet<String> copy = new LinkedHashSet<>();
        if (values != null) {
            values.stream()
                    .map(AiOutputValidationContext::normalize)
                    .filter(value -> value != null)
                    .forEach(copy::add);
        }
        return Collections.unmodifiableSet(copy);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
