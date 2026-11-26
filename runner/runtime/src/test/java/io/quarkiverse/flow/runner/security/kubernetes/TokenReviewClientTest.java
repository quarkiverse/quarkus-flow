package io.quarkiverse.flow.runner.security.kubernetes;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import io.fabric8.kubernetes.client.KubernetesClientException;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

/**
 * TokenReview client tests: the fabric8 {@code KubernetesClient} (injected by
 * the {@code quarkus-kubernetes-client} extension) is pointed at a WireMock
 * instance standing in for the kube-apiserver via the standard
 * {@code quarkus.kubernetes-client.api-server-url} / {@code trust-certs}
 * properties. The configured {@code quarkus.kubernetes-client.token} plays
 * the Runner's own ServiceAccount token, so the client sends it as the
 * Bearer credential of the review request.
 */
@QuarkusTest
// Windows CI runners have no Docker; see the module's test application.properties
@DisabledOnOs(OS.WINDOWS)
@TestProfile(TokenReviewClientTest.Profile.class)
@DisplayName("TokenReview client tests")
class TokenReviewClientTest {

    private static final String TOKEN_REVIEWS_PATH = "/apis/authentication.k8s.io/v1/tokenreviews";

    @jakarta.inject.Inject
    TokenReviewClient client;

    @AfterEach
    void resetStubs() {
        Profile.KubeApiServer.server().resetAll();
    }

    @Test
    @DisplayName("test_review_authenticated_returns_fields_and_sends_bearer")
    void testReviewAuthenticatedReturnsFields() throws Exception {
        Profile.KubeApiServer.server().stubFor(post(urlPathEqualTo(TOKEN_REVIEWS_PATH))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"status":{"authenticated":true,
                                 "user":{"username":"system:serviceaccount:infrans:gateway"},
                                 "expirationTimestamp":"2026-12-01T00:00:00Z"}}""")));

        TokenReviewClient.TokenReviewResult result = client.review("incoming-token");

        assertThat(result.authenticated()).isTrue();
        assertThat(result.username()).isEqualTo("system:serviceaccount:infrans:gateway");
        assertThat(result.expiresAt()).isEqualTo(java.time.Instant.parse("2026-12-01T00:00:00Z"));

        Profile.KubeApiServer.server().verify(postRequestedFor(urlEqualTo(TOKEN_REVIEWS_PATH))
                .withHeader("Authorization",
                        WireMock.equalTo("Bearer " + Profile.RUNNER_SA_TOKEN))
                .withRequestBody(containing("\"token\":\"incoming-token\"")));
    }

    @Test
    @DisplayName("test_review_not_authenticated")
    void testReviewNotAuthenticated() throws Exception {
        Profile.KubeApiServer.server().stubFor(post(urlPathEqualTo(TOKEN_REVIEWS_PATH))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":{\"authenticated\":false}}")));

        TokenReviewClient.TokenReviewResult result = client.review("bad-token");

        assertThat(result.authenticated()).isFalse();
        assertThat(result.username()).isNull();
        assertThat(result.expiresAt()).isNull();
    }

    @Test
    @DisplayName("test_review_server_error_propagates")
    void testReviewServerErrorPropagates() throws Exception {
        Profile.KubeApiServer.server().stubFor(post(urlPathEqualTo(TOKEN_REVIEWS_PATH))
                .willReturn(aResponse().withStatus(500)));

        // Fail-closed: the transport error propagates (the mechanism maps any
        // failure to a 401). fabric8 auto-retries 5xx responses, so the exact
        // exception type is nondeterministic: either the wrapped
        // KubernetesClientException or the bounded-wait timeout IOException.
        assertThatThrownBy(() -> client.review("any-token"))
                .isInstanceOfAny(KubernetesClientException.class, IOException.class);
    }

    @Test
    @DisplayName("test_review_expiration_omitted_is_null")
    void testReviewExpirationOmittedIsNull() throws Exception {
        Profile.KubeApiServer.server().stubFor(post(urlPathEqualTo(TOKEN_REVIEWS_PATH))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":{\"authenticated\":true,"
                                + "\"user\":{\"username\":\"system:serviceaccount:infrans:gateway\"}}}")));

        TokenReviewClient.TokenReviewResult result = client.review("incoming-token");

        assertThat(result.authenticated()).isTrue();
        assertThat(result.expiresAt()).isNull();
    }

    @Test
    @DisplayName("test_review_unreachable_api_server_propagates")
    void testReviewUnreachableApiServerPropagates() throws Exception {
        // No matching stub answers with a 404, proving the error path through
        // the fabric8 client. (Kept minimal: transport failure propagation is
        // covered by the 500 case above.)
        Profile.KubeApiServer.server().stubFor(post(urlPathEqualTo(TOKEN_REVIEWS_PATH))
                .withRequestBody(containing("unknown-stub-token"))
                .willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> client.review("unknown-stub-token"))
                .isInstanceOf(KubernetesClientException.class);
    }

    @Test
    @DisplayName("test_parse_namespace_extracts_third_segment")
    void testParseNamespace() {
        assertThat(TokenReviewClient.parseNamespace("system:serviceaccount:infrans:gateway"))
                .isEqualTo("infrans");
        assertThat(TokenReviewClient.parseNamespace("some-user")).isNull();
        assertThat(TokenReviewClient.parseNamespace(null)).isNull();
    }

    /**
     * Points the extension's {@code KubernetesClient} at the WireMock
     * kube-apiserver using the standard {@code quarkus.kubernetes-client.*}
     * properties (the same seam available to deployments).
     */
    public static class Profile implements QuarkusTestProfile {

        /**
         * Token configured as the client's credential: stands in for the
         * Runner's own ServiceAccount token in-cluster.
         */
        static final String RUNNER_SA_TOKEN = "test-runner-sa-token";

        @Override
        public Map<String, String> getConfigOverrides() {
            // NOTE: quarkus.kubernetes-client.devservices.enabled is build-time
            // config and cannot be set here; it is disabled for this module in
            // src/test/resources/application.properties.
            return Map.of(
                    "quarkus.flow.runner.security.type", "none",
                    "quarkus.kubernetes-client.token", RUNNER_SA_TOKEN,
                    "quarkus.http.test-port", "0");
        }

        @Override
        public List<TestResourceEntry> testResources() {
            return List.of(new TestResourceEntry(KubeApiServer.class));
        }

        /**
         * Starts a WireMock server standing in for the Kubernetes API server
         * and points {@code quarkus.kubernetes-client.api-server-url} at it.
         */
        public static class KubeApiServer implements QuarkusTestResourceLifecycleManager {

            private static WireMockServer server;

            @Override
            public Map<String, String> start() {
                server = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
                server.start();
                WireMock.configureFor("localhost", server.port());
                return Map.of(
                        "quarkus.kubernetes-client.api-server-url", server.baseUrl(),
                        "quarkus.kubernetes-client.trust-certs", "true");
            }

            @Override
            public void stop() {
                if (server != null) {
                    server.stop();
                    server = null;
                }
            }

            static WireMockServer server() {
                if (server == null) {
                    throw new IllegalStateException("WireMock kube-apiserver not started");
                }
                return server;
            }
        }
    }
}
