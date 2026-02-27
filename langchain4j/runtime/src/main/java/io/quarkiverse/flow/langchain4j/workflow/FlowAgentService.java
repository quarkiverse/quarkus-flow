package io.quarkiverse.flow.langchain4j.workflow;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import io.quarkiverse.flow.internal.WorkflowRegistry;
import io.serverlessworkflow.fluent.func.FuncDoTaskBuilder;

public interface FlowAgentService<T> {

    Function<List<AgentInstance>, Consumer<FuncDoTaskBuilder>> tasksDefinition();

    String description();

    WorkflowRegistry workflowRegistry();

    Class<T> agentServiceClass();

    AgenticSystemTopology topology();
}
