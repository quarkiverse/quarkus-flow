package io.quarkiverse.flow.recorders;

import java.util.function.Supplier;

import io.quarkiverse.flow.config.FlowStructuredLoggingConfig;
import io.quarkiverse.flow.structuredlogging.StructuredLoggingListener;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class WorkflowStructuredLoggingRecorder {

    public Supplier<StructuredLoggingListener> supplyStructuredLoggingListener(
            FlowStructuredLoggingConfig config) {
        return () -> {
            if (config.enabled()) {
                return new StructuredLoggingListener(config);
            }
            return null;
        };
    }
}
