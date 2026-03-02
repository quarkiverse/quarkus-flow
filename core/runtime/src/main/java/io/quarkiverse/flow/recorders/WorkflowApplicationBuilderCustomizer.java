package io.quarkiverse.flow.recorders;

import io.serverlessworkflow.impl.ServicePriority;
import io.serverlessworkflow.impl.WorkflowApplication;

public interface WorkflowApplicationBuilderCustomizer extends ServicePriority {
    void customize(WorkflowApplication.Builder builder);
}
