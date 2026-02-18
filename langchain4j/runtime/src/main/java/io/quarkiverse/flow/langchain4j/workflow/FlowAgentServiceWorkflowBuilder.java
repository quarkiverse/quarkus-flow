package io.quarkiverse.flow.langchain4j.workflow;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.agentic.planner.AgentInstance;
import io.quarkiverse.flow.internal.WorkflowNameUtils;
import io.quarkiverse.flow.internal.WorkflowRegistry;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncDoTaskBuilder;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowDefinitionId;

public class FlowAgentServiceWorkflowBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(FlowAgentServiceWorkflowBuilder.class);
    private final Class<?> agentServiceClass;
    private final String description;
    private final Function<List<AgentInstance>, Consumer<FuncDoTaskBuilder>> taskFactory;
    private final Map<String, WorkflowDefinition> cache = new ConcurrentHashMap<>();

    FlowAgentServiceWorkflowBuilder(Class<?> agentServiceClass, String description,
                                    Function<List<AgentInstance>, Consumer<FuncDoTaskBuilder>> taskFactory) {
        this.agentServiceClass = agentServiceClass;
        this.description = description;
        this.taskFactory = taskFactory;
    }

    public WorkflowDefinition build(List<AgentInstance> agents, String plannerAgentId) {
        return cache.computeIfAbsent(plannerAgentId, __ -> {
            final WorkflowDefinitionId id = WorkflowNameUtils.newId(agentServiceClass);
            final WorkflowRegistry registry = WorkflowRegistry.current();

            FuncWorkflowBuilder builder = FuncWorkflowBuilder.workflow();
            builder.document(d -> d
                    .name(id.name())
                    .namespace(id.namespace())
                    .version(id.version())
                    .summary(description));
            builder.tasks(taskFactory.apply(agents));

            final Workflow topologyWorkflow = builder.build();
            Workflow workflowToRegister = registry.lookupDescriptor(id)
                    .map(descriptor -> {
                        descriptor.getDocument().setName(id.name());
                        descriptor.getDocument().setNamespace(id.namespace());
                        descriptor.getDocument().setVersion(id.version());
                        descriptor.getDocument().setSummary(description);
                        descriptor.setDo(topologyWorkflow.getDo());
                        return descriptor;
                    })
                    .orElse(topologyWorkflow);

            LOG.info("Building LC4J Workflow {}", workflowToRegister.getDocument().getName());
            return registry.register(workflowToRegister);
        });
    }

}
