package io.quarkiverse.flow.runner.security.kubernetes;

import static io.quarkiverse.flow.runner.security.AuthzConsts.CLAIM_NAMESPACES;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.flow.runner.FlowRunnerConfig;
import io.quarkiverse.flow.runner.security.AuthzConsts;
import io.quarkus.arc.Unremovable;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
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
 * {@code quarkus.flow.runner.security.service.kubernetes.trusted-service-accounts}
 * (full Kubernetes usernames such as {@code system:serviceaccount:infrans:gateway})
 * get the {@code *} (all) namespaces authorization attribute; all others are
 * scoped to their own namespace.
 * <p>
 * Consulted before the default authentication priority band (see
 * {@link #getPriority()}): Quarkus consults mechanisms in priority order and
 * ties are resolved nondeterministically, so this composable mechanism must
 * get the chance to handle ServiceAccount-shaped Bearer tokens before
 * catch-all mechanisms such as the NONE-mode permit-all mechanism (which never
 * abstains) answer the request. It abstains on any other credential, so it
 * never competes with the API-key or OIDC mechanisms.
 *
 * @see <a href="https://github.com/quarkiverse/quarkus-flow/issues/1160">Issue #1160</a>
 */
@ApplicationScoped
@Unremovable
@Priority(Priorities.AUTHENTICATION + 100)
public class KubernetesServiceAccountAuthenticationMechanism implements HttpAuthenticationMechanism {

    private static final Logger LOG = LoggerFactory.getLogger(KubernetesServiceAccountAuthenticationMechanism.class);

    /**
     * RoutingContext attribute set in {@link #authenticate} when the request
     * carries an SA-shaped Bearer token (i.e. this mechanism actually engaged
     * for that request). {@link #getChallenge} only issues a challenge when
     * the attribute is present, so abstained requests never get this
     * mechanism's Bearer challenge — other mechanisms' challenges stay
     * authoritative for their own credentials.
     */
    static final String ENGAGED_ATTRIBUTE = KubernetesServiceAccountAuthenticationMechanism.class.getName()
            + ".engaged";

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

        // This mechanism engaged for the request: remember it so getChallenge
        // only challenges requests it actually handled (never abstained ones).
        context.put(ENGAGED_ATTRIBUTE, Boolean.TRUE);

        // 4. Cache hit → use it; otherwise ask the API server (fail closed).
        Optional<TokenReviewClient.TokenReviewResult> cached = tokenReviewCache.get(token);
        if (cached.isPresent()) {
            TokenReviewClient.TokenReviewResult result = cached.get();
            if (!result.authenticated()) {
                return Uni.createFrom().failure(new AuthenticationFailedException("Invalid ServiceAccount token"));
            }
            return Uni.createFrom().item(buildIdentity(result));
        }

        // 5. Miss → review off the event loop. The TokenReview call is a
        // blocking fabric8 client request (bounded by TokenReviewClient's
        // request timeout); Quarkus invokes authenticate on the vert.x event
        // loop, so the review must run on a worker thread to avoid stalling
        // the Runner (and starving the loop under repeated requests).
        return Uni.createFrom().item(() -> {
            try {
                return tokenReviewClient.review(token);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        })
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .onFailure().transform(e -> {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    LOG.debug("TokenReview request failed for an incoming ServiceAccount token", cause);
                    return new AuthenticationFailedException(
                            "Kubernetes API server unavailable: " + cause.getMessage());
                })
                .onItem().invoke(result -> tokenReviewCache.put(token, result, result.expiresAt()))
                .onItem().transform(result -> {
                    // 6. Not authenticated → fail.
                    if (!result.authenticated()) {
                        throw new AuthenticationFailedException("Invalid ServiceAccount token");
                    }
                    // 7. Authenticated → build the identity.
                    return buildIdentity(result);
                });
    }

    /**
     * Consulted before the default authentication priority band: Quarkus
     * consults mechanisms ordered by this value (the default of 1000 is shared
     * by every mechanism that does not override it, and ties are resolved
     * nondeterministically), so this composable mechanism must outrank
     * catch-all mechanisms such as the NONE-mode permit-all mechanism (which
     * never abstains) to get the chance to handle ServiceAccount-shaped Bearer
     * tokens.
     */
    @Override
    public int getPriority() {
        return Priorities.AUTHENTICATION + 100;
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context) {
        // Only challenge requests this mechanism actually engaged on (SA-shaped
        // Bearer token seen in authenticate); abstained requests must surface
        // the challenge of whichever mechanism owns them.
        if (!isActive() || context.get(ENGAGED_ATTRIBUTE) == null) {
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
        if (username != null && config.security().service().kubernetes().trustedServiceAccounts()
                .map(trusted -> trusted.contains(username))
                .orElse(false)) {
            return Set.of(AuthzConsts.ALL_NAMESPACES);
        }
        String namespace = TokenReviewClient.parseNamespace(username);
        return namespace != null ? Set.of(namespace) : Set.of();
    }
}
