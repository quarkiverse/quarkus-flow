package io.quarkiverse.flow.langchain4j.workflow;

import java.util.List;
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

public class FlowAgentWorkflowBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(FlowAgentWorkflowBuilder.class);
    private final Class<?> agentServiceClass;
    private final String description;
    private final Function<List<AgentInstance>, Consumer<FuncDoTaskBuilder>> taskFactory;
    private final WorkflowRegistry workflowRegistry;

    FlowAgentWorkflowBuilder(Class<?> agentServiceClass, String description,
            Function<List<AgentInstance>, Consumer<FuncDoTaskBuilder>> taskFactory, WorkflowRegistry workflowRegistry) {
        this.agentServiceClass = agentServiceClass;
        this.description = description;
        this.taskFactory = taskFactory;
        this.workflowRegistry = workflowRegistry;
    }

    /**
     * Build a new workflow definition for the given agents of this Agent Workflow Pattern. If already in registry (keyed by
     * WorkflowDefinitionId), return it instead.
     */
    public WorkflowDefinition buildOrGet(List<AgentInstance> agents) {
        final WorkflowDefinitionId id = WorkflowNameUtils.newId(agentServiceClass);
        return workflowRegistry.lookup(id).orElseGet(() -> {
            FuncWorkflowBuilder builder = FuncWorkflowBuilder.workflow();
            builder.document(d -> d
                    .name(id.name())
                    .namespace(id.namespace())
                    .version(id.version())
                    .summary(description));
            builder.tasks(taskFactory.apply(agents));

            final Workflow topologyWorkflow = builder.build();
            Workflow workflowToRegister = workflowRegistry.lookupDescriptor(id)
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
            return workflowRegistry.register(workflowToRegister);
        });
    }

}
