package io.quarkiverse.flow.langchain4j.workflow;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import dev.langchain4j.agentic.planner.AgentInstance;
import io.serverlessworkflow.fluent.func.FuncDoTaskBuilder;

public interface FlowAgentService {

    Function<List<AgentInstance>, Consumer<FuncDoTaskBuilder>> tasksDefinition();

}
