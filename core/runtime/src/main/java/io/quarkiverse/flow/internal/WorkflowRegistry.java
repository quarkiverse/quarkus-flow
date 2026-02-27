package io.quarkiverse.flow.internal;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.FlowMetadata;
import io.quarkiverse.flow.Flowable;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.api.types.WorkflowMetadata;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowDefinitionId;

/**
 * Registry for programmatically look up for registered {@link WorkflowDefinition} in the {@link WorkflowApplication}.
 * Quarkus Flow builds and registry the definitions in build time for any {@link Flowable} classes (the ones users extends via
 * {@link Flow}).
 * <p/>
 * Internally, other extensions may directly inject definitions into the {@link WorkflowApplication}.
 * This registry centralizes all of them in one place with quick, cacheable, structures.
 */
@ApplicationScoped
public class WorkflowRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(WorkflowRegistry.class);

    @Inject
    WorkflowApplication app;

    private Map<WorkflowDefinitionId, Workflow> agenticCache = new ConcurrentHashMap<>();

    public static WorkflowRegistry current() {
        return Arc.container().instance(WorkflowRegistry.class).get();
    }

    public Collection<Workflow> all() {
        return app.workflowDefinitions().values().stream().map(WorkflowDefinition::workflow).toList();
    }

    public int count() {
        return app.workflowDefinitions().size();
    }

    public Optional<WorkflowDefinition> lookup(WorkflowDefinitionId id) {
        return Optional.ofNullable(app.workflowDefinitions().get(id));
    }

    public Optional<Workflow> lookupDescriptor(WorkflowDefinitionId id) {
        Optional<Workflow> workflow = lookup(id).map(WorkflowDefinition::workflow);
        return workflow.isPresent() ? workflow : Optional.ofNullable(agenticCache.get(id));
    }

    public WorkflowDefinition register(Flowable flowable) {
        LOG.info("Registering workflow {}", flowable.descriptor().getDocument().getName());
        return app.workflowDefinition(addFlowableMetadata(flowable));
    }

    public WorkflowDefinition register(Workflow workflow) {
        LOG.info("Registering workflow {}", workflow.getDocument().getName());
        return app.workflowDefinition(workflow);
    }

    private Workflow addFlowableMetadata(final Flowable flowable) {
        final Workflow workflow = flowable.descriptor();
        if (workflow.getDocument().getMetadata() == null) {
            workflow.getDocument().setMetadata(new WorkflowMetadata());
        }
        workflow.getDocument().getMetadata().setAdditionalProperty(FlowMetadata.FLOW_IDENTIFIER_CLASS,
                flowable.identifier());
        return workflow;
    }

    void warmUp() {
        List<InstanceHandle<WorkflowDefinition>> definitionHandles = Arc.container().listAll(WorkflowDefinition.class);
        LOG.info("Warming up {} WorkflowDefinition beans", definitionHandles.size());
        for (InstanceHandle<WorkflowDefinition> handle : definitionHandles) {
            try {
                // This triggers the synthetic bean's supplier (WorkflowDefinitionRecorder)
                handle.get().workflow();
            } catch (Exception e) {
                LOG.warn("Flow: Failed to warm up WorkflowDefinition from {}",
                        handle.getBean().getIdentifier(), e);
            }
        }
    }

    public void cacheDescriptor(Workflow workflow) {
        LOG.debug("Caching workflow descriptor for {}", workflow.getDocument().getName());
        agenticCache.put(WorkflowDefinitionId.of(workflow), workflow);
    }
}
