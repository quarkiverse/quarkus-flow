package io.quarkiverse.flow.runner.resources;

import java.util.Collection;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.flow.internal.WorkflowVersionComparator;
import io.quarkiverse.flow.runner.security.NamespaceAuthorizationService;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowDefinitionId;

/**
 * Resolves a {@link WorkflowDefinition} by namespace/name/version, treating a {@code null} or
 * {@value #LATEST} version as "the highest version registered for that namespace/name".
 *
 * <p>
 * Shared by the runner endpoints that accept an optional version path segment, so namespace/name/version
 * resolution stays consistent across execution and instance-listing endpoints.
 */
@ApplicationScoped
public class WorkflowDefinitionLookup {

    public static final String LATEST = "latest";

    @Inject
    WorkflowApplication application;

    @Inject
    NamespaceAuthorizationService namespaceAuth;

    public Collection<WorkflowDefinition> definitions() {
        return application.workflowDefinitions().values().stream()
                .filter(e -> namespaceAuth.isNamespaceAuthorized(e.id().namespace()))
                .toList();
    }

    public WorkflowDefinition find(WorkflowDefinitionId id) {
        if (!namespaceAuth.isNamespaceAuthorized(id.namespace())) {
            throw new SecurityException(
                    "User do not have permission to interact with definitions belonging to namespace " + id.namespace());
        }
        return id.version() == null || id.version().equals(LATEST) ? application.workflowDefinitions().entrySet().stream()
                .filter(entry -> id.name().equals(entry.getKey().name()) && id.namespace().equals(entry.getKey().namespace()))
                .max(new WorkflowVersionComparator())
                .map(Map.Entry::getValue).orElse(null) : application.workflowDefinitions().get(id);
    }
}
