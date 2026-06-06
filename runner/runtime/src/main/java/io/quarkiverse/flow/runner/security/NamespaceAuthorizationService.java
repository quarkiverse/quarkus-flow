package io.quarkiverse.flow.runner.security;

import static io.quarkiverse.flow.runner.security.AuthzConsts.CLAIM_NAMESPACES;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.jwt.JsonWebToken;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.flow.runner.FlowRunnerConfig;
import io.quarkus.arc.Unremovable;
import io.quarkus.security.identity.SecurityIdentity;

/**
 * Service for retrieving authorized namespaces from the current security context.
 * <p>
 * This service extracts namespace information from {@link SecurityIdentity} attributes
 * that were set by authentication mechanisms during login. It supports multiple authentication
 * modes transparently:
 * <ul>
 * <li><b>API_KEY mode:</b> Namespaces from {@code api-keys.*.namespaces} configuration</li>
 * <li><b>OIDC mode:</b> Namespaces from JWT claim (configurable via {@code security.namespace.claim})</li>
 * <li><b>NONE mode:</b> No namespaces attribute (returns null = all allowed)</li>
 * </ul>
 * <p>
 * The service returns {@code null} or empty set to indicate "all namespaces allowed".
 * A non-empty set restricts access to only those namespaces.
 * <p>
 * This is a pure data provider service with no side effects - it does not throw
 * authorization exceptions. Authorization enforcement is handled by
 * {@link NamespaceAuthorizationFilter}.
 *
 * @see NamespaceAuthorizationFilter
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
     * Gets the set of namespaces the current user is authorized to access.
     * <p>
     * The method checks for namespace information in the following order:
     * <ol>
     * <li>Standard {@code "namespaces"} attribute (set by API_KEY authentication)</li>
     * <li>Configured claim name from {@code security.namespace.claim} (for OIDC)</li>
     * </ol>
     * <p>
     * Return values:
     * <ul>
     * <li>{@code null} - User has access to all namespaces (no restrictions)</li>
     * <li>Empty set - User has access to all namespaces (no restrictions)</li>
     * <li>Non-empty set - User can only access workflows in these specific namespaces</li>
     * </ul>
     *
     * @return set of authorized namespace names, or null/empty if all namespaces are allowed
     */
    public Set<String> getAuthorizedNamespaces() {
        // Try standard "namespaces" attribute (API_KEY mode)
        Object attr = securityIdentity.getAttribute(CLAIM_NAMESPACES);

        if (attr == null) {
            // Fall back to configured claim name
            String claimName = config.security().namespace().claim();

            // Try as attribute first (API_KEY mode)
            attr = securityIdentity.getAttribute(claimName);

            // If not found, try to extract from JWT token (OIDC mode)
            if (attr == null && securityIdentity.getPrincipal() instanceof JsonWebToken jwt) {
                attr = jwt.getClaim(claimName);
            }
        }

        if (attr == null) {
            return null; // All namespaces allowed
        }

        return convertToSet(attr);
    }

    /**
     * Converts namespace attribute value to a Set of namespace names.
     * <p>
     * Handles multiple attribute types from different sources:
     * <ul>
     * <li>{@code Set<String>} - Already a set (from API_KEY mechanism)</li>
     * <li>{@code Collection<String>} - Convert to set (from OIDC JWT array claim)</li>
     * <li>{@code String} - Single namespace or comma-separated list</li>
     * <li>Other - Convert via {@code toString()}</li>
     * </ul>
     * <p>
     * Empty or blank strings return {@code null} to indicate "all namespaces allowed".
     *
     * @param attr the namespace attribute value from SecurityIdentity
     * @return set of namespace names, or null if empty/blank
     */
    @SuppressWarnings("unchecked")
    private Set<String> convertToSet(Object attr) {
        if (attr instanceof Set) {
            return (Set<String>) attr;
        } else if (attr instanceof Collection) {
            return new HashSet<>((Collection<String>) attr);
        } else if (attr instanceof String ns) {
            // Handle JSON array string: ["ns1","ns2"] or ["ns1", "ns2"]
            if (ns.startsWith("[") && ns.endsWith("]")) {
                try {
                    JsonNode jsonNode = objectMapper.readTree(ns);
                    if (jsonNode.isArray()) {
                        return StreamSupport.stream(jsonNode.spliterator(), false)
                                .map(JsonNode::asText)
                                .filter(s -> !s.isBlank())
                                .collect(Collectors.toSet());
                    }
                } catch (Exception e) {
                    // Not valid JSON, fall through to other parsing strategies
                }
            }
            // Handle comma-separated string: ns1,ns2
            if (ns.contains(",")) {
                return Arrays.stream(ns.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isBlank())
                        .collect(Collectors.toSet());
            }
            return ns.isBlank() ? null : Set.of(ns);
        }
        return Set.of(attr.toString());
    }

}
