package io.quarkiverse.flow.dsl;

import java.util.Collection;

import io.quarkiverse.flow.dsl.configurers.FuncListenConfigurer;
import io.quarkiverse.flow.dsl.types.SerializableFunction;
import io.serverlessworkflow.api.types.ListenTaskConfiguration;

/**
 * Fluent spec for configuring a {@code listen} task. Supports {@link #first()} and
 * {@link #envelope()} to control how the CloudEvent collection is unwrapped.
 *
 * <p>
 * By design, the Open Workflow specification always delivers events as a collection,
 * even when only one event is expected (via {@code toOne()}). Call {@link #first()} to
 * explicitly extract the first element from that collection so downstream tasks receive
 * a single object rather than a single-element array.
 *
 * <pre>{@code
 * // Extract first CE data payload
 * listen(toOne("type").first())
 *
 * // Extract first full CE envelope
 * listen(toOne("type").envelope().first())
 * }</pre>
 */
public final class FuncListenSpec extends BaseFuncListenSpec<FuncListenSpec, FuncListenTaskBuilder>
        implements FuncListenConfigurer {

    private boolean first;
    private boolean envelope;

    public FuncListenSpec() {
        super(FuncListenTaskBuilder::to);
    }

    /**
     * Extract the first item from the CloudEvent collection as the listen task output.
     * The specification always delivers events as a collection — even for {@code toOne()}
     * which produces a single-element array. This method unwraps that array so the next
     * task receives a single object directly.
     *
     * <p>
     * By default, only the CE {@code data} payload is extracted. Chain with
     * {@link #envelope()} before this call to keep the full CloudEvent envelope instead.
     *
     * @return this spec for chaining
     */
    public FuncListenSpec first() {
        this.first = true;
        return this;
    }

    /**
     * When combined with {@link #first()}, passes the full CloudEvent envelope
     * (including extensions, source, type, etc.) instead of just the data payload.
     *
     * @return this spec for chaining
     */
    public FuncListenSpec envelope() {
        this.envelope = true;
        return this;
    }

    @Override
    protected FuncListenSpec self() {
        return this;
    }

    @Override
    public void accept(FuncListenTaskBuilder funcListenTaskBuilder) {
        acceptInto(funcListenTaskBuilder);
        if (first) {
            if (envelope) {
                funcListenTaskBuilder.read(ListenTaskConfiguration.ListenAndReadAs.ENVELOPE);
            }
            funcListenTaskBuilder.outputAs(
                    (SerializableFunction<Collection<Object>, Object>) col -> {
                        if (col != null && !col.isEmpty()) {
                            return col.iterator().next();
                        }
                        return col;
                    });
        }
    }
}
