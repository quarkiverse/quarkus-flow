package io.quarkiverse.flow.dsl;

import java.util.function.Consumer;

/** Chainable listen step; applies FuncListenSpec then queued export/when. */
public final class ListenStep extends Step<ListenStep, FuncListenTaskBuilder> {

    private final String name; // nullable
    private final FuncListenSpec spec;

    ListenStep(String name, FuncListenSpec spec) {
        this.name = name;
        this.spec = spec;
    }

    @Override
    protected void configure(
            FuncTaskItemListBuilder list, Consumer<FuncListenTaskBuilder> postApply) {
        if (name == null) {
            list.listen(
                    lb -> {
                        spec.accept(lb);
                        postApply.accept(lb);
                    });
        } else {
            list.listen(
                    name,
                    lb -> {
                        spec.accept(lb);
                        postApply.accept(lb);
                    });
        }
    }
}
