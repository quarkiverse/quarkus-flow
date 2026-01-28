package io.quarkiverse.flow.providers;

import jakarta.ws.rs.client.Invocation;

import org.eclipse.microprofile.config.ConfigProvider;

import io.serverlessworkflow.impl.TaskContext;
import io.serverlessworkflow.impl.WorkflowContext;
import io.serverlessworkflow.impl.executors.http.HttpRequestDecorator;

public class MetadataPropagationRequestDecorator implements HttpRequestDecorator {

    public static final String X_FLOW_INSTANCE_ID = "X-Flow-Instance-Id";
    public static final String X_FLOW_TASK_ID = "X-Flow-Task-Id";
    private final boolean enableMetadataPropagation;

    public MetadataPropagationRequestDecorator() {
        this.enableMetadataPropagation = ConfigProvider.getConfig()
                .getOptionalValue("quarkus.flow.http.client.enable-metadata-propagation", Boolean.class)
                .orElse(Boolean.TRUE);
    }

    @Override
    public void decorate(Invocation.Builder requestBuilder, WorkflowContext workflowContext, TaskContext taskContext) {
        if (enableMetadataPropagation) {
            requestBuilder.header(X_FLOW_INSTANCE_ID, workflowContext.instance().id())
                    .header(X_FLOW_TASK_ID, taskContext.position().jsonPointer());
        }
    }
}
