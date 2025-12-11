package io.quarkiverse.flow.camel;

import java.util.function.Function;

public interface CamelConnector<T, R> extends Function<T, R> {
    String connectorName();
}
