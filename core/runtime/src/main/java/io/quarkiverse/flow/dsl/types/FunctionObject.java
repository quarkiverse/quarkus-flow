package io.quarkiverse.flow.dsl.types;

import java.io.Serializable;

/**
 * Marked interface for objects that represent a function, predicate or consumer invoked by the
 * runtime
 */
public interface FunctionObject extends Serializable, FilterSerializable {
}
