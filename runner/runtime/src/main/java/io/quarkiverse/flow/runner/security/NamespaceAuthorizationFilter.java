package io.quarkiverse.flow.runner.security;

import static io.quarkiverse.flow.runner.security.AuthzConsts.ALL_NAMESPACES;

import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.core.UriInfo;

import org.jboss.resteasy.reactive.server.ServerRequestFilter;

import io.quarkiverse.flow.runner.FlowRunnerConfig;
import io.quarkus.arc.Unremovable;
import io.quarkus.security.identity.SecurityIdentity;

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
 * Authorized namespaces are extracted from {@link SecurityIdentity} attributes
 * set by the active authentication mechanism.
 * <p>
 * For non-admin identities, a null, empty, or blank namespace set grants no
 * namespace access. The normalized value {@code "*"} grants access to all
 * current and future namespaces. Identities with the {@code flow-admin} role
 * bypass namespace authorization.
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

    @Inject
    SecurityIdentity securityIdentity;

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
     * <li>If the identity has the {@code flow-admin} role, all namespaces are
     * allowed.</li>
     * <li>If the authorized namespace set is null, empty, or contains only blank
     * values, access is denied.</li>
     * <li>If the authorized namespace set contains the exact value {@code "*"},
     * all namespaces are allowed.</li>
     * <li>If the authorized namespace set contains the requested namespace,
     * access is allowed.</li>
     * <li>Otherwise, access is denied with {@code 403 Forbidden}.</li>
     * </ul>
     * <p>
     * Namespace matching is exact and case-sensitive. Values such as
     * {@code "team-*"}, {@code "my*"}, or {@code "**"} are not wildcard
     * expressions.
     *
     * @param namespace the namespace to validate access for
     * @throws ForbiddenException if the current user is not authorized for the
     *         namespace
     */
    private void validateNamespaceAccess(String namespace) {
        // Admin bypass applies to every authentication mechanism.
        if (securityIdentity.hasRole(AuthzConsts.ROLE_ADMIN)) {
            return;
        }

        Set<String> authorizedNamespaces = namespaceAuthzService.getAuthorizedNamespaces();

        if (hasNoAuthorizedNamespaces(authorizedNamespaces)) {
            throw new ForbiddenException(
                    "The authenticated identity has no authorized namespaces");
        }

        if (authorizedNamespaces.contains(ALL_NAMESPACES)
                || authorizedNamespaces.contains(namespace)) {
            return;
        }

        throw new ForbiddenException(
                "The authenticated identity is not authorized for namespace: "
                        + namespace);
    }

    /**
     * Determines whether the authorized namespace set grants no namespace access.
     * <p>
     * A namespace set grants no access when it is:
     * <ul>
     * <li>{@code null}</li>
     * <li>Empty</li>
     * <li>Composed only of {@code null}, empty, or blank values</li>
     * </ul>
     * <p>
     * This helper does not evaluate roles. The caller must apply the
     * {@code flow-admin} bypass before calling this method.
     *
     * @param authorizedNamespaces the configured namespaces for the current
     *        identity, or {@code null} when none are available
     * @return {@code true} when no non-blank authorized namespace is present;
     *         otherwise {@code false}
     */
    private boolean hasNoAuthorizedNamespaces(
            Set<String> authorizedNamespaces) {

        return authorizedNamespaces == null
                || authorizedNamespaces.isEmpty()
                || authorizedNamespaces.stream()
                        .allMatch(namespace -> namespace == null || namespace.isBlank());
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
