package io.quarkiverse.flow.langchain4j.deployment;

import java.util.Map;

public record ConditionalMetadata(Map<String, PredicateMetadata> activationConditions) {
}
