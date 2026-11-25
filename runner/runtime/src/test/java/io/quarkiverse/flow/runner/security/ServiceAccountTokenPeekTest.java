package io.quarkiverse.flow.runner.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ServiceAccount token peek tests")
class ServiceAccountTokenPeekTest {

    private static String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String jwt(String headerJson, String payloadJson) {
        return base64Url(headerJson) + "." + base64Url(payloadJson) + "." + base64Url("signature");
    }

    @Test
    @DisplayName("test_peek_returns_claims_for_valid_sa_shaped_jwt")
    void testPeekReturnsClaimsForValidSaShapedJwt() {
        String token = jwt("{\"alg\":\"RS256\",\"typ\":\"JWT\"}",
                "{\"kubernetes.io\":{\"serviceaccount\":{\"namespace\":\"infrans\",\"service-account.name\":\"gateway\"}}}");

        var claims = ServiceAccountTokenPeek.peek(token);

        assertThat(claims).isPresent();
        assertThat(claims.get().namespace()).isEqualTo("infrans");
        assertThat(claims.get().serviceAccountName()).isEqualTo("gateway");
    }

    @Test
    @DisplayName("test_peek_returns_empty_for_bearer_garbage")
    void testPeekReturnsEmptyForBearerGarbage() {
        assertThat(ServiceAccountTokenPeek.peek("Bearer abc")).isEmpty();
        assertThat(ServiceAccountTokenPeek.peek("abc")).isEmpty();
        assertThat(ServiceAccountTokenPeek.peek(null)).isEmpty();
        assertThat(ServiceAccountTokenPeek.peek("")).isEmpty();
    }

    @Test
    @DisplayName("test_peek_returns_empty_for_jwt_without_sa_claims")
    void testPeekReturnsEmptyForJwtWithoutSaClaims() {
        String token = jwt("{\"alg\":\"RS256\",\"typ\":\"JWT\"}",
                "{\"sub\":\"user\",\"iss\":\"https://example.com\"}");

        assertThat(ServiceAccountTokenPeek.peek(token)).isEmpty();
    }

    @Test
    @DisplayName("test_peek_returns_empty_for_two_segment_jwt")
    void testPeekReturnsEmptyForTwoSegmentJwt() {
        String token = base64Url("{\"alg\":\"RS256\",\"typ\":\"JWT\"}") + "."
                + base64Url(
                        "{\"kubernetes.io\":{\"serviceaccount\":{\"namespace\":\"infrans\",\"service-account.name\":\"gateway\"}}}");

        assertThat(ServiceAccountTokenPeek.peek(token)).isEmpty();
    }

    @Test
    @DisplayName("test_peek_returns_empty_for_malformed_base64_payload")
    void testPeekReturnsEmptyForMalformedBase64Payload() {
        // '!' is not a valid base64url character
        assertThat(ServiceAccountTokenPeek.peek("eyJhbGciOiJSUzI1NiJ9.!!invalid!!.c2lnbmF0dXJl")).isEmpty();
    }

    @Test
    @DisplayName("test_peek_returns_empty_for_non_json_payload")
    void testPeekReturnsEmptyForNonJsonPayload() {
        String token = base64Url("{\"alg\":\"RS256\"}") + "." + base64Url("not json at all") + "." + base64Url("sig");

        assertThat(ServiceAccountTokenPeek.peek(token)).isEmpty();
    }

    @Test
    @DisplayName("test_peek_returns_empty_when_sa_subtree_incomplete")
    void testPeekReturnsEmptyWhenSaSubtreeIncomplete() {
        // namespace present but service-account.name missing
        String missingName = jwt("{\"alg\":\"RS256\"}",
                "{\"kubernetes.io\":{\"serviceaccount\":{\"namespace\":\"infrans\"}}}");
        assertThat(ServiceAccountTokenPeek.peek(missingName)).isEmpty();

        // serviceaccount node is not an object
        String notAnObject = jwt("{\"alg\":\"RS256\"}",
                "{\"kubernetes.io\":{\"serviceaccount\":\"gateway\"}}");
        assertThat(ServiceAccountTokenPeek.peek(notAnObject)).isEmpty();
    }
}
