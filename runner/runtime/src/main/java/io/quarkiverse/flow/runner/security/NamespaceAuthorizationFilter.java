package io.quarkiverse.flow.runner.security;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.core.UriInfo;

import org.jboss.resteasy.reactive.server.ServerRequestFilter;

import io.quarkus.arc.Unremovable;

/**
 * Modern RESTEasy Reactive filter that enforces namespace-level authorization (ABAC).
 * <p>
 * This filter intercepts all requests, extracts the namespace from the request URI, and
 * delegates the authorization decision to {@link NamespaceAuthorizationService}. Namespace can
 * be specified as:
 * <ul>
 * <li>Path parameter: {@code /runner/exec/{namespace}/...}</li>
 * <li>Query parameter: {@code /runner/definitions?namespace=...}</li>
 * </ul>
 * <p>
 * If no namespace is specified in the request (e.g., {@code GET /runner/definitions}),
 * the filter allows the request through and the resource method handles filtering
 * by authorized namespaces.
 *
 * @see NamespaceAuthorizationService
 */
@FlowRunnerEndpoint
@Unremovable
@ApplicationScoped
public class NamespaceAuthorizationFilter {

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
        String namespace = extractNamespaceFromUri();
        if (namespace == null || namespace.isBlank()) {
            return;
        }

        if (!namespaceAuthzService.isNamespaceAuthorized(namespace)) {
            throw new ForbiddenException(
                    "The authenticated identity is not authorized for namespace: " + namespace);
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
