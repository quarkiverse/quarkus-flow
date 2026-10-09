package io.quarkiverse.flow.opentelemetry.runtime.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "quarkus.flow.otel")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface FlowOTelConfig {

    String QUARKUS_FLOW_OTEL_ENABLED = "quarkus.flow.otel.enabled";
    String QUARKUS_FLOW_OTEL_TASK_SPAN_CURRENT = "quarkus.flow.otel.task-span-current";

    /**
     * Enable OpenTelemetry for Quarkus Flows.
     */
    Optional<Boolean> enabled();

    /**
     * Use this method to access the actual configured value, or default value.
     * To distinguish default value from user explicitly configured value, if any, use enabled() instead.
     *
     * @return returns the currently configured value or the default value when not configured.
     */
    default boolean isEnabled() {
        return enabled().orElse(true);
    }

    /**
     * Make each task's {@code task.execute} span the current span while the task body runs, so that spans created by
     * the task body (for example LangChain4j AI service calls or REST client calls) are children of the task span and
     * belong to the workflow's trace.
     * <p>
     * Set to {@code false} to restore the previous behavior, where the task span is recorded but never current.
     */
    @WithName("task-span-current")
    @WithDefault("true")
    boolean taskSpanCurrent();
}
