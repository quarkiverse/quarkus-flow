package io.quarkiverse.flow.dsl;

import java.util.Objects;
import java.util.function.Consumer;

/** Chainable emit step; applies FuncEmitSpec then queued export/when. */
public final class EmitStep extends Step<EmitStep, FuncEmitTaskBuilder> {

    private final String name; // nullable
    private final Consumer<FuncEmitTaskBuilder> cfg;

    EmitStep(String name, Consumer<FuncEmitTaskBuilder> cfg) {
        this.name = name;
        this.cfg = Objects.requireNonNull(cfg, "cfg");
    }

    @Override
    protected void configure(FuncTaskItemListBuilder list, Consumer<FuncEmitTaskBuilder> postApply) {
        if (name == null) {
            list.emit(
                    e -> {
                        cfg.accept(e);
                        postApply.accept(e);
                    });
        } else {
            list.emit(
                    name,
                    e -> {
                        cfg.accept(e);
                        postApply.accept(e);
                    });
        }
    }
}
