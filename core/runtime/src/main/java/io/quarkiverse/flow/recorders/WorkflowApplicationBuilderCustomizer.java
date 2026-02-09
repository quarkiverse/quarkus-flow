package io.quarkiverse.flow.recorders;

import io.serverlessworkflow.impl.WorkflowApplication;

public interface WorkflowApplicationBuilderCustomizer extends Comparable<WorkflowApplicationBuilderCustomizer> {
    default int priority() {
        return Integer.MAX_VALUE;
    }

    default int compareTo(WorkflowApplicationBuilderCustomizer other) {
        return this.priority() - other.priority();
    }

    void customize(WorkflowApplication.Builder builder);
}
