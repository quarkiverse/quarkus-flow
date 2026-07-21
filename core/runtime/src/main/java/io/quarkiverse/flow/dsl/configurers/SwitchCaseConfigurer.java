package io.quarkiverse.flow.dsl.configurers;

import java.util.function.Consumer;

import io.quarkiverse.flow.dsl.FuncSwitchTaskBuilder;

@FunctionalInterface
public interface SwitchCaseConfigurer
        extends Consumer<FuncSwitchTaskBuilder.SwitchCasePredicateBuilder> {
}
