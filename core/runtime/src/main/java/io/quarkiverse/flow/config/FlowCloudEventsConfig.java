package io.quarkiverse.flow.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Configuration for the CloudEvents emitted by workflows ({@code emit} tasks).
 * <p>
 * By default, Quarkus Flow gives every emitted CloudEvent that does not declare its own {@code source} a traceable
 * default derived from the emitting workflow's identity ({@code namespace:name:version}). This avoids the opaque
 * {@code reference-impl} placeholder used by the underlying SDK when no source is present.
 */
@ConfigMapping(prefix = "quarkus.flow.cloud-events")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface FlowCloudEventsConfig {

    /**
     * Default {@code source} applied to emitted CloudEvents that do not declare their own source.
     * <p>
     * When set, this fixed value is used for every emitted event (unless the event sets its own {@code source}),
     * overriding the workflow-identity-derived default. When unset, the source defaults to the emitting workflow's
     * identity in the form {@code namespace:name:version}.
     * <p>
     * An explicit {@code source} on an individual {@code emit} task always takes precedence over this value.
     */
    Optional<String> source();

    /**
     * Whether to derive a default {@code source} from the emitting workflow's identity
     * ({@code namespace:name:version}) for emitted CloudEvents that do not declare their own source.
     * <p>
     * Set to {@code false} to disable the default entirely and preserve the underlying SDK behavior (no source
     * injection). Ignored when {@link #source()} is set, since an explicit default source always applies.
     */
    @WithDefault("true")
    boolean deriveSourceFromWorkflow();
}
