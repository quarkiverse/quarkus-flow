package io.quarkiverse.flow.recorders;

import io.quarkus.runtime.annotations.RecordableConstructor;

public record DiscoveredFlow(String className, String methodName, boolean isStatic) {
    @RecordableConstructor
    public DiscoveredFlow {
    }
}
