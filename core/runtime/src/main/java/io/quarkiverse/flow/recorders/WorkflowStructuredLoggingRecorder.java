package io.quarkiverse.flow.recorders;

import java.util.function.Function;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.flow.config.FlowStructuredLoggingConfig;
import io.quarkiverse.flow.structuredlogging.StructuredLoggingListener;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class WorkflowStructuredLoggingRecorder {

    public Function<SyntheticCreationalContext<StructuredLoggingListener>, StructuredLoggingListener> structuredLoggingListenerCreator(
            FlowStructuredLoggingConfig config) {
        return context -> {
            if (config.enabled()) {
                ObjectMapper objectMapper = context.getInjectedReference(ObjectMapper.class);
                return new StructuredLoggingListener(config, objectMapper);
            }
            return null;
        };
    }
}
