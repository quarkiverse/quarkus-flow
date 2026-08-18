package io.quarkiverse.flow.runner.security;

import static io.quarkiverse.flow.runner.security.AuthzConsts.CLAIM_NAMESPACES;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;

import io.quarkiverse.flow.runner.FlowRunnerConfig;
import io.quarkus.arc.Unremovable;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;

/**
 * Authenticates requests using configured API keys.
 * <p>
 * Each API key is mapped to a principal, a set of roles, and a normalized set
 * of authorized namespaces. The namespace configuration is propagated to the
 * resulting {@link SecurityIdentity} through the
 * {@link AuthzConsts#CLAIM_NAMESPACES} attribute.
 * <p>
 * Namespace values are normalized by trimming surrounding whitespace and
 * removing null or blank entries. Consequently, values such as {@code " * "}
 * are normalized to the exact wildcard value {@code "*"}.
 * <p>
 * This authentication mechanism does not make namespace authorization
 * decisions. The exact value {@code "*"} and explicit namespace values are
 * interpreted by the namespace authorization layer.
 */
@ApplicationScoped
@Unremovable
@Priority(Priorities.AUTHENTICATION)
public class ApiKeyAuthenticationMechanism implements HttpAuthenticationMechanism {

    @Inject
    FlowRunnerConfig config;

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext context, IdentityProviderManager identityProviderManager) {
        // Only handle requests when API_KEY mode is configured
        if (config.security().type() != FlowRunnerConfig.Security.Type.API_KEY) {
            return Uni.createFrom().nullItem();
        }

        // Extract Authorization header
        String authHeader = context.request().getHeader("Authorization");

        // No auth header → anonymous identity (let @RolesAllowed reject if needed)
        if (authHeader == null || authHeader.isBlank()) {
            return Uni.createFrom().nullItem();
        }

        // Validate Bearer token format (case-insensitive)
        if (!authHeader.regionMatches(true, 0, "bearer ", 0, 7) || authHeader.length() <= 7) {
            return Uni.createFrom().failure(
                    new AuthenticationFailedException(
                            "Invalid Authorization header format. Expected: Bearer <api-key>"));
        }

        // Extract API key (strip "Bearer " prefix and trim)
        final String apiKey = authHeader.substring(7).trim();

        if (apiKey.isEmpty()) {
            return Uni.createFrom().failure(
                    new AuthenticationFailedException("Empty API key"));
        }

        // Find matching API key configuration
        final byte[] submittedKeyBytes = apiKey.getBytes(StandardCharsets.UTF_8);
        Optional<Map.Entry<String, FlowRunnerConfig.Security.ApiKey>> matchingKey = config.security().apiKeys()
                .entrySet().stream()
                .filter(entry -> MessageDigest.isEqual(entry.getValue().secret().getBytes(StandardCharsets.UTF_8),
                        submittedKeyBytes))
                .findFirst();

        if (matchingKey.isPresent()) {
            String keyName = matchingKey.get().getKey();

            FlowRunnerConfig.Security.ApiKey apiKeyConfig = matchingKey.get().getValue();

            Set<String> roles = apiKeyConfig.roles();
            Set<String> namespaces = normalizeNamespaces(apiKeyConfig.namespaces().orElse(Set.of("*")));

            // Build security identity with principal name and roles.
            QuarkusSecurityIdentity.Builder builder = QuarkusSecurityIdentity.builder()
                    .setPrincipal(() -> "api-key:" + keyName);

            roles.forEach(builder::addRole);

            // Always propagate the normalized namespace set, including an empty set.
            // The namespace authorization layer interprets "*" and enforces access.
            builder.addAttribute(CLAIM_NAMESPACES, namespaces);

            return Uni.createFrom().item(builder.build());
        }

        // API key not found in configuration
        return Uni.createFrom().failure(
                new AuthenticationFailedException("Invalid API key"));
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context) {
        if (config.security().type() != FlowRunnerConfig.Security.Type.API_KEY) {
            return Uni.createFrom().nullItem();
        }
        return Uni.createFrom().item(new ChallengeData(401, "Bearer", "Invalid or missing API Key"));
    }

    /**
     * Normalizes the namespace values configured for an API key.
     * <p>
     * Null and blank entries are removed, and surrounding whitespace is trimmed.
     * An absent or empty namespace set is normalized to an empty set. The exact
     * wildcard value {@code "*"} is preserved; values such as {@code " * "} are
     * normalized to {@code "*"}.
     * <p>
     * This method does not interpret namespace permissions. In particular, it
     * does not implement wildcard matching. The normalized values are propagated
     * to the {@link SecurityIdentity}, and the authorization layer determines
     * whether a namespace is allowed.
     *
     * @param namespaces the configured namespace values
     * @return an immutable, normalized namespace set
     */
    private Set<String> normalizeNamespaces(Set<String> namespaces) {
        if (namespaces == null || namespaces.isEmpty()) {
            return Set.of();
        }

        return namespaces.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(namespace -> !namespace.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
