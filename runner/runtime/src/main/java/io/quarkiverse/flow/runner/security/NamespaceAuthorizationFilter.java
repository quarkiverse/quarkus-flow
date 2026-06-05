package io.quarkiverse.flow.runner.security;

import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.core.UriInfo;

import org.jboss.resteasy.reactive.server.ServerRequestFilter;

import io.quarkiverse.flow.runner.FlowRunnerConfig;
import io.quarkus.arc.Unremovable;

/**
 * Modern RESTEasy Reactive filter that enforces namespace-level authorization (ABAC).
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
 * Authorized namespaces are extracted from {@link io.quarkus.security.identity.SecurityIdentity} attributes
 * set by authentication mechanisms during login. An empty or null namespace set
 * means the user has access to all namespaces.
 * <p>
 * <strong>Implementation Note:</strong> Uses {@code @ServerRequestFilter} (RESTEasy Reactive modern approach)
 * instead of the legacy {@code ContainerRequestFilter} interface.
 *
 * @see NamespaceAuthorizationService
 */
@FlowRunnerEndpoint
@Unremovable
@ApplicationScoped
public class NamespaceAuthorizationFilter {

    @Inject
    FlowRunnerConfig config;

    @Inject
    NamespaceAuthorizationService namespaceAuthzService;

    @Inject
    UriInfo uriInfo;

    /**
     * Server request filter method that validates namespace access.
     * <p>
     * This method is automatically invoked by RESTEasy Reactive for every request.
     * The {@code @ServerRequestFilter} annotation is the modern, declarative approach
     * that replaces implementing {@code ContainerRequestFilter}.
     *
     * @throws ForbiddenException if user does not have access to the namespace
     */
    @ServerRequestFilter
    public void filter() {
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
        String ns = uriInfo.getPathParameters().getFirst("namespace");
        if (ns == null || ns.isBlank()) {
            ns = uriInfo.getQueryParameters().getFirst("namespace");
        }
        return ns == null ? null : ns.trim();
    }

}
