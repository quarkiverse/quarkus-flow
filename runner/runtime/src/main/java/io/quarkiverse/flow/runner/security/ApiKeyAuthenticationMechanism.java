package io.quarkiverse.flow.runner.security;

import static io.quarkiverse.flow.runner.security.AuthzConsts.CLAIM_NAMESPACES;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
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

@ApplicationScoped
@Unremovable
@Priority(Priorities.AUTHENTICATION)
@Alternative
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
        Optional<Map.Entry<String, FlowRunnerConfig.Security.ApiKey>> matchingKey = config.security().apiKeys()
                .entrySet().stream()
                .filter(entry -> entry.getValue().secret().equals(apiKey))
                .findFirst();

        if (matchingKey.isPresent()) {
            String keyName = matchingKey.get().getKey();
            Set<String> roles = matchingKey.get().getValue().roles();
            Optional<Set<String>> namespacesOpt = matchingKey.get().getValue().namespaces();

            // Build security identity with principal name and roles
            QuarkusSecurityIdentity.Builder builder = QuarkusSecurityIdentity.builder()
                    .setPrincipal(() -> "api-key:" + keyName);
            roles.forEach(builder::addRole);

            // Add namespaces attribute if configured
            namespacesOpt.ifPresent(namespaces -> {
                if (!namespaces.isEmpty()) {
                    Set<String> nonBlankNs = namespaces.stream()
                            .filter(Objects::nonNull)
                            .filter(ns -> !ns.isBlank())
                            .collect(Collectors.toSet());
                    if (!nonBlankNs.isEmpty()) {
                        builder.addAttribute(CLAIM_NAMESPACES, nonBlankNs);
                    }
                }
            });

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

}
