package io.quarkiverse.flow;

import java.io.Serializable;
import java.util.function.Supplier;

import io.serverlessworkflow.api.types.Workflow;

@FunctionalInterface
public interface FlowSupplier extends Supplier<Workflow>, Serializable {
}
