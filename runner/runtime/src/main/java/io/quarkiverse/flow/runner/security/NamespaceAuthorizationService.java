package io.quarkiverse.flow.runner.security;

import static io.quarkiverse.flow.runner.security.AuthzConsts.CLAIM_NAMESPACES;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import org.eclipse.microprofile.jwt.JsonWebToken;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.flow.runner.FlowRunnerConfig;
import io.quarkus.arc.Unremovable;
import io.quarkus.security.identity.SecurityIdentity;

/**
 * Service for deciding namespace-level authorization (ABAC) for the current security context.
 * <p>
 * {@link #isNamespaceAuthorized(String)} is the single source of truth for this decision;
 * every namespace-aware endpoint and helper should call it instead of re-implementing the policy.
 *
 * @see io.quarkiverse.flow.runner.security.ApiKeyAuthenticationMechanism
 */
@ApplicationScoped
@Unremovable
public class NamespaceAuthorizationService {

    @Inject
    FlowRunnerConfig config;

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    ObjectMapper objectMapper;

    /**
     * Determines whether the current identity is authorized to access the given namespace.
     * <p>
     * Authorization logic:
     * <ul>
     * <li>If namespace validation is disabled ({@code quarkus.flow.runner.security.namespace.validate=false}),
     * every namespace is allowed.</li>
     * <li>If the identity has the {@code flow-admin} role, every namespace is allowed.</li>
     * <li>If the identity's authorized-namespace set is null, empty, or contains only blank
     * values, no namespace is allowed.</li>
     * <li>If the authorized-namespace set contains the exact value {@code "*"} or the requested
     * namespace, access is allowed.</li>
     * <li>Otherwise, access is denied.</li>
     * </ul>
     * <p>
     * Namespace matching is exact and case-sensitive; values such as {@code "team-*"}, {@code "my*"},
     * or {@code "**"} are not wildcard expressions.
     *
     * @param namespace the namespace to check access for
     * @return {@code true} if the current identity may access the namespace, {@code false} otherwise
     */
    public boolean isNamespaceAuthorized(String namespace) {
        if (!config.security().namespace().validate()) {
            return true;
        }
        if (securityIdentity.hasRole(AuthzConsts.ROLE_ADMIN)) {
            return true;
        }
        Set<String> authorizedNamespaces = getAuthorizedNamespaces();
        if (hasNoAuthorizedNamespaces(authorizedNamespaces)) {
            return false;
        }
        return authorizedNamespaces.contains(AuthzConsts.ALL_NAMESPACES)
                || authorizedNamespaces.contains(namespace);
    }

    private boolean hasNoAuthorizedNamespaces(Set<String> authorizedNamespaces) {
        return authorizedNamespaces == null
                || authorizedNamespaces.isEmpty()
                || authorizedNamespaces.stream().allMatch(ns -> ns == null || ns.isBlank());
    }

    /**
     * Extracts and normalizes the raw namespace claim for the current identity.
     * <p>
     * This is a data accessor only - it does not apply any authorization policy. Checks, in order:
     * <ol>
     * <li>Standard {@code "namespaces"} attribute (set by API_KEY authentication)</li>
     * <li>Configured claim name from {@code security.namespace.claim} (for OIDC)</li>
     * </ol>
     * An empty result simply means no namespace claim was found; it carries no authorization
     * meaning by itself. Use {@link #isNamespaceAuthorized(String)} for authorization decisions.
     *
     * @return the raw set of namespace names found on the identity, or an empty set if none
     */
    Set<String> getAuthorizedNamespaces() {
        // Try standard "namespaces" attribute (API_KEY mode)
        Object attr = securityIdentity.getAttribute(CLAIM_NAMESPACES);

        if (attr == null) {
            // Fall back to configured claim name
            String claimName = config.security().namespace().claim();

            // Try as attribute first (API_KEY mode)
            attr = securityIdentity.getAttribute(claimName);

            // If not found, try to extract from JWT token (OIDC mode)
            if (attr == null) {
                attr = extractFromJwt(securityIdentity, claimName);
            }
        }

        if (attr == null) {
            return Set.of();
        }

        return convertToSet(attr);
    }

    private Object extractFromJwt(SecurityIdentity securityIdentity, String claimName) {
        try {
            if (securityIdentity.getPrincipal() instanceof JsonWebToken jwt) {
                return jwt.getClaim(claimName);
            }
        } catch (NoClassDefFoundError e) {
            // quarkus-oidc not present - this is expected for API_KEY mode
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Set<String> convertToSet(Object attr) {
        if (attr instanceof Collection<?> collection) {
            return collection.stream()
                    .map(this::normalizeNamespaceValue)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toUnmodifiableSet());
        }

        if (attr instanceof JsonNode jsonNode) {
            if (jsonNode.isNull() || jsonNode.isMissingNode()) {
                return Set.of();
            }

            if (jsonNode.isArray()) {
                return StreamSupport.stream(jsonNode.spliterator(), false)
                        .map(this::normalizeNamespaceValue)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toUnmodifiableSet());
            }

            String value = normalizeNamespaceValue(jsonNode);
            return value == null ? Set.of() : Set.of(value);
        }

        if (attr instanceof String rawValue) {
            String value = rawValue.trim();

            if (value.isEmpty()) {
                return Set.of();
            }

            // JSON array represented as a string.
            if (value.startsWith("[") && value.endsWith("]")) {
                try {
                    JsonNode jsonNode = objectMapper.readTree(value);

                    if (jsonNode.isArray()) {
                        return StreamSupport.stream(
                                jsonNode.spliterator(),
                                false)
                                .map(this::normalizeNamespaceValue)
                                .filter(Objects::nonNull)
                                .collect(Collectors.toUnmodifiableSet());
                    }
                } catch (Exception ignored) {
                    // Not valid JSON; continue with the other formats.
                }
            }

            if (value.contains(",")) {
                return Arrays.stream(value.split(","))
                        .map(String::trim)
                        .filter(namespace -> !namespace.isEmpty())
                        .collect(Collectors.toUnmodifiableSet());
            }

            return Set.of(value);
        }

        String value = normalizeNamespaceValue(attr);
        return value == null ? Set.of() : Set.of(value);
    }

    private String claimValueAsString(Object value) {
        if (value == null || value == JsonValue.NULL) {
            return null;
        }

        if (value instanceof JsonString jsonString) {
            return jsonString.getString();
        }

        if (value instanceof JsonNode jsonNode) {
            return jsonNode.isNull() || jsonNode.isMissingNode()
                    ? null
                    : jsonNode.asText();
        }

        return value.toString();
    }

    private String normalizeNamespaceValue(Object value) {
        String namespace = claimValueAsString(value);

        if (namespace == null) {
            return null;
        }

        namespace = namespace.trim();
        return namespace.isEmpty() ? null : namespace;
    }
}
