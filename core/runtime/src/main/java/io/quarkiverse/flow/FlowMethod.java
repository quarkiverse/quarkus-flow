package io.quarkiverse.flow;

import java.io.Serializable;
import java.util.function.Function;

import io.serverlessworkflow.api.types.Workflow;

@FunctionalInterface
public interface FlowMethod<B> extends Function<B, Workflow>, Serializable {
}
