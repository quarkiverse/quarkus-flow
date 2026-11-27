package io.quarkiverse.flow.runner.it;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.WireMockServer;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * End-to-end integration tests for the service-to-service Kubernetes ServiceAccount
 * authentication mechanism (issue #1160), with the Kubernetes API server
 * mocked by WireMock.
 * <p>
 * The client tokens are hand-built, SA-shaped JWTs (three base64url segments
 * with the {@code kubernetes.io.serviceaccount} claims); their signatures are
 * not valid because authentication is delegated to the stubbed TokenReview
 * response, exactly as in-cluster where the API server does the validation.
 */
@QuarkusTest
@TestProfile(SecurityKubernetesProfile.class)
@DisplayName("Security: Kubernetes ServiceAccount Authentication")
class SecurityKubernetesIT {

    private static final String TOKEN_REVIEWS_PATH = "/apis/authentication.k8s.io/v1/tokenreviews";

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

    /**
     * Stubs the TokenReview endpoint to answer the given token with the given
     * review outcome. The stub matches the token in the request body so each
     * test case gets its own verdict.
     */
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

    // ==================== Matrix case 1: trusted SA can read ====================

    @Test
    @DisplayName("test_trusted_service_account_can_list_definitions")
    void test_trusted_service_account_can_list_definitions() {
        String token = saToken("infrans", "gateway");
        stubTokenReview(token, true, "system:serviceaccount:infrans:gateway");

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/q/flow/definitions")
                .then()
                .statusCode(200);
    }

    @Test
    @DisplayName("test_trusted_service_account_can_list_instances")
    void test_trusted_service_account_can_list_instances() {
        String token = saToken("infrans", "gateway");
        // Same token as the definitions test: served from the TokenReview cache
        stubTokenReview(token, true, "system:serviceaccount:infrans:gateway");

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/q/flow/instances")
                .then()
                .statusCode(200);
    }

    // ==================== Matrix case 2: reader role cannot execute ====================

    @Test
    @DisplayName("test_service_account_reader_role_cannot_execute_workflow")
    void test_service_account_reader_role_cannot_execute_workflow() {
        // Same trusted token as case 1 (namespaces = *): the 403 can only come
        // from the exec endpoint rejecting the flow-service-reader role
        String token = saToken("infrans", "gateway");
        stubTokenReview(token, true, "system:serviceaccount:infrans:gateway");

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(Map.of("name", "Kubernetes SA Test"))
                .when()
                .post("/q/flow/exec/test-namespace/simple-greeting/1.0.0/sync")
                .then()
                .statusCode(403); // flow-service-reader is not allowed to execute
    }

    // ==================== Matrix case 3: namespace ABAC for untrusted SA ====================

    @Test
    @DisplayName("test_untrusted_service_account_cannot_access_other_namespace")
    void test_untrusted_service_account_cannot_access_other_namespace() {
        String token = saToken("other-ns", "some-svc");
        stubTokenReview(token, true, "system:serviceaccount:other-ns:some-svc");

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/q/flow/definitions?namespace=infrans")
                .then()
                .statusCode(403); // Namespace ABAC: scoped to other-ns only
    }

    @Test
    @DisplayName("test_untrusted_service_account_can_access_own_namespace")
    void test_untrusted_service_account_can_access_own_namespace() {
        String token = saToken("other-ns", "some-svc-list");
        stubTokenReview(token, true, "system:serviceaccount:other-ns:some-svc-list");

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/q/flow/definitions?namespace=other-ns")
                .then()
                .statusCode(200);
    }

    // ==================== Matrix case 4: garbage token → mechanism abstains ====================

    @Test
    @DisplayName("test_garbage_token_makes_mechanism_abstain")
    void test_garbage_token_makes_mechanism_abstain() {
        // Not a SA-shaped JWT: the mechanism abstains and, with security.type=NONE,
        // the anonymous/permit-all path applies → 200. This pins the abstain contract.
        given()
                .header("Authorization", "Bearer not-a-jwt")
                .when()
                .get("/q/flow/definitions")
                .then()
                .statusCode(200);

        // The abstaining mechanism must never have sent this token for review
        kubeApiServer().verify(0, postRequestedFor(urlEqualTo(TOKEN_REVIEWS_PATH))
                .withRequestBody(containing("not-a-jwt")));
    }

    // ==================== Matrix case 5: TokenReview rejects the token ====================

    @Test
    @DisplayName("test_token_review_rejected_returns_401")
    void test_token_review_rejected_returns_401() {
        String token = saToken("infrans", "revoked-svc");
        stubTokenReview(token, false, null);

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/q/flow/definitions")
                .then()
                .statusCode(401);
    }

    // ==================== Matrix case 6: TokenReview cache ====================

    @Test
    @DisplayName("test_repeated_token_reuses_cached_token_review")
    void test_repeated_token_reuses_cached_token_review() {
        // Unique SA/token so counts from other tests cannot interfere
        String token = saToken("infrans", "cache-probe-svc");
        stubTokenReview(token, true, "system:serviceaccount:infrans:cache-probe-svc");

        for (int i = 0; i < 2; i++) {
            given()
                    .header("Authorization", "Bearer " + token)
                    .when()
                    .get("/q/flow/definitions")
                    .then()
                    .statusCode(200);
        }

        // Exactly one TokenReview call for this token: the second request hit the cache
        int reviews = kubeApiServer().countRequestsMatching(
                postRequestedFor(urlEqualTo(TOKEN_REVIEWS_PATH))
                        .withRequestBody(containing(token)).build())
                .getCount();
        assertThat(reviews).isEqualTo(1);
    }
}
