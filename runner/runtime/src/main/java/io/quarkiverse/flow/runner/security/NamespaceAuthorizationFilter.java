package io.quarkiverse.flow.runner.security;

import java.io.IOException;
import java.util.Set;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.Provider;

import io.quarkiverse.flow.runner.FlowRunnerConfig;
import io.quarkus.arc.Unremovable;
import io.quarkus.security.identity.SecurityIdentity;

/**
 * JAX-RS filter that enforces namespace-level authorization (ABAC).
 * <p>
 * This filter intercepts all requests and validates that the authenticated user
 * has access to the requested namespace. Namespace can be specified as:
 * <ul>
 * <li>Path parameter: {@code /runner/exec/{namespace}/...}</li>
 * <li>Query parameter: {@code /runner/definitions?namespace=...}</li>
 * </ul>
 * <p>
 * Authorization is only enforced when {@code quarkus.flow.runner.security.namespace.validate=true}.
 * <p>
 * If no namespace is specified in the request (e.g., {@code GET /runner/definitions}),
 * the filter allows the request through and the resource method handles filtering
 * by authorized namespaces.
 * <p>
 * Authorized namespaces are extracted from {@link SecurityIdentity} attributes
 * set by authentication mechanisms during login. An empty or null namespace set
 * means the user has access to all namespaces.
 *
 * @see NamespaceAuthorizationService
 */
@Provider
@Unremovable
@Priority(Priorities.AUTHORIZATION)
public class NamespaceAuthorizationFilter implements ContainerRequestFilter {

    @Inject
    FlowRunnerConfig config;

    @Inject
    NamespaceAuthorizationService namespaceAuthzService;

    @Inject
    UriInfo uriInfo;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        if (!config.security().namespace().validate()) {
            return;
        }

        String namespace = extractNamespaceFromUri();
        if (namespace == null || namespace.isBlank()) {
            return;
        }

        validateNamespaceAccess(namespace);
    }

    /**
     * Validates that the current user has access to the specified namespace.
     * <p>
     * Authorization logic:
     * <ul>
     * <li>If authorized namespaces is null or empty → all namespaces allowed (pass)</li>
     * <li>If namespace is in authorized set → allowed (pass)</li>
     * <li>Otherwise → denied (throws 403 Forbidden)</li>
     * </ul>
     *
     * @param namespace the namespace to validate access for
     * @throws ForbiddenException if user does not have access to the namespace
     */
    private void validateNamespaceAccess(String namespace) {
        Set<String> authorizedNamespaces = namespaceAuthzService.getAuthorizedNamespaces();

        // Empty or null = all namespaces allowed
        if (authorizedNamespaces == null || authorizedNamespaces.isEmpty()) {
            return;
        }

        // Check specific namespace
        if (!authorizedNamespaces.contains(namespace)) {
            throw new ForbiddenException("Access denied to namespace: " + namespace);
        }
    }

    /**
     * Extracts namespace from request URI.
     * <p>
     * Checks in order:
     * <ol>
     * <li>Path parameter {@code {namespace}} (e.g., {@code /runner/exec/my-ns/workflow})</li>
     * <li>Query parameter {@code namespace} (e.g., {@code /runner/definitions?namespace=my-ns})</li>
     * </ol>
     *
     * @return the namespace from path or query parameter, or null if not present
     */
    private String extractNamespaceFromUri() {
        MultivaluedMap<String, String> pathParams = uriInfo.getPathParameters();
        String ns = pathParams.getFirst("namespace");
        if (ns == null || ns.isBlank()) {
            MultivaluedMap<String, String> queryParams = uriInfo.getQueryParameters();
            ns = queryParams.getFirst("namespace");
        }
        return ns == null ? null : ns.trim();
    }

}
