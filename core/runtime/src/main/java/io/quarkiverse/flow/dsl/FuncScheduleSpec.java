package io.quarkiverse.flow.dsl;

import java.util.function.Consumer;

import io.serverlessworkflow.fluent.spec.ScheduleBuilder;

/**
 * A schedule specification that wraps a {@link FuncScheduleEventSpec} and propagates
 * its {@link FuncScheduleEventSpec#isFirst()} and {@link FuncScheduleEventSpec#isEnvelope()}
 * flags through the {@code schedule(on(one("type").first()))} call chain.
 *
 * <p>
 * Returned by {@link FlowDSL#on(FuncScheduleEventSpec)}.
 */
public class FuncScheduleSpec implements Consumer<ScheduleBuilder> {

    private final FuncScheduleEventSpec eventSpec;

    FuncScheduleSpec(FuncScheduleEventSpec eventSpec) {
        this.eventSpec = eventSpec;
    }

    public boolean isFirst() {
        return eventSpec.isFirst();
    }

    public boolean isEnvelope() {
        return eventSpec.isEnvelope();
    }

    @Override
    public void accept(ScheduleBuilder scheduleBuilder) {
        scheduleBuilder.on(eventSpec);
    }
}
