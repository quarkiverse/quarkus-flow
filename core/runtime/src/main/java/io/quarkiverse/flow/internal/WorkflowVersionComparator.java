package io.quarkiverse.flow.internal;

import java.util.Comparator;
import java.util.Map;

import com.github.zafarkhaja.semver.ParseException;
import com.github.zafarkhaja.semver.Version;

import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowDefinitionId;

public class WorkflowVersionComparator implements Comparator<Map.Entry<WorkflowDefinitionId, WorkflowDefinition>> {

    @Override
    public int compare(Map.Entry<WorkflowDefinitionId, WorkflowDefinition> o1,
            Map.Entry<WorkflowDefinitionId, WorkflowDefinition> o2) {
        return tryParseSemver(o1.getKey().version(), o1.getKey())
                .compareTo(tryParseSemver(o2.getKey().version(), o2.getKey()));
    }

    private Version tryParseSemver(String semver, WorkflowDefinitionId id) {
        try {
            return Version.parse(semver);
        } catch (IllegalArgumentException | ParseException e) {
            throw new IllegalArgumentException(
                    String.format("Invalid semantic version '%s' in workflow '%s:%s'. " +
                            "Expected format: MAJOR.MINOR.PATCH (e.g., '1.0.0')",
                            id.version(), id.namespace(), id.name()),
                    e);
        }
    }
}
