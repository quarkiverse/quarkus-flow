package io.quarkiverse.flow.runner.security.kubernetes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * <strong>UNVERIFIED, ROUTING ONLY, NEVER TRUST.</strong>
 * <p>
 * Performs an <em>unverified</em> peek at the payload claims of a JSON Web
 * Token to decide whether it <em>looks like</em> a Kubernetes ServiceAccount
 * token. This is used solely to route incoming credentials to the correct
 * authentication mechanism (for example, choosing between the service-to-service
 * Kubernetes ServiceAccount mechanism and another mechanism).
 * <p>
 * <ul>
 * <li>No signature verification is performed - none whatsoever.</li>
 * <li>No expiry, issuer, audience, or any other claim validation is
 * performed.</li>
 * <li>The returned claims are attacker-controllable input. Any code that
 * receives a {@link ServiceAccountTokenClaims} from this class MUST treat it
 * as untrusted and MUST NOT use it for authorization decisions.</li>
 * </ul>
 * <p>
 * Actual authentication of the token happens elsewhere.
 *
 * @see <a href="https://github.com/quarkiverse/quarkus-flow/issues/1160">Issue #1160</a>
 */
public final class ServiceAccountTokenPeek {

    /**
     * Jackson mapper used to parse the (unverified) JWT payload.
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ServiceAccountTokenPeek() {
        // Utility class
    }

    /**
     * Unverified claims of a Kubernetes ServiceAccount token.
     * <p>
     * <strong>UNVERIFIED, ROUTING ONLY, NEVER TRUST:</strong> these values are
     * read directly from the token payload without any signature or claim
     * validation. They must never be used for authorization decisions.
     */
    public record ServiceAccountTokenClaims(String namespace, String serviceAccountName) {
    }

    /**
     * Peeks (unverified) at a JWT to determine whether it carries a Kubernetes
     * ServiceAccount claim subtree
     * ({@code kubernetes.io.serviceaccount.namespace} and
     * {@code kubernetes.io.serviceaccount.service-account.name}).
     * <p>
     * <strong>UNVERIFIED, ROUTING ONLY, NEVER TRUST:</strong> no signature
     * verification or claim validation of any kind is performed. The result is
     * only suitable for routing the credential to the correct authentication
     * mechanism.
     *
     * @param jwt the raw JWT string (three dot-separated base64url segments)
     * @return the unverified ServiceAccount claims when the token is a
     *         well-formed JWT carrying the Kubernetes ServiceAccount claim
     *         subtree; empty for non-JWT input, malformed base64, a missing
     *         payload, or a payload without the claim subtree
     */
    public static Optional<ServiceAccountTokenClaims> peek(String jwt) {
        if (jwt == null || jwt.isEmpty()) {
            return Optional.empty();
        }
        String[] segments = jwt.split("\\.");
        if (segments.length != 3) {
            return Optional.empty();
        }
        JsonNode payload;
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(segment(segments[1]));
            payload = MAPPER.readTree(new String(decoded, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException | IOException e) {
            return Optional.empty();
        }
        if (payload == null || !payload.isObject()) {
            return Optional.empty();
        }
        JsonNode serviceAccount = payload.path("kubernetes.io").path("serviceaccount");
        if (!serviceAccount.isObject()) {
            return Optional.empty();
        }
        JsonNode namespace = serviceAccount.path("namespace");
        JsonNode name = serviceAccount.path("service-account.name");
        if (!namespace.isTextual() || !name.isTextual()) {
            return Optional.empty();
        }
        return Optional.of(new ServiceAccountTokenClaims(namespace.asText(), name.asText()));
    }

    private static String segment(String value) {
        // strip the base64url padding characters, tokens are unpadded
        String trimmed = value.trim();
        while (trimmed.endsWith("=")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
