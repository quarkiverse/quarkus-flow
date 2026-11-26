package io.quarkiverse.flow.runner.security.kubernetes;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.quarkiverse.flow.runner.FlowRunnerConfig;
import io.quarkus.arc.Unremovable;
import io.quarkus.runtime.StartupEvent;

/**
 * HTTP client for the Kubernetes TokenReview API.
 * <p>
 * Validates incoming Kubernetes ServiceAccount tokens by asking the cluster's
 * API server to review them:
 *
 * <pre>
 * POST &lt;apiServerUrl&gt;/apis/authentication.k8s.io/v1/tokenreviews
 * Authorization: Bearer &lt;runner's own ServiceAccount token&gt;
 * {"spec":{"token":"&lt;incoming token&gt;"}}
 * </pre>
 * <p>
 * The API server (not this class) performs the actual signature and claim
 * validation; the returned {@link TokenReviewResult} carries the verified
 * identity.
 * <p>
 * When {@code apiServerUrl} is unset (the default, in-cluster case), the URL
 * is derived from the standard {@code KUBERNETES_SERVICE_HOST} and
 * {@code KUBERNETES_SERVICE_PORT_HTTPS} environment variables and the TLS
 * trust store is built from the projected cluster CA certificate at
 * {@code /var/run/secrets/kubernetes.io/serviceaccount/ca.crt}. When the URL
 * is explicitly overridden (mainly for tests), plain HTTP and no client
 * certificate are assumed.
 * <p>
 * The {@link java.net.http.HttpClient} is created lazily on first use so that
 * bean construction never fails outside a cluster.
 *
 * @see <a href="https://github.com/quarkiverse/quarkus-flow/issues/1160">Issue #1160</a>
 */
@ApplicationScoped
@Unremovable
public class TokenReviewClient {

    private static final Logger LOG = Logger.getLogger(TokenReviewClient.class);

    /**
     * TokenReview endpoint path on the Kubernetes API server.
     */
    static final String TOKEN_REVIEWS_PATH = "/apis/authentication.k8s.io/v1/tokenreviews";

    /**
     * Path of the projected ServiceAccount token inside a pod.
     */
    static final String TOKEN_PATH = "/var/run/secrets/kubernetes.io/serviceaccount/token";

    /**
     * Path of the projected cluster CA certificate inside a pod.
     */
    static final String CA_CERT_PATH = "/var/run/secrets/kubernetes.io/serviceaccount/ca.crt";

    /**
     * Jackson mapper used to build requests and parse responses.
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final FlowRunnerConfig config;

    /**
     * Lazily created HTTP client (never built off-cluster unless used).
     */
    private volatile HttpClient httpClient;

    @Inject
    public TokenReviewClient(FlowRunnerConfig config) {
        this.config = config;
    }

    void init(@Observes StartupEvent event) {
        // Nothing eager: the HTTP client is created lazily on first use so
        // that startup never fails outside a Kubernetes cluster.
        LOG.debug("TokenReviewClient initialized (HTTP client deferred to first use)");
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
     * On connect failure or non-2xx response the exception is propagated; the
     * calling authentication mechanism maps it to a 401 (fail closed).
     *
     * @param token the incoming (untrusted) ServiceAccount token to validate
     * @return the verified review result; never {@code null}
     * @throws IOException on connect failure or non-2xx API server response
     */
    public TokenReviewResult review(String token) throws IOException {
        ObjectNode body = MAPPER.createObjectNode();
        body.putObject("spec").put("token", token);

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(resolveApiServerUrl() + TOKEN_REVIEWS_PATH))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));

        String ownToken = readOwnServiceAccountToken();
        if (ownToken != null) {
            requestBuilder.header("Authorization", "Bearer " + ownToken);
        }

        try {
            HttpResponse<String> response = client().send(requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException(
                        "TokenReview request failed with HTTP status " + response.statusCode());
            }
            return parseResult(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("TokenReview request interrupted", e);
        }
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

    /**
     * Reads the runner's own ServiceAccount token used to authenticate
     * against the API server.
     * <p>
     * Package-private and overridable as a test seam. Returns {@code null}
     * when the token file is not available (off-cluster); in that case no
     * Authorization header is sent.
     *
     * @return the token content, or {@code null}
     */
    String readOwnServiceAccountToken() {
        try {
            Path path = Path.of(TOKEN_PATH);
            if (Files.isReadable(path)) {
                return Files.readString(path).trim();
            }
        } catch (IOException e) {
            LOG.warnf(e, "Could not read the ServiceAccount token at %s", TOKEN_PATH);
        }
        return null;
    }

    private TokenReviewResult parseResult(String responseBody) throws IOException {
        JsonNode status;
        try {
            status = MAPPER.readTree(responseBody).path("status");
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IOException("Could not parse TokenReview response", e);
        }
        boolean authenticated = status.path("authenticated").asBoolean(false);
        JsonNode userNode = status.path("user");
        String username = userNode.hasNonNull("username") ? userNode.get("username").asText() : null;

        Instant expiresAt = null;
        JsonNode expiration = status.get("expirationTimestamp");
        if (expiration != null && !expiration.isNull()) {
            try {
                expiresAt = Instant.parse(expiration.asText());
            } catch (DateTimeParseException e) {
                LOG.warnf(e, "Unparseable TokenReview expirationTimestamp: %s", expiration.asText());
            }
        }
        return new TokenReviewResult(authenticated, username, expiresAt);
    }

    /**
     * Resolves the API server base URL: the configured override when present,
     * otherwise the in-cluster default derived from the standard environment
     * variables.
     */
    private String resolveApiServerUrl() {
        Optional<String> configured = config.security().service().kubernetes().apiServerUrl();
        if (configured.isPresent()) {
            // Explicit override (tests): plain HTTP, no client certificate.
            return trimTrailingSlash(configured.get());
        }
        String host = System.getenv("KUBERNETES_SERVICE_HOST");
        String port = System.getenv("KUBERNETES_SERVICE_PORT_HTTPS");
        if (host == null || port == null) {
            throw new IllegalStateException(
                    "KUBERNETES_SERVICE_HOST/KUBERNETES_SERVICE_PORT_HTTPS not set and no "
                            + "API server URL configured; the TokenReview client requires an "
                            + "explicit apiServerUrl outside a cluster");
        }
        return "https://" + host + ":" + port;
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * Returns the lazily created HTTP client. When the API server URL is the
     * in-cluster default, the client trusts the cluster CA certificate.
     */
    private HttpClient client() {
        HttpClient local = httpClient;
        if (local == null) {
            synchronized (this) {
                local = httpClient;
                if (local == null) {
                    local = buildClient();
                    httpClient = local;
                }
            }
        }
        return local;
    }

    private HttpClient buildClient() {
        HttpClient.Builder builder = HttpClient.newBuilder();
        if (config.security().service().kubernetes().apiServerUrl().isEmpty()) {
            // In-cluster default: trust the projected cluster CA certificate.
            try {
                KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
                keyStore.load(null, null);
                CertificateFactory factory = CertificateFactory.getInstance("X.509");
                int i = 0;
                try (var caStream = Files.newInputStream(Path.of(CA_CERT_PATH))) {
                    for (Certificate certificate : factory.generateCertificates(caStream)) {
                        keyStore.setCertificateEntry("ca-" + i++, certificate);
                    }
                }
                javax.net.ssl.TrustManagerFactory trustManagerFactory = javax.net.ssl.TrustManagerFactory.getInstance(
                        javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
                trustManagerFactory.init(keyStore);
                javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("TLS");
                sslContext.init(null, trustManagerFactory.getTrustManagers(), new SecureRandom());
                builder.sslContext(sslContext);
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Could not build the SSL context from " + CA_CERT_PATH, e);
            }
        }
        return builder.build();
    }
}
