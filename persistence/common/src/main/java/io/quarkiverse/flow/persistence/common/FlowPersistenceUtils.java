package io.quarkiverse.flow.persistence.common;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import io.serverlessworkflow.impl.WorkflowDefinitionId;

class FlowPersistenceUtils {

    private FlowPersistenceUtils() {
    }

    public static Collection<WorkflowDefinitionId> excludedIds(Collection<String> excludedWorkflows) {
        return excludedWorkflows == null || excludedWorkflows.isEmpty() ? Set.of()
                : excludedWorkflows.stream().map(FlowPersistenceUtils::parseId).collect(Collectors.toUnmodifiableSet());
    }

    private static WorkflowDefinitionId parseId(String name) {
        return parseId(name, ":");

    }

    private static WorkflowDefinitionId parseId(String name, String separator) {
        String[] split = name.split(separator);
        return new WorkflowDefinitionId(split[0], split[1], split[2]);
    }
}
