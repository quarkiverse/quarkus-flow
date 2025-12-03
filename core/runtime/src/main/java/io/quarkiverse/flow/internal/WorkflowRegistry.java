package io.quarkiverse.flow.internal;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.Flowable;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.InstanceHandle;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowDefinitionId;
import io.smallrye.common.annotation.Identifier;

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
    // qualifier -> id (FQCN, workflow file id, or custom alias)
    private final Map<String, WorkflowDefinitionId> byQualifier = new ConcurrentHashMap<>();
    // document.name -> id
    private final Map<String, WorkflowDefinitionId> byDocumentName = new ConcurrentHashMap<>();

    @Inject
    WorkflowApplication app;
    private volatile boolean cdiWarmedUp = false;

    public static WorkflowRegistry current() {
        return Arc.container().instance(WorkflowRegistry.class).get();
    }

    // ---------- Public API ----------

    public Collection<WorkflowDefinition> all() {
        ensureCdiWarmup();
        return app.workflowDefinitions().values();
    }

    public int count() {
        ensureCdiWarmup();
        return app.workflowDefinitions().size();
    }

    public Optional<WorkflowDefinition> lookup(WorkflowDefinitionId id) {
        ensureCdiWarmup();
        return Optional.ofNullable(app.workflowDefinitions().get(id));
    }

    /**
     * Lookup by qualifier (FQCN, file identifier, etc).
     * For non-CDI flows, we fall back to document name == qualifier.
     */
    public Optional<WorkflowDefinition> lookup(String qualifier) {
        ensureCdiWarmup();

        final WorkflowDefinitionId id = byQualifier.get(qualifier);
        if (id != null) {
            return lookup(id);
        }

        final WorkflowDefinition def = findByDocumentNameInApplication(qualifier);
        if (def != null) {
            final WorkflowDefinitionId newId = WorkflowDefinitionId.of(def.workflow());
            byQualifier.putIfAbsent(qualifier, newId);

            final String docName = def.workflow().getDocument().getName();
            if (docName != null && !docName.isBlank()) {
                byDocumentName.putIfAbsent(docName, newId);
            }

            return Optional.of(def);
        }

        return Optional.empty();
    }

    public Optional<WorkflowDefinition> lookup(Class<?> flowClass) {
        return lookup(flowClass.getName());
    }

    public Optional<WorkflowDefinition> lookupByDocumentName(String documentName) {
        ensureCdiWarmup();

        final WorkflowDefinitionId id = byDocumentName.get(documentName);
        if (id != null) {
            return lookup(id);
        }

        final WorkflowDefinition def = findByDocumentNameInApplication(documentName);
        if (def == null) {
            return Optional.empty();
        }

        final WorkflowDefinitionId newId = WorkflowDefinitionId.of(def.workflow());
        byDocumentName.putIfAbsent(documentName, newId);
        return Optional.of(def);
    }

    public WorkflowDefinition register(Workflow describer, String qualifier) {
        ensureCdiWarmup();
        final WorkflowDefinition definition = app.workflowDefinition(describer);
        final WorkflowDefinitionId id = WorkflowDefinitionId.of(definition.workflow());
        byDocumentName.putIfAbsent(describer.getDocument().getName(), id);
        if (qualifier != null && !qualifier.isBlank()) {
            byQualifier.putIfAbsent(qualifier, id);
        }
        return definition;
    }

    // ---------- Internal helpers ----------

    private void ensureCdiWarmup() {
        if (cdiWarmedUp) {
            return;
        }
        synchronized (this) {
            if (cdiWarmedUp) {
                return;
            }
            warmupFromCdiDefinitions();
            cdiWarmedUp = true;
        }
    }

    /**
     * Force CDI `WorkflowDefinition` beans to be instantiated
     * so their synthetic suppliers run and register into WorkflowApplication.
     */
    private void warmupFromCdiDefinitions() {
        ArcContainer container = Arc.container();
        for (InstanceHandle<WorkflowDefinition> handle : container.listAll(WorkflowDefinition.class)) {
            try {
                WorkflowDefinition def = handle.get();
                // Triggers supplier: ensures app.workflowDefinitions() knows about it
                final Workflow wf = def.workflow();
                final WorkflowDefinitionId id = WorkflowDefinitionId.of(wf);
                final String docName = wf.getDocument().getName();

                // map @Identifier value -> id
                handle.getBean().getQualifiers().stream()
                        .filter(a -> a.annotationType().equals(Identifier.class))
                        .map(a -> ((Identifier) a).value())
                        .forEach(q -> byQualifier.putIfAbsent(q, id));

                if (docName != null && !docName.isBlank()) {
                    byDocumentName.putIfAbsent(docName, id);
                }
            } catch (Exception e) {
                LOG.warn("Flow: Failed to warm up WorkflowDefinition from {}", handle.getBean().getIdentifier(), e);
            }
        }
    }

    private WorkflowDefinition findByDocumentNameInApplication(String docName) {
        if (docName == null || docName.isBlank()) {
            return null;
        }
        for (WorkflowDefinition def : app.workflowDefinitions().values()) {
            String name = def.workflow().getDocument().getName();
            if (docName.equals(name)) {
                return def;
            }
        }
        return null;
    }
}
