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

    private final Map<WorkflowDefinitionId, Workflow> workflows = new ConcurrentHashMap<>();

    @Inject
    WorkflowApplication app;

    private volatile boolean warmedUp = false;

    public static WorkflowRegistry current() {
        return Arc.container().instance(WorkflowRegistry.class).get();
    }

    public Collection<Workflow> all() {
        ensureWarmup();
        return List.copyOf(workflows.values());
    }

    public int count() {
        ensureWarmup();
        return workflows.size();
    }

    public Optional<WorkflowDefinition> lookup(WorkflowDefinitionId id) {
        ensureWarmup();
        Optional<WorkflowDefinition> def = Optional.ofNullable(app.workflowDefinitions().get(id));
        def.ifPresent(wf -> this.workflows.computeIfAbsent(id, k -> wf.workflow()));
        return def;
    }

    public Optional<Workflow> lookupDescriptor(WorkflowDefinitionId id) {
        ensureWarmup();
        return Optional.ofNullable(workflows.get(id));
    }

    public WorkflowDefinition register(Flowable flowable) {
        LOG.info("Registering workflow {}", flowable.descriptor().getDocument().getName());
        final WorkflowDefinition definition = app.workflowDefinition(addFlowableMetadata(flowable));
        checkDuplicatedWorkflow(definition);
        return app.workflowDefinition(addFlowableMetadata(flowable));
    }

    public WorkflowDefinition register(Workflow workflow) {
        LOG.info("Registering workflow {}", workflow.getDocument().getName());
        final WorkflowDefinition definition = app.workflowDefinition(workflow);
        checkDuplicatedWorkflow(definition);
        return definition;
    }

    private void checkDuplicatedWorkflow(WorkflowDefinition definition) {
        WorkflowDefinitionId id = WorkflowDefinitionId.of(definition.workflow());
        Workflow previous = workflows.putIfAbsent(id, definition.workflow());
        if (previous != null) {
            LOG.warn(
                    "Duplicate workflow detected {}. Please remove the duplicate definition to prevent execution collisions or unexpected behavior during runtime.",
                    id);
        }
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

    public void cacheDescriptor(Workflow workflow) {
        workflows.put(WorkflowDefinitionId.of(workflow), workflow);
    }

    private void ensureWarmup() {
        if (warmedUp) {
            return;
        }
        synchronized (this) {
            if (warmedUp) {
                return;
            }
            var container = Arc.container();
            LOG.info("Warming up workflow registry");
            for (InstanceHandle<WorkflowDefinition> handle : container.listAll(WorkflowDefinition.class)) {
                try {
                    // This triggers the synthetic bean's supplier (WorkflowDefinitionRecorder)
                    WorkflowDefinition def = handle.get();
                    Workflow wf = def.workflow();
                    WorkflowDefinitionId id = WorkflowDefinitionId.of(wf);
                    workflows.putIfAbsent(id, wf);
                    LOG.info("Warmed Workflow {} registered with id {}", wf, id);
                } catch (Exception e) {
                    LOG.warn("Flow: Failed to warm up WorkflowDefinition from {}",
                            handle.getBean().getIdentifier(), e);
                }
            }
            warmedUp = true;
        }
    }

}
