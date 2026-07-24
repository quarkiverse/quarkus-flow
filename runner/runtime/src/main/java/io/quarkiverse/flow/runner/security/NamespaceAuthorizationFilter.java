package io.quarkiverse.flow.runner.security;

import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.core.UriInfo;

import org.eclipse.microprofile.jwt.JsonWebToken;
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

    @Inject
    SecurityIdentity securityIdentity;

    private static final String ALL_NAMESPACES = "*";

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
     * <li>If the identity has the admin role → all namespaces are allowed.</li>
     * <li>If the identity is OIDC-authenticated and the authorized namespace set
     * is null, empty, or contains only blank values → access is denied.</li>
     * <li>If the identity is not OIDC-authenticated and the authorized namespace
     * set is null or empty → all namespaces are allowed.</li>
     * <li>If the authorized namespace set contains the exact value {@code "*"}
     * → all namespaces are allowed.</li>
     * <li>If the authorized namespace set contains the requested namespace
     * → access is allowed.</li>
     * <li>Otherwise → access is denied with {@code 403 Forbidden}.</li>
     * </ul>
     *
     * @param namespace the namespace to validate access for
     * @throws ForbiddenException if the current user is not authorized for the namespace
     */
    private void validateNamespaceAccess(String namespace) {
        // Admin bypass applies to every authentication mechanism.
        if (securityIdentity.hasRole(AuthzConsts.ROLE_ADMIN)) {
            return;
        }

        Set<String> authorizedNamespaces = namespaceAuthzService.getAuthorizedNamespaces();

        boolean oidcIdentity = securityIdentity.getPrincipal() instanceof JsonWebToken;

        if (oidcIdentity && hasNoAuthorizedNamespaces(authorizedNamespaces)) {
            throw new ForbiddenException(
                    "The authenticated OIDC identity has no authorized namespaces");
        }

        // If authorized namespaces is null or empty → all namespaces allowed ONLY for non-OIDC.
        if (authorizedNamespaces == null || authorizedNamespaces.isEmpty()) {
            return;
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
     * A namespace set is considered empty when it is:
     * <ul>
     * <li>{@code null}</li>
     * <li>Empty</li>
     * <li>Composed only of {@code null}, empty, or blank values</li>
     * </ul>
     * <p>
     * This helper does not evaluate the admin role or authentication mechanism.
     * Those checks must be performed by the caller.
     *
     * @param authorizedNamespaces the namespaces authorized for the current identity,
     *        or {@code null} when no namespace information is available
     * @return {@code true} if no non-blank authorized namespace is present;
     *         otherwise {@code false}
     */
    private boolean hasNoAuthorizedNamespaces(
            Set<String> authorizedNamespaces) {

        return authorizedNamespaces == null
                || authorizedNamespaces.isEmpty()
                || authorizedNamespaces.stream()
                        .allMatch(value -> value == null || value.isBlank());
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

    /**
     * Determines whether the current identity is an OIDC-authenticated, non-admin
     * user with no authorized namespaces.
     * <p>
     * This condition is used to distinguish OIDC users who are missing the
     * configured namespace claim from identities in other security modes where
     * an empty or absent namespace set may represent unrestricted access.
     *
     * @param authorizedNamespaces the namespaces resolved for the current identity,
     *        or {@code null} if no namespace information is available
     * @return {@code true} when the identity uses OIDC, does not have the admin
     *         role, and has no authorized namespaces; otherwise {@code false}
     */
    private boolean isOidcIdentityWithoutNamespaceRestriction(Set<String> authorizedNamespaces) {
        return securityIdentity.getPrincipal() instanceof JsonWebToken
                && !securityIdentity.hasRole(AuthzConsts.ROLE_ADMIN)
                && (authorizedNamespaces == null
                        || authorizedNamespaces.isEmpty());
    }

}
