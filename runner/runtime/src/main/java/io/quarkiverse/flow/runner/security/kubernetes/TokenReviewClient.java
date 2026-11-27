package io.quarkiverse.flow.runner.security.kubernetes;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.authentication.TokenReview;
import io.fabric8.kubernetes.api.model.authentication.TokenReviewBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.arc.Unremovable;

/**
 * Thin adapter over the fabric8 Kubernetes client for the TokenReview API.
 * <p>
 * Validates incoming Kubernetes ServiceAccount tokens by asking the cluster's
 * API server to review them:
 *
 * <pre>
 * POST /apis/authentication.k8s.io/v1/tokenreviews
 * {"spec":{"token":"&lt;incoming token&gt;"}}
 * </pre>
 * <p>
 * The API server (not this class) performs the actual signature and claim
 * validation; the returned {@link TokenReviewResult} carries the verified
 * identity.
 * <p>
 * The call is made through the {@link KubernetesClient} bean provided by the
 * {@code quarkus-kubernetes-client} extension, which resolves in-cluster
 * configuration (API server URL, CA certificate, the Runner's own rotating
 * ServiceAccount token) and is native-image supported. API-server overrides
 * (mainly for tests) use the standard {@code quarkus.kubernetes-client.*}
 * properties ({@code api-server-url}, {@code trust-certs}, ...), shared with the
 * {@code durable-kubernetes} module's client.
 * <p>
 * <strong>Timeout:</strong> the extension's
 * {@code quarkus.kubernetes-client.request-timeout} is a global client setting
 * with no per-call hard default this class can rely on, so the blocking review
 * call is additionally bounded here by a {@value #REVIEW_TIMEOUT_SECONDS}s
 * {@link CompletableFuture} wait; a timeout surfaces as an {@link IOException}
 * and follows the existing fail-closed 401 path. The mechanism already runs
 * the review off the event loop on a worker thread.
 * <p>
 * Fail-closed semantics: transport failures propagate as
 * {@link io.fabric8.kubernetes.client.KubernetesClientException} (or
 * {@link IOException} on timeout/interrupt); the calling authentication
 * mechanism maps any failure to a 401.
 *
 * @see <a href="https://github.com/quarkiverse/quarkus-flow/issues/1160">Issue #1160</a>
 */
@ApplicationScoped
@Unremovable
public class TokenReviewClient {

    private static final Logger LOG = LoggerFactory.getLogger(TokenReviewClient.class);

    /**
     * Hard timeout in seconds for a single TokenReview request. Bounded so that
     * a hung kube-apiserver cannot stall the runner indefinitely.
     */
    static final long REVIEW_TIMEOUT_SECONDS = 10;

    private final KubernetesClient client;

    @Inject
    public TokenReviewClient(KubernetesClient client) {
        this.client = client;
    }

    /**
     * Verified result of a Kubernetes TokenReview.
     *
     * @param authenticated whether the API server authenticated the token
     * @param username the authenticated username (for example
     *        {@code system:serviceaccount:infrans:gateway}), or {@code null}
     * @param expiresAt the expiration timestamp reported by the API server,
     *        or {@code null} when omitted
     */
    public record TokenReviewResult(boolean authenticated, String username, Instant expiresAt) {
    }

    /**
     * Asks the Kubernetes API server to review (authenticate) the given token.
     * <p>
     * On failure, timeout, or interruption the exception is propagated; the
     * calling authentication mechanism maps it to a 401 (fail closed).
     *
     * @param token the incoming (untrusted) ServiceAccount token to validate
     * @return the verified review result; never {@code null}
     * @throws IOException on timeout or interruption of the review call
     */
    public TokenReviewResult review(String token) throws IOException {
        TokenReview request = new TokenReviewBuilder()
                .withNewSpec()
                .withToken(token)
                .endSpec()
                .build();

        TokenReview response;
        CompletableFuture<TokenReview> call = CompletableFuture.supplyAsync(
                () -> client.tokenReviews().create(request));
        try {
            response = call.get(REVIEW_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            // cancel(true) does not interrupt the already-running supplier; the
            // hung request lingers only until the HTTP client's own timeouts,
            // while this thread fails closed immediately.
            call.cancel(true);
            throw new IOException("TokenReview request timed out after " + REVIEW_TIMEOUT_SECONDS + "s", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            call.cancel(true);
            throw new IOException("TokenReview request interrupted", e);
        } catch (ExecutionException e) {
            // KubernetesClientException and friends propagate unwrapped.
            throw rethrowUnchecked(e.getCause() != null ? e.getCause() : e);
        }
        return mapResult(response);
    }

    /**
     * Extracts the namespace from a Kubernetes ServiceAccount username.
     *
     * @param username a username such as
     *        {@code system:serviceaccount:infrans:gateway}
     * @return the namespace ({@code infrans} in the example), or {@code null}
     *         when the username does not match the
     *         {@code system:serviceaccount:<ns>:<name>} shape
     */
    public static String parseNamespace(String username) {
        if (username == null) {
            return null;
        }
        String[] parts = username.split(":");
        if (parts.length != 4 || !"system:serviceaccount".equals(
                parts[0] + ":" + parts[1])) {
            return null;
        }
        return parts[2].isEmpty() ? null : parts[2];
    }

    private TokenReviewResult mapResult(TokenReview response) {
        if (response.getStatus() == null) {
            return new TokenReviewResult(false, null, null);
        }
        Boolean authenticated = response.getStatus().getAuthenticated();
        String username = response.getStatus().getUser() != null
                ? response.getStatus().getUser().getUsername()
                : null;

        // fabric8's TokenReviewStatus does not model expirationTimestamp
        // (a known gap); the API server's response captures it as an
        // additional property via @JsonAnySetter.
        Instant expiresAt = null;
        Object expiration = response.getStatus().getAdditionalProperties().get("expirationTimestamp");
        if (expiration != null) {
            try {
                expiresAt = Instant.parse(String.valueOf(expiration));
            } catch (DateTimeParseException e) {
                LOG.warn("Unparseable TokenReview expirationTimestamp: {}", expiration, e);
            }
        }
        return new TokenReviewResult(Boolean.TRUE.equals(authenticated), username, expiresAt);
    }

    private static <T extends Throwable> T rethrowUnchecked(Throwable throwable) throws T {
        @SuppressWarnings("unchecked")
        T asT = (T) throwable;
        throw asT;
    }
}
