package io.quarkiverse.flow.deployment;

import io.quarkus.builder.item.SimpleBuildItem;
import io.quarkus.runtime.RuntimeValue;
import io.serverlessworkflow.impl.WorkflowApplication;

public final class WorkflowApplicationBuilderBuildItem extends SimpleBuildItem {

    private final RuntimeValue<WorkflowApplication.Builder> appBuilder;

    public WorkflowApplicationBuilderBuildItem(RuntimeValue<WorkflowApplication.Builder> appBuilder) {
        this.appBuilder = appBuilder;
    }

    public RuntimeValue<WorkflowApplication.Builder> builder() {
        return appBuilder;
    }
}
