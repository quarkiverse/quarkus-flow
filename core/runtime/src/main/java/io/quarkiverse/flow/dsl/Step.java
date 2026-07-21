package io.quarkiverse.flow.dsl;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import io.quarkiverse.flow.dsl.configurers.FuncTaskConfigurer;
import io.quarkiverse.flow.dsl.spi.ConditionalTaskBuilder;
import io.quarkiverse.flow.dsl.spi.FuncTaskTransformations;
import io.quarkiverse.flow.dsl.types.ContextFunction;
import io.quarkiverse.flow.dsl.types.FilterFunction;
import io.quarkiverse.flow.dsl.types.SerializableFunction;
import io.quarkiverse.flow.dsl.types.SerializablePredicate;
import io.quarkiverse.flow.dsl.types.utils.ReflectionUtils;
import io.serverlessworkflow.api.types.FlowDirectiveEnum;
import io.serverlessworkflow.fluent.spec.TaskBaseBuilder;
import io.serverlessworkflow.fluent.spec.TimeoutBuilder;

/**
 * A deferred configurer that can chain when/inputFrom/outputAs/exportAs and apply them later to the
 * concrete task builder (e.g., Call/Emit/Listen builder).
 */
abstract class Step<SELF extends Step<SELF, B>, B> implements FuncTaskConfigurer {

    private final List<Consumer<B>> postConfigurers = new ArrayList<>();

    @SuppressWarnings("unchecked")
    protected SELF self() {
        return (SELF) this;
    }

    // ---------------------------------------------------------------------------
    // ConditionalTaskBuilder passthroughs (if/when)
    // ---------------------------------------------------------------------------

    /** Queue a {@code when(predicate)} to be applied on the concrete builder. */
    public <T> SELF when(SerializablePredicate<T> predicate) {
        postConfigurers.add(
                b -> ((ConditionalTaskBuilder<?>) b)
                        .when(predicate, ReflectionUtils.inferInputType(predicate)));
        return self();
    }

    /** Queue a {@code when(predicate, argClass)} to be applied on the concrete builder. */
    public <T> SELF when(Predicate<T> predicate, Class<T> argClass) {
        postConfigurers.add(b -> ((ConditionalTaskBuilder<?>) b).when(predicate, argClass));
        return self();
    }

    public SELF when(String jqExpr) {
        postConfigurers.add(b -> ((TaskBaseBuilder<?>) b).when(jqExpr));
        return self();
    }

    /**
     * Queue a {@code then(taskName)} to be applied on the concrete builder. Directs the workflow
     * engine to jump to the named task after this one completes.
     *
     * @param taskName the name of the next task to execute
     * @return this step for further chaining
     * @see <a
     *      href="https://github.com/serverlessworkflow/specification/blob/main/dsl-reference.md#task">DSL
     *      Reference - Task</a>
     */
    public SELF then(String taskName) {
        postConfigurers.add(b -> ((TaskBaseBuilder<?>) b).then(taskName));
        return self();
    }

    /**
     * Queue a {@code then(directive)} to be applied on the concrete builder. Directs the workflow
     * engine to apply the given flow directive after this task completes.
     *
     * @param directive the flow directive (e.g., {@link FlowDirectiveEnum#END})
     * @return this step for further chaining
     * @see <a
     *      href="https://github.com/serverlessworkflow/specification/blob/main/dsl-reference.md#task">DSL
     *      Reference - Task</a>
     */
    public SELF then(FlowDirectiveEnum directive) {
        postConfigurers.add(b -> ((TaskBaseBuilder<?>) b).then(directive));
        return self();
    }

    // ---------------------------------------------------------------------------
    // FuncTaskTransformations passthroughs: EXPORT (fn/context/filter + JQ)
    // ---------------------------------------------------------------------------

    /**
     * Shapes what the task exports for downstream consumers using a Java function.
     *
     * <p>
     * This method queues an {@code exportAs} transformation to be applied when the step is built.
     * It allows chaining transformations in a fluent manner.
     *
     * <p>
     * {@code exportAs} controls what the task <strong>exports</strong> for downstream consumers
     * (the next task, events, etc.), immediately updating the workflow context.
     *
     * <p>
     * <strong>Example:</strong>
     *
     * <pre>{@code
     * agent("draftNewsletter", drafter::draft, Draft.class)
     *         .exportAs(draft -> Map.of("draftText", draft.text()))
     *         .when(condition);
     * }</pre>
     *
     * @param <T> the context type
     * @param <R> the export type (what gets written in the workflow context)
     * @param function the transformation function
     * @return this step for method chaining
     * @see io.quarkiverse.flow.dsl.spi.FuncTaskTransformations#exportAs(Function)
     */
    public <T, R> SELF exportAs(SerializableFunction<T, R> function) {
        postConfigurers.add(
                b -> ((FuncTaskTransformations<?>) b)
                        .exportAs(function, ReflectionUtils.inferInputType(function)));
        return self();
    }

    /**
     * Shapes what the task exports for downstream consumers using a Java function with explicit
     * context input type.
     *
     * <p>
     * This variant allows you to explicitly specify the input type class for better type safety.
     *
     * @param <T> the workflow context type
     * @param <R> the export type (what gets written in the context)
     * @param function the transformation function
     * @param taskResultClass the class of the workflow context type
     * @return this step for method chaining
     * @see io.quarkiverse.flow.dsl.spi.FuncTaskTransformations#exportAs(Function, Class)
     */
    public <T, R> SELF exportAs(Function<T, R> function, Class<T> taskResultClass) {
        postConfigurers.add(b -> ((FuncTaskTransformations<?>) b).exportAs(function, taskResultClass));
        return self();
    }

    /**
     * Shapes what the task exports for downstream consumers using a context-aware filter function.
     *
     * <p>
     * This variant provides access to both workflow and task context, allowing you to inspect
     * metadata when shaping the export.
     *
     * @param <T> the workflow context type
     * @param <R> the export type (what gets written in the context)
     * @param function the filter function with workflow and task context
     * @return this step for method chaining
     * @see io.quarkiverse.flow.dsl.spi.FuncTaskTransformations#exportAs(FilterFunction)
     */
    public <T, R> SELF exportAs(FilterFunction<T, R> function) {
        postConfigurers.add(
                b -> ((FuncTaskTransformations<?>) b)
                        .exportAs(function, ReflectionUtils.inferInputType(function)));
        return self();
    }

    /**
     * Shapes what the task exports for downstream consumers using a context-aware filter function
     * with explicit input type.
     *
     * @param <T> the workflow context type
     * @param <R> the export type (what gets written in the context)
     * @param function the filter function with workflow and task context
     * @param taskResultClass the class of the workflow context type
     * @return this step for method chaining
     * @see io.quarkiverse.flow.dsl.spi.FuncTaskTransformations#exportAs(FilterFunction,
     *      Class)
     */
    public <T, R> SELF exportAs(FilterFunction<T, R> function, Class<T> taskResultClass) {
        postConfigurers.add(b -> ((FuncTaskTransformations<?>) b).exportAs(function, taskResultClass));
        return self();
    }

    /**
     * Shapes what the task exports for downstream consumers using a context-aware function.
     *
     * <p>
     * This variant provides access to workflow context, allowing you to inspect workflow metadata
     * when shaping the export.
     *
     * @param <T> the workflow context type
     * @param <R> the export type (what gets written in the context)
     * @param function the context function with workflow context
     * @return this step for method chaining
     * @see io.quarkiverse.flow.dsl.spi.FuncTaskTransformations#exportAs(ContextFunction)
     */
    public <T, R> SELF exportAs(ContextFunction<T, R> function) {
        postConfigurers.add(
                b -> ((FuncTaskTransformations<?>) b)
                        .exportAs(function, ReflectionUtils.inferInputType(function)));
        return self();
    }

    /**
     * Shapes what the task exports for downstream consumers using a context-aware function with
     * explicit input type.
     *
     * @param <T> the workflow context type
     * @param <R> the export type (what gets written in the context)
     * @param function the context function with workflow context
     * @param taskResultClass the class of the workflow context type
     * @return this step for method chaining
     * @see io.quarkiverse.flow.dsl.spi.FuncTaskTransformations#exportAs(ContextFunction,
     *      Class)
     */
    public <T, R> SELF exportAs(ContextFunction<T, R> function, Class<T> taskResultClass) {
        postConfigurers.add(b -> ((FuncTaskTransformations<?>) b).exportAs(function, taskResultClass));
        return self();
    }

    /**
     * Overrides the workflow context with the current task output.
     *
     * @return this step for method chaining
     */
    public SELF exportAsTaskOutput() {
        postConfigurers.add(
                b -> ((FuncTaskTransformations<?>) b)
                        .exportAs((context, workflowContext, taskContext) -> taskContext.output()));
        return self();
    }

    /**
     * Shapes what the task exports for downstream consumers using a JQ expression.
     *
     * <p>
     * This variant allows you to use JQ expressions to transform the task result before it's
     * forwarded to the next step or event.
     *
     * <p>
     * <strong>Example:</strong>
     *
     * <pre>{@code
     * // Given that your context has this attribute already
     * exportAs("$.username")
     * }</pre>
     *
     * @param jqExpression the JQ expression to transform the export
     * @return this step for method chaining
     */
    public SELF exportAs(String jqExpression) {
        postConfigurers.add(b -> ((TaskBaseBuilder<?>) b).exportAs(jqExpression));
        return self();
    }

    // ---------------------------------------------------------------------------
    // FuncTaskTransformations passthroughs: OUTPUT (fn/context/filter + JQ)
    // ---------------------------------------------------------------------------

    /**
     * Shapes what gets written back into the workflow data document using a Java function.
     *
     * <p>
     * This method queues an {@code outputAs} transformation to be applied when the step is built.
     * It allows chaining transformations in a fluent manner.
     *
     * <p>
     * {@code outputAs} controls what gets written back into the workflow data document. Use this
     * when you want to rename fields, drop internal details, or construct a composite DTO.
     *
     * <p>
     * <strong>Example:</strong>
     *
     * <pre>{@code
     * agent("investmentAnalyst", analyst::analyse, InvestmentMemo.class)
     *         .outputAs(memo -> Map.of("memo", memo))
     *         .when(condition);
     * }</pre>
     *
     * @param <T> the task result type
     * @param <R> the output type (what gets written to workflow data)
     * @param function the transformation function
     * @return this step for method chaining
     * @see io.quarkiverse.flow.dsl.spi.FuncTransformations#outputAs(Function)
     */
    public <T, R> SELF outputAs(SerializableFunction<T, R> function) {
        postConfigurers.add(
                b -> ((FuncTaskTransformations<?>) b)
                        .outputAs(function, ReflectionUtils.inferInputType(function)));
        return self();
    }

    /**
     * Shapes what gets written back into the workflow data document using a Java function with
     * explicit input type.
     *
     * <p>
     * This variant allows you to explicitly specify the input type class for better type safety.
     *
     * @param <T> the task result type
     * @param <R> the output type (what gets written to workflow data)
     * @param function the transformation function
     * @param taskResultClass the class of the task result type
     * @return this step for method chaining
     * @see io.quarkiverse.flow.dsl.spi.FuncTransformations#outputAs(Function, Class)
     */
    public <T, R> SELF outputAs(Function<T, R> function, Class<T> taskResultClass) {
        postConfigurers.add(b -> ((FuncTaskTransformations<?>) b).outputAs(function, taskResultClass));
        return self();
    }

    /**
     * Shapes what gets written back into the workflow data document using a context-aware filter
     * function.
     *
     * <p>
     * This variant provides access to both workflow and task context, allowing you to inspect
     * metadata, task input, and raw output when shaping the committed output.
     *
     * <p>
     * <strong>Example:</strong>
     *
     * <pre>{@code
     * get("fetchMarketData", "http://localhost:8081/market-data/{ticker}")
     *         .outputAs((MarketDataSnapshot snapshot,
     *                 WorkflowContextData wf,
     *                 TaskContextData task) -> {
     *             var input = task.input().asMap().orElseThrow();
     *             var rawBody = task.rawOutput().asText().orElseThrow();
     *             return new InvestmentPrompt(
     *                     snapshot.ticker(),
     *                     input.get("objective").toString(),
     *                     rawBody);
     *         }, MarketDataSnapshot.class);
     * }</pre>
     *
     * @param <T> the task result type
     * @param <R> the output type (what gets written to workflow data)
     * @param function the filter function with workflow and task context
     * @return this step for method chaining
     * @see io.quarkiverse.flow.dsl.spi.FuncTransformations#outputAs(FilterFunction)
     */
    public <T, R> SELF outputAs(FilterFunction<T, R> function) {
        postConfigurers.add(
                b -> ((FuncTaskTransformations<?>) b)
                        .outputAs(function, ReflectionUtils.inferInputType(function)));
        return self();
    }

    /**
     * Shapes what gets written back into the workflow data document using a context-aware filter
     * function with explicit input type.
     *
     * @param <T> the task result type
     * @param <R> the output type (what gets written to workflow data)
     * @param function the filter function with workflow and task context
     * @param taskResultClass the class of the task result type
     * @return this step for method chaining
     * @see io.quarkiverse.flow.dsl.spi.FuncTransformations#outputAs(FilterFunction, Class)
     */
    public <T, R> SELF outputAs(FilterFunction<T, R> function, Class<T> taskResultClass) {
        postConfigurers.add(b -> ((FuncTaskTransformations<?>) b).outputAs(function, taskResultClass));
        return self();
    }

    /**
     * Shapes what gets written back into the workflow data document using a context-aware function.
     *
     * <p>
     * This variant provides access to workflow context, allowing you to inspect workflow metadata
     * when shaping the committed output.
     *
     * @param <T> the task result type
     * @param <R> the output type (what gets written to workflow data)
     * @param function the context function with workflow context
     * @return this step for method chaining
     * @see io.quarkiverse.flow.dsl.spi.FuncTransformations#outputAs(ContextFunction)
     */
    public <T, R> SELF outputAs(ContextFunction<T, R> function) {
        postConfigurers.add(
                b -> ((FuncTaskTransformations<?>) b)
                        .outputAs(function, ReflectionUtils.inferInputType(function)));
        return self();
    }

    /**
     * Shapes what gets written back into the workflow data document using a context-aware function
     * with explicit input type.
     *
     * @param <T> the task result type
     * @param <R> the output type (what gets written to workflow data)
     * @param function the context function with workflow context
     * @param taskResultClass the class of the task result type
     * @return this step for method chaining
     * @see io.quarkiverse.flow.dsl.spi.FuncTransformations#outputAs(ContextFunction, Class)
     */
    public <T, R> SELF outputAs(ContextFunction<T, R> function, Class<T> taskResultClass) {
        postConfigurers.add(b -> ((FuncTaskTransformations<?>) b).outputAs(function, taskResultClass));
        return self();
    }

    /**
     * Shapes what gets written back into the workflow data document using a JQ expression.
     *
     * <p>
     * This variant allows you to use JQ expressions to project the task result into the workflow
     * data.
     *
     * <p>
     * <strong>Example:</strong>
     *
     * <pre>{@code
     * outputAs("$.username")
     * }</pre>
     *
     * @param jqExpression the JQ expression to transform the output
     * @return this step for method chaining
     */
    public SELF outputAs(String jqExpression) {
        postConfigurers.add(b -> ((FuncTaskTransformations<?>) b).outputAs(jqExpression));
        return self();
    }

    // ---------------------------------------------------------------------------
    // FuncTaskTransformations passthroughs: INPUT (fn/context/filter + JQ)
    // ---------------------------------------------------------------------------

    /**
     * Shapes the task input using a Java function.
     *
     * <p>
     * This method queues an {@code inputFrom} transformation to be applied when the step is built.
     * It allows chaining transformations in a fluent manner.
     *
     * <p>
     * Without transformations, a task sees the <strong>whole workflow data</strong> as its input.
     * Use {@code inputFrom} to give it a smaller, focused view.
     *
     * <p>
     * <strong>Example:</strong>
     *
     * <pre>{@code
     * function(...)
     *     .inputFrom((MyData data) -> Map.of("seed", data.seed()));
     * }</pre>
     *
     * @param <T> the input type (workflow data or task input)
     * @param <R> the result type (what the task will see as input)
     * @param function the transformation function
     * @return this step for method chaining
     * @see io.quarkiverse.flow.dsl.spi.FuncTransformations#inputFrom(Function)
     */
    public <T, R> SELF inputFrom(SerializableFunction<T, R> function) {
        postConfigurers.add(
                b -> ((FuncTaskTransformations<?>) b)
                        .inputFrom(function, ReflectionUtils.inferInputType(function)));
        return self();
    }

    /**
     * Shapes the task input using a Java function with explicit input type.
     *
     * <p>
     * This variant allows you to explicitly specify the input type class for better type safety.
     *
     * <p>
     * <strong>Example:</strong>
     *
     * <pre>{@code
     * inputFrom((InvestmentRequest in) -> Map.of("ticker", in.ticker().toUpperCase()),
     *         InvestmentRequest.class);
     * }</pre>
     *
     * @param <T> the input type (workflow data or task input)
     * @param <R> the result type (what the task will see as input)
     * @param function the transformation function
     * @param inputClass the class of the input type
     * @return this step for method chaining
     * @see io.quarkiverse.flow.dsl.spi.FuncTransformations#inputFrom(Function, Class)
     */
    public <T, R> SELF inputFrom(Function<T, R> function, Class<T> inputClass) {
        postConfigurers.add(b -> ((FuncTaskTransformations<?>) b).inputFrom(function, inputClass));
        return self();
    }

    /**
     * Shapes the task input using a context-aware filter function with access to workflow and task
     * context.
     *
     * <p>
     * This variant provides access to both the workflow context ({@code WorkflowContextData}) and
     * task context ({@code TaskContextData}), allowing you to inspect workflow metadata, task
     * position, and other contextual information.
     *
     * <p>
     * <strong>Example:</strong>
     *
     * <pre>{@code
     * inputFrom((InvestmentRequest in,
     *         WorkflowContextData wf,
     *         TaskContextData task) -> Map.of(
     *                 "ticker", in.ticker(),
     *                 "objective", in.objective(),
     *                 "taskPos", task.position().jsonPointer() // e.g. /do/0/task
     * ));
     * }</pre>
     *
     * @param <T> the input type (workflow data or task input)
     * @param <R> the result type (what the task will see as input)
     * @param function the filter function with workflow and task context
     * @return this step for method chaining
     * @see io.quarkiverse.flow.dsl.spi.FuncTransformations#inputFrom(FilterFunction)
     */
    public <T, R> SELF inputFrom(FilterFunction<T, R> function) {
        postConfigurers.add(
                b -> ((FuncTaskTransformations<?>) b)
                        .inputFrom(function, ReflectionUtils.inferInputType(function)));
        return self();
    }

    /**
     * Shapes the task input using a context-aware filter function with explicit input type.
     *
     * <p>
     * This variant combines the benefits of {@link #inputFrom(FilterFunction)} with explicit type
     * specification.
     *
     * @param <T> the input type (workflow data or task input)
     * @param <R> the result type (what the task will see as input)
     * @param function the filter function with workflow and task context
     * @param inputClass the class of the input type
     * @return this step for method chaining
     * @see io.quarkiverse.flow.dsl.spi.FuncTransformations#inputFrom(FilterFunction, Class)
     */
    public <T, R> SELF inputFrom(FilterFunction<T, R> function, Class<T> inputClass) {
        postConfigurers.add(b -> ((FuncTaskTransformations<?>) b).inputFrom(function, inputClass));
        return self();
    }

    /**
     * Shapes the task input using a context-aware function with access to workflow context.
     *
     * <p>
     * This variant provides access to the workflow context ({@code WorkflowContextData}), allowing
     * you to inspect workflow metadata and current data.
     *
     * @param <T> the input type (workflow data or task input)
     * @param <R> the result type (what the task will see as input)
     * @param function the context function with workflow context
     * @return this step for method chaining
     * @see io.quarkiverse.flow.dsl.spi.FuncTransformations#inputFrom(ContextFunction)
     */
    public <T, R> SELF inputFrom(ContextFunction<T, R> function) {
        postConfigurers.add(
                b -> ((FuncTaskTransformations<?>) b)
                        .inputFrom(function, ReflectionUtils.inferInputType(function)));
        return self();
    }

    /**
     * Shapes the task input using a context-aware function with explicit input type.
     *
     * <p>
     * This variant combines the benefits of {@link #inputFrom(ContextFunction)} with explicit type
     * specification.
     *
     * @param <T> the input type (workflow data or task input)
     * @param <R> the result type (what the task will see as input)
     * @param function the context function with workflow context
     * @param inputClass the class of the input type
     * @return this step for method chaining
     * @see io.quarkiverse.flow.dsl.spi.FuncTransformations#inputFrom(ContextFunction,
     *      Class)
     */
    public <T, R> SELF inputFrom(ContextFunction<T, R> function, Class<T> inputClass) {
        postConfigurers.add(b -> ((FuncTaskTransformations<?>) b).inputFrom(function, inputClass));
        return self();
    }

    /**
     * Shapes the task input using a JQ expression.
     *
     * <p>
     * This variant allows you to use JQ expressions to slice and transform the workflow data
     * before it reaches the task.
     *
     * <p>
     * <strong>Example:</strong>
     *
     * <pre>{@code
     * // Only pass the "seed" field into this task:
     * function(...)
     *     .inputFrom("$.seed");
     * }</pre>
     *
     * @param jqExpression the JQ expression to transform the input
     * @return this step for method chaining
     */
    public SELF inputFrom(String jqExpression) {
        postConfigurers.add(b -> ((FuncTaskTransformations<?>) b).inputFrom(jqExpression));
        return self();
    }

    public SELF timeout(String durationExpression) {
        this.postConfigurers.add(b -> ((TaskBaseBuilder<?>) b).timeout(durationExpression));
        return self();
    }

    public SELF timeout(Consumer<TimeoutBuilder> timeout) {
        this.postConfigurers.add(b -> ((TaskBaseBuilder<?>) b).timeout(timeout));
        return self();
    }

    // ---------------------------------------------------------------------------
    // wiring into the underlying list/builder
    // ---------------------------------------------------------------------------

    @Override
    public final void accept(FuncTaskItemListBuilder list) {
        configure(list, this::applyPost);
    }

    /** Implement per-step to attach to the correct builder and run {@code post} at the end. */
    protected abstract void configure(FuncTaskItemListBuilder list, Consumer<B> post);

    /** Applies all queued post-configurers to the concrete builder. */
    private void applyPost(B builder) {
        for (Consumer<B> c : postConfigurers)
            c.accept(builder);
    }
}
