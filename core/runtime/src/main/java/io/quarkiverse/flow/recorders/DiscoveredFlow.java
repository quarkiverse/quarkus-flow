package io.quarkiverse.flow.recorders;

import io.quarkus.runtime.annotations.RecordableConstructor;

/**
 * Build-time discovery info for one flow.
 */
public final class DiscoveredFlow {
    public final String className;
    public final String methodName;
    public final String workflowName;
    public final boolean isStatic;

    @RecordableConstructor
    public DiscoveredFlow(String className, String methodName, String workflowName, boolean isStatic) {
        this.className = className;
        this.methodName = methodName;
        this.isStatic = isStatic;
        this.workflowName = workflowName;
    }
}
