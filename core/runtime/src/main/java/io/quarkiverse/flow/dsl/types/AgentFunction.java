package io.quarkiverse.flow.dsl.types;

/**
 * A {@link FilterFunction} whose body invokes an agent.
 * <p>
 * Tasks created with the Java DSL
 * {@link io.quarkiverse.flow.dsl.FlowDSL#agent(String, UniqueIdBiFunction) agent(..)} hold a function of this type,
 * which is what makes the task recognizable as an agent invocation. Nothing is written to the workflow document:
 * integrations such as OpenTelemetry read the function from the {@code call: Java} task
 * ({@link CallJava#functionOf(io.serverlessworkflow.api.types.TaskBase)}) and type the task's span according to the
 * GenAI semantic conventions, using the task name as the agent name.
 * <p>
 * The runtime treats it exactly like any other {@link FilterFunction}.
 */
@FunctionalInterface
public interface AgentFunction<T, R> extends FilterFunction<T, R> {
}
