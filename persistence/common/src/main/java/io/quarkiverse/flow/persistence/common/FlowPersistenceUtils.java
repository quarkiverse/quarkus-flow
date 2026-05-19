package io.quarkiverse.flow.persistence.common;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.serverlessworkflow.impl.WorkflowDefinitionId;

class FlowPersistenceUtils {

    private FlowPersistenceUtils() {
    }

    public static Collection<WorkflowDefinitionId> excludedIds(Optional<List<String>> excludedWorkflows) {
        return excludedWorkflows
                .map(exc -> exc.stream().map(FlowPersistenceUtils::parseId).collect(Collectors.toUnmodifiableSet()))
                .orElse(Set.of());
    }

    private static WorkflowDefinitionId parseId(String name) {
        return parseId(name, ":");

    }

    private static WorkflowDefinitionId parseId(String name, String separator) {
        String[] split = name.split(separator);
        return new WorkflowDefinitionId(split[0], split[1], split[2]);
    }
}