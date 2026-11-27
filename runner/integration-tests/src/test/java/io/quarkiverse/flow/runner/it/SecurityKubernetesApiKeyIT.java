package io.quarkiverse.flow.runner.it;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.restassured.RestAssured.given;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.WireMockServer;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * Integration test pinning the API_KEY coexistence acceptance criterion of the
 * Kubernetes ServiceAccount authentication mechanism (issue #1160): with
 * {@code security.type=API_KEY} and the Kubernetes mechanism enabled, a valid
 * SA-shaped token is authenticated via (mocked) TokenReview with reader access,
 * while the configured API key keeps working exactly as before — including its
 * roles — and non-SA tokens fall through to API-key handling.
 */
@QuarkusTest
@TestProfile(SecurityKubernetesApiKeyProfile.class)
@DisplayName("Security: Kubernetes SA auth coexisting with API_KEY")
class SecurityKubernetesApiKeyIT {

    private static final String TOKEN_REVIEWS_PATH = "/apis/authentication.k8s.io/v1/tokenreviews";

    private static final String INVOKER_API_KEY = "test-invoker-secret-key-456";

    // ==================== Helpers ====================

    private static String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Builds a SA-shaped JWT whose signature is intentionally not valid:
     * validation is delegated to the stubbed TokenReview response.
     */
    private static String saToken(String namespace, String serviceAccountName) {
        String header = base64Url("{\"alg\":\"RS256\",\"typ\":\"JWT\"}");
        String payload = base64Url("{\"kubernetes.io\":{\"serviceaccount\":{\"namespace\":\"" + namespace
                + "\",\"service-account.name\":\"" + serviceAccountName + "\"}}}");
        String signature = base64Url("signature");
        return header + "." + payload + "." + signature;
    }

    private static WireMockServer kubeApiServer() {
        return SecurityKubernetesProfile.KubeApiServerResource.server();
    }

    private static void stubTokenReview(String token, boolean authenticated, String username) {
        kubeApiServer().stubFor(post(urlPathEqualTo(TOKEN_REVIEWS_PATH))
                .withRequestBody(containing(token))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":{\"authenticated\":" + authenticated
                                + (username != null
                                        ? ",\"user\":{\"username\":\"" + username + "\"}"
                                        : "")
                                + "}}")));
    }

    // ==================== (a) valid SA token → reader access via TokenReview ====================

    @Test
    @DisplayName("test_sa_token_gets_reader_access_alongside_api_key_mode")
    void test_sa_token_gets_reader_access_alongside_api_key_mode() {
        String token = saToken("infrans", "gateway");
        stubTokenReview(token, true, "system:serviceaccount:infrans:gateway");

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/q/flow/definitions")
                .then()
                .statusCode(200);
    }

    // ==================== (b) API key behavior unchanged, roles apply ====================

    @Test
    @DisplayName("test_api_key_still_works_on_reads")
    void test_api_key_still_works_on_reads() {
        given()
                .header("Authorization", "Bearer " + INVOKER_API_KEY)
                .when()
                .get("/q/flow/definitions")
                .then()
                .statusCode(200);
    }

    @Test
    @DisplayName("test_api_key_roles_still_apply")
    void test_api_key_roles_still_apply() {
        // flow-invoker may execute; the SA reader role may not — this pins that
        // the API key's roles (not the SA reader role) govern the key's requests
        given()
                .header("Authorization", "Bearer " + INVOKER_API_KEY)
                .contentType("application/json")
                .body(Map.of("name", "API Key Coexistence Test"))
                .when()
                .post("/q/flow/exec/test-namespace/simple-greeting/1.0.0/sync")
                .then()
                .statusCode(200);
    }

    // ==================== (c) garbage token → abstain, fall through to API-key handling ====================

    @Test
    @DisplayName("test_garbage_token_falls_through_to_api_key_handling")
    void test_garbage_token_falls_through_to_api_key_handling() {
        // Not a SA-shaped JWT and not a valid API key: the Kubernetes mechanism
        // abstains and the API-key mechanism rejects the credential → 401
        given()
                .header("Authorization", "Bearer not-a-jwt")
                .when()
                .get("/q/flow/definitions")
                .then()
                .statusCode(401);

        // The abstaining Kubernetes mechanism must never have sent it for review
        kubeApiServer().verify(0, postRequestedFor(urlEqualTo(TOKEN_REVIEWS_PATH))
                .withRequestBody(containing("not-a-jwt")));
    }
}
