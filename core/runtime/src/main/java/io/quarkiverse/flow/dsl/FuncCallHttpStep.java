package io.quarkiverse.flow.dsl;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import io.serverlessworkflow.fluent.spec.dsl.BaseCallHttpSpec;
import io.serverlessworkflow.fluent.spec.spi.CallHttpTaskFluent;

public class FuncCallHttpStep extends Step<FuncCallHttpStep, FuncCallHttpTaskBuilder>
        implements BaseCallHttpSpec<FuncCallHttpStep> {

    private final List<Consumer<CallHttpTaskFluent<?>>> steps = new ArrayList<>();

    private String name;

    public FuncCallHttpStep(String name) {
        this.name = name;
    }

    public FuncCallHttpStep() {
    }

    @Override
    public FuncCallHttpStep self() {
        return this;
    }

    protected void configure(FuncTaskItemListBuilder list, Consumer<FuncCallHttpTaskBuilder> post) {
        list.http(
                name,
                builder -> {
                    this.accept(builder);
                    post.accept(builder);
                });
    }

    @Override
    public List<Consumer<CallHttpTaskFluent<?>>> steps() {
        return steps;
    }
}
