package io.quarkiverse.flow.dsl;

import io.quarkiverse.flow.dsl.configurers.FuncListenConfigurer;

public final class FuncListenSpec extends BaseFuncListenSpec<FuncListenSpec, FuncListenTaskBuilder>
        implements FuncListenConfigurer {

    public FuncListenSpec() {
        super(FuncListenTaskBuilder::to);
    }

    @Override
    protected FuncListenSpec self() {
        return this;
    }

    @Override
    public void accept(FuncListenTaskBuilder funcListenTaskBuilder) {
        acceptInto(funcListenTaskBuilder);
    }
}
