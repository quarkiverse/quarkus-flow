package io.quarkiverse.flow.runner.security;

import static io.quarkiverse.flow.runner.security.AuthzConsts.CLAIM_NAMESPACES;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;

import org.jboss.logging.Logger;

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
 * Authenticates requests carrying a Kubernetes ServiceAccount token.
 * <p>
 * The mechanism is composable: it only handles requests whose
 * {@code Authorization: Bearer} token <em>looks like</em> a Kubernetes
 * ServiceAccount JWT (unverified {@link ServiceAccountTokenPeek} peek, routing
 * only). Other bearer tokens (for example OIDC tokens) and absent credentials
 * are left to other mechanisms.
 * <p>
 * Authentication itself is delegated to the Kubernetes API server through the
 * TokenReview API (see {@link TokenReviewClient}); the result is cached (see
 * {@link TokenReviewCache}). Failures to reach the API server fail closed.
 * <p>
 * Authenticated ServiceAccounts receive the
 * {@link AuthzConsts#ROLE_SERVICE_READER} role. ServiceAccounts listed in
 * {@code quarkus.flow.runner.security.embed.kubernetes.trusted-service-accounts}
 * (in {@code namespace/name} format) get the {@code *} (all) namespaces
 * authorization attribute; all others are scoped to their own namespace.
 *
 * @see <a href="https://github.com/quarkiverse/quarkus-flow/issues/1160">Issue #1160</a>
 */
@ApplicationScoped
@Unremovable
@Priority(Priorities.AUTHENTICATION)
public class KubernetesServiceAccountAuthenticationMechanism implements HttpAuthenticationMechanism {

    private static final Logger LOG = Logger.getLogger(KubernetesServiceAccountAuthenticationMechanism.class);

    /**
     * Fallback cache lifetime for positive TokenReview results that omit the
     * expiration timestamp.
     */
    static final Duration POSITIVE_CACHE_FALLBACK = Duration.ofMinutes(5);

    /**
     * Cache lifetime for negative (not authenticated) TokenReview results.
     */
    static final Duration NEGATIVE_CACHE_TTL = Duration.ofMinutes(1);

    @Inject
    FlowRunnerConfig config;

    @Inject
    TokenReviewClient tokenReviewClient;

    @Inject
    TokenReviewCache tokenReviewCache;

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext context, IdentityProviderManager identityProviderManager) {
        // 1. Only handle requests when the mechanism is active.
        if (!isActive()) {
            return Uni.createFrom().nullItem();
        }

        // 2. No Bearer header → abstain (other mechanisms/anonymous handle it).
        String authHeader = context.request().getHeader("Authorization");
        if (authHeader == null || !authHeader.regionMatches(true, 0, "bearer ", 0, 7)) {
            return Uni.createFrom().nullItem();
        }
        final String token = authHeader.substring(7).trim();
        if (token.isEmpty()) {
            return Uni.createFrom().nullItem();
        }

        // 3. Not a ServiceAccount-shaped JWT → abstain (garbage/OIDC-token path).
        if (ServiceAccountTokenPeek.peek(token).isEmpty()) {
            return Uni.createFrom().nullItem();
        }

        // 4. Cache hit → use it; otherwise ask the API server (fail closed).
        Optional<TokenReviewClient.TokenReviewResult> cached = tokenReviewCache.get(token);
        if (cached.isPresent()) {
            TokenReviewClient.TokenReviewResult result = cached.get();
            if (!result.authenticated()) {
                return Uni.createFrom().failure(new AuthenticationFailedException("Invalid ServiceAccount token"));
            }
            return Uni.createFrom().item(buildIdentity(result));
        }

        final TokenReviewClient.TokenReviewResult result;
        try {
            result = tokenReviewClient.review(token);
        } catch (Exception e) {
            LOG.debugf(e, "TokenReview request failed for an incoming ServiceAccount token");
            return Uni.createFrom().failure(
                    new AuthenticationFailedException("Kubernetes API server unavailable: " + e.getMessage()));
        }
        tokenReviewCache.put(token, result, cacheExpiry(result));

        // 5. Not authenticated → fail.
        if (!result.authenticated()) {
            return Uni.createFrom().failure(new AuthenticationFailedException("Invalid ServiceAccount token"));
        }

        // 6. Authenticated → build the identity.
        return Uni.createFrom().item(buildIdentity(result));
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context) {
        if (!isActive()) {
            return Uni.createFrom().nullItem();
        }
        return Uni.createFrom().item(new ChallengeData(401, "Bearer", "Invalid or missing ServiceAccount token"));
    }

    /**
     * Whether the mechanism is active for the current configuration.
     * Package-private and overridable as a test seam.
     */
    boolean isActive() {
        return KubernetesInCluster.isMechanismActive(config);
    }

    /**
     * Computes the cache expiry for a TokenReview result: positive results are
     * cached until the verified expiration (with a fallback when the API
     * server omits it); negative results are cached briefly.
     */
    private static Instant cacheExpiry(TokenReviewClient.TokenReviewResult result) {
        if (!result.authenticated()) {
            return Instant.now().plus(NEGATIVE_CACHE_TTL);
        }
        return result.expiresAt() != null ? result.expiresAt()
                : Instant.now().plus(POSITIVE_CACHE_FALLBACK);
    }

    /**
     * Builds the {@link SecurityIdentity} for a TokenReview result.
     * <p>
     * Trusted ServiceAccounts get the {@code *} (all) namespaces attribute;
     * untrusted ones are scoped to their own namespace (an empty set when the
     * namespace cannot be parsed, so the namespace authorization layer denies
     * access).
     */
    private SecurityIdentity buildIdentity(TokenReviewClient.TokenReviewResult result) {
        String username = result.username();
        Set<String> namespaces = namespacesFor(username);

        return QuarkusSecurityIdentity.builder()
                .setPrincipal(() -> username)
                .addRole(AuthzConsts.ROLE_SERVICE_READER)
                .addAttribute(CLAIM_NAMESPACES, namespaces)
                .build();
    }

    private Set<String> namespacesFor(String username) {
        if (username != null && config.security().embed().kubernetes().trustedServiceAccounts()
                .map(trusted -> trusted.contains(peekNamespacedName(username)))
                .orElse(false)) {
            return Set.of(AuthzConsts.ALL_NAMESPACES);
        }
        String namespace = TokenReviewClient.parseNamespace(username);
        return namespace != null ? Set.of(namespace) : Set.of();
    }

    /**
     * Formats a ServiceAccount username as the {@code namespace/name} key used
     * by the trusted-service-accounts configuration.
     */
    private static String peekNamespacedName(String username) {
        String namespace = TokenReviewClient.parseNamespace(username);
        if (namespace == null) {
            return username;
        }
        String[] parts = username.split(":");
        return parts.length == 4 ? namespace + "/" + parts[3] : username;
    }
}
