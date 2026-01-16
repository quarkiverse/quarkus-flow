package io.quarkiverse.flow.messaging;

import static io.quarkiverse.flow.messaging.FlowMessagingConfig.FLOW_MESSAGING_CONFIG_PREFIX;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = FLOW_MESSAGING_CONFIG_PREFIX)
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface FlowMessagingConfig {

    String FLOW_MESSAGING_CONFIG_PREFIX = "quarkus.flow.messaging";

    /**
     * Whether the emitters should propagate correlation metadata.
     * <p>
     * When enabled, correlation metadata attributes are automatically added to emitted CloudEvents
     * to enable traceability and correlation across workflow instances and tasks.
     * <p>
     * The default correlation metadata includes:
     * <ul>
     * <li><code>flowinstanceid</code> - the Workflow instance's ID</li>
     * <li><code>flowtaskid</code> - the task position where the event was published</li>
     * </ul>
     */
    @WithDefault("true")
    Optional<Boolean> enableCorrelationPropagation();

    /**
     * Configure the metadata used in correlation propagation.
     * <p>
     * Allows customization of the metadata keys used for correlation information.
     * <p>
     * Example configuration:
     *
     * <pre>
     * quarkus.flow.messaging.metadata.task-id.key=taskPosition
     * quarkus.flow.messaging.metadata.instance-id.key=workflowInstanceId
     * </pre>
     */
    MetadataConfig metadata();

    interface MetadataConfig {

        /**
         * Configure the metadata Task ID used in correlation propagation.
         * <p>
         * This configures the key name for the task identifier in correlation metadata.
         */
        @WithDefault("flowtaskid")
        MetadataItemConfig taskId();

        /**
         * Configure the metadata Workflow Instance ID used in correlation propagation.
         * <p>
         * This configures the key name for the workflow instance identifier in correlation metadata.
         */
        @WithDefault("flowinstanceid")
        MetadataItemConfig instanceId();

    }

    interface MetadataItemConfig {
        /**
         * The metadata's key name to be used in correlation propagation.
         * <p>
         * This defines the actual key name that will be used in the emitted CloudEvents.
         */
        Optional<String> key();
    }
}
