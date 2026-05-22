package io.quarkiverse.flow.langchain4j.deployment;

import java.util.List;

public record PredicateMetadata(String methodName, List<String> parameterTypeNames, String description) {
}
