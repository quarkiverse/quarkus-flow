package io.quarkiverse.flow.messaging;

import java.util.Optional;

import org.eclipse.microprofile.config.ConfigProvider;

import io.cloudevents.core.builder.CloudEventBuilder;
import io.serverlessworkflow.impl.TaskContext;
import io.serverlessworkflow.impl.WorkflowContext;
import io.serverlessworkflow.impl.events.EmittedEventDecorator;

public class MetadataPropagationEmittedEventDecorator implements EmittedEventDecorator {

    public static final String FLOW_INSTANCE_ID = "flowinstanceid";
    public static final String FLOW_TASK_ID = "flowtaskid";

    private final boolean enableMetadataPropagation;
    private final String instanceIDKey;
    private final String taskIDKey;

    public MetadataPropagationEmittedEventDecorator() {
        this.enableMetadataPropagation = ConfigProvider.getConfig()
                .getOptionalValue("quarkus.flow.messaging.enable-metadata-propagation", Boolean.class)
                .orElse(Boolean.TRUE);
        this.instanceIDKey = metadataKey("instance-id")
                .orElse(FLOW_INSTANCE_ID);
        this.taskIDKey = metadataKey("task-id")
                .orElse(FLOW_TASK_ID);
    }

    @Override
    public void decorate(CloudEventBuilder ceBuilder, WorkflowContext workflowContext, TaskContext taskContext) {
        if (enableMetadataPropagation) {
            ceBuilder.withContextAttribute(this.instanceIDKey, workflowContext.instance().id())
                    .withContextAttribute(this.taskIDKey, taskContext.position().jsonPointer());
        }
    }

    private Optional<String> metadataKey(final String keyName) {
        return ConfigProvider.getConfig()
                .getOptionalValue("quarkus.flow.messaging.metadata." + keyName + ".key", String.class);
    }
}
