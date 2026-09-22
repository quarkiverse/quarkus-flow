package io.quarkiverse.flow.runner.resources;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.flow.internal.WorkflowVersionComparator;
import io.quarkiverse.flow.runner.FlowRunnerConfig;
import io.quarkiverse.flow.runner.security.AuthzConsts;
import io.quarkiverse.flow.runner.security.NamespaceAuthorizationService;
import io.quarkus.security.identity.SecurityIdentity;
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

    @Inject
    FlowRunnerConfig config;

    @Inject
    SecurityIdentity securityIdentity;

    public Collection<WorkflowDefinition> definitions() {
        if (config.security().namespace().validate() && !securityIdentity.hasRole(AuthzConsts.ROLE_ADMIN)) {
            Set<String> authorizedNamespaces = namespaceAuth.getAuthorizedNamespaces();
            if (authorizedNamespaces != null && !authorizedNamespaces.isEmpty()
                    && !authorizedNamespaces.contains(AuthzConsts.ALL_NAMESPACES)) {
                return application.workflowDefinitions()
                        .values().stream().filter(e -> authorizedNamespaces.contains(e.id().namespace())).toList();
            }
        }
        return application.workflowDefinitions().values();
    }

    public WorkflowDefinition find(WorkflowDefinitionId id) {
        if (config.security().namespace().validate() && !securityIdentity.hasRole(AuthzConsts.ROLE_ADMIN)) {
            Set<String> authorizedNamespaces = namespaceAuth.getAuthorizedNamespaces();
            if (authorizedNamespaces != null && !authorizedNamespaces.isEmpty()
                    && !authorizedNamespaces.contains(AuthzConsts.ALL_NAMESPACES) &&
                    !authorizedNamespaces.contains(id.namespace())) {
                throw new SecurityException(
                        "User do not have permission to interact with definitions belonging to namespace " + id.namespace());
            }
        }
        return id.version() == null || id.version().equals(LATEST) ? application.workflowDefinitions().entrySet().stream()
                .filter(entry -> id.name().equals(entry.getKey().name()) && id.namespace().equals(entry.getKey().namespace()))
                .max(new WorkflowVersionComparator())
                .map(Map.Entry::getValue).orElse(null) : application.workflowDefinitions().get(id);
    }
}
