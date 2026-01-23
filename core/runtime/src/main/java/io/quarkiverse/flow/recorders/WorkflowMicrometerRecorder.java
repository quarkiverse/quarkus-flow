package io.quarkiverse.flow.recorders;

import java.util.function.Supplier;

import jakarta.enterprise.inject.Any;

import io.micrometer.core.instrument.MeterRegistry;
import io.quarkiverse.flow.config.FlowMetricsConfig;
import io.quarkiverse.flow.metrics.MicrometerExecutionListener;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InjectableInstance;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class WorkflowMicrometerRecorder {

    public Supplier<MicrometerExecutionListener> supplyMicrometerExecutionListener(FlowMetricsConfig flowMetricsConfig) {
        return () -> {
            if (flowMetricsConfig.enabled()) {
                InjectableInstance<MeterRegistry> meterRegistry = Arc.container().select(MeterRegistry.class,
                        Any.Literal.INSTANCE);
                if (meterRegistry.isResolvable()) {
                    return new MicrometerExecutionListener(meterRegistry.get(), flowMetricsConfig);
                }
            }
            return null;
        };
    }

}
