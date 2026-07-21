package io.quarkiverse.flow.dsl;

import java.util.function.Predicate;

import io.quarkiverse.flow.dsl.configurers.SwitchCaseConfigurer;

public class SwitchCaseSpec<T> implements SwitchCaseConfigurer {

    private String then = "";
    private Predicate<T> when;
    private Class<T> whenClass;

    public SwitchCaseSpec<T> when(Predicate<T> when, Class<T> whenClass) {
        this.when = when;
        this.whenClass = whenClass;
        return this;
    }

    public SwitchCaseSpec<T> when(Predicate<T> when) {
        this.when = when;
        return this;
    }

    public SwitchCaseSpec<T> then(String directive) {
        this.then = directive;
        return this;
    }

    @Override
    public void accept(FuncSwitchTaskBuilder.SwitchCasePredicateBuilder switchCasePredicateBuilder) {
        if (this.whenClass != null) {
            switchCasePredicateBuilder.then(this.then).when(this.when, this.whenClass);
        } else {
            switchCasePredicateBuilder.then(this.then).when(this.when);
        }
    }
}
