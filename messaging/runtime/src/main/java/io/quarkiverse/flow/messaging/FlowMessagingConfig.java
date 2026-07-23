package io.quarkiverse.flow.messaging;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.serverlessworkflow.impl.WorkflowInstance;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.flow.messaging")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface FlowMessagingConfig {

    /**
     * Whether the emitters should propagate correlation metadata.
     * <p>
     * When enabled, correlation metadata attributes are automatically added to emitted CloudEvents
     * to enable traceability and correlation across workflow instances and tasks.
     * <p>
     * The default correlation metadata includes:
     * <ul>
     * <li><code>flowinstanceid</code> the instance ID, see {@link WorkflowInstance#id()}</li>
     * <li><code>flowtaskid</code> the task's position that where the request was made, see
     * {@link io.serverlessworkflow.impl.TaskContext#position()}</li>
     * </ul>
     */
    @WithDefault("true")
    Optional<Boolean> enableMetadataCorrelation();

    /**
     * Automatically configure Kafka connector properties for the default Flow messaging channels
     * ('flow-in', 'flow-out'). When enabled, the engine injects all necessary Kafka connector,
     * topic, and serializer/deserializer defaults. These defaults can always be overridden in
     * {@code application.properties}.
     * <p>
     * This setting is available in any launch mode (dev, test, or production) and does not require
     * DevServices. To also enable the lifecycle channel, use
     * {@code quarkus.flow.messaging.lifecycle-enabled=true}.
     */
    @WithDefault("false")
    Optional<Boolean> defaultKafkaEnabled();

    /**
     * Configure the metadata key used in correlation propagation.
     * <p>
     * Allows customization of the metadata keys used for correlation information.
     * <p>
     * Example configuration:
     *
     * <pre>
     * quarkus.flow.messaging.metadata.task-id.key=flowtooltaskid
     * quarkus.flow.messaging.metadata.instance-id.key=flowtoolinstanceid
     * </pre>
     */
    MetadataConfig metadata();

    interface MetadataConfig {

        /**
         * Configure the metadata Task ID used in correlation propagation.
         */
        MetadataItemConfig taskId();

        /**
         * Configure the metadata Workflow Instance ID used in correlation propagation.
         */
        MetadataItemConfig instanceId();
    }

    interface MetadataItemConfig {
        /**
         * The metadata's key name to be used in correlation propagation.
         * <p>
         * This defines the actual
         * <a href="https://github.com/cloudevents/spec/blob/v1.0/spec.md#extension-context-attributes">extension context
         * attribute's</a> key name that will be used in the emitted CloudEvents.
         */
        Optional<String> key();
    }
}
