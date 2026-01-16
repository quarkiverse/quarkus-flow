package io.quarkiverse.flow.langchain4j.workflow;

import java.util.function.BiFunction;
import java.util.function.Consumer;

import dev.langchain4j.agentic.planner.InitPlanningContext;
import io.serverlessworkflow.fluent.func.FuncDoTaskBuilder;

public interface FlowAgentService {

    BiFunction<FlowPlanner, InitPlanningContext, Consumer<FuncDoTaskBuilder>> tasksDefinition();

}
