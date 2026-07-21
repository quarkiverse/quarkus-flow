package io.quarkiverse.flow.dsl;

import java.util.function.Consumer;

import io.quarkiverse.flow.dsl.spi.ConditionalTaskBuilder;
import io.quarkiverse.flow.dsl.spi.FuncTaskTransformations;
import io.serverlessworkflow.fluent.spec.BaseTryTaskBuilder;

public class FuncTryTaskBuilder
        extends BaseTryTaskBuilder<FuncTryTaskBuilder, FuncTaskItemListBuilder>
        implements FuncTaskTransformations<FuncTryTaskBuilder>,
        ConditionalTaskBuilder<FuncTryTaskBuilder> {

    FuncTryTaskBuilder() {
        super(new FuncTaskItemListBuilder(0));
    }

    @Override
    protected FuncTryTaskBuilder self() {
        return this;
    }

    /**
     * Defines the tasks to execute within the try.
     *
     * @param tryHandler a consumer that configures the tasks to be executed in the try
     * @return this builder instance for method chaining
     */
    public FuncTryTaskBuilder tryCatch(Consumer<FuncTaskItemListBuilder> tryHandler) {
        return this.tryHandler(tryHandler);
    }

    /**
     * Defines a catch that handles specific errors and executes tasks when those errors occur.
     *
     * @param catchErrors a consumer that configures which errors to catch (e.g., by type, status
     *        code)
     * @param catchHandler a consumer that configures the tasks to execute when the specified errors
     *        are caught
     * @return this builder instance for method chaining
     */
    public FuncTryTaskBuilder catchError(
            Consumer<CatchErrorsBuilder> catchErrors, Consumer<FuncTaskItemListBuilder> catchHandler) {
        return this.catchHandler(handler -> handler.errorsWith(catchErrors).doTasks(catchHandler));
    }

    /**
     * Defines a conditional catch that executes tasks when a specific condition is met.
     *
     * @param expr a runtime expression that evaluates to a boolean, determining whether to execute
     *        the catch handler
     * @param catchHandler a consumer that configures the tasks to execute when the condition
     *        evaluates to true
     * @return this builder instance for method chaining
     */
    public FuncTryTaskBuilder catchWhen(String expr, Consumer<FuncTaskItemListBuilder> catchHandler) {
        return this.catchHandler(handler -> handler.when(expr).doTasks(catchHandler));
    }

    /**
     * Defines a catch that handles errors of a specific type.
     *
     * @param type the error type to catch (e.g., "java.lang.NullPointerException")
     * @param catchHandler a consumer that configures the tasks to execute when an error of the
     *        specified type is caught
     * @return this builder instance for method chaining
     */
    public FuncTryTaskBuilder catchType(String type, Consumer<FuncTaskItemListBuilder> catchHandler) {
        return this.catchHandler(
                handler -> handler.errorsWith(err -> err.type(type)).doTasks(catchHandler));
    }
}
