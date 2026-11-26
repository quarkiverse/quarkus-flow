package io.quarkiverse.flow.runner.security;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import io.quarkiverse.flow.runner.FlowRunnerConfig;

@DisplayName("TokenReview client tests")
class TokenReviewClientTest {

    private static final String RUNNER_SA_TOKEN = "test-runner-sa-token";

    private WireMockServer wireMockServer;
    private TokenReviewClient client;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(
                WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());

        FlowRunnerConfig config = mock(FlowRunnerConfig.class);
        FlowRunnerConfig.Security security = mock(FlowRunnerConfig.Security.class);
        FlowRunnerConfig.Security.Embed embed = mock(FlowRunnerConfig.Security.Embed.class);
        FlowRunnerConfig.Security.Embed.Kubernetes kubernetes = mock(FlowRunnerConfig.Security.Embed.Kubernetes.class);

        when(config.security()).thenReturn(security);
        when(security.embed()).thenReturn(embed);
        when(embed.kubernetes()).thenReturn(kubernetes);
        when(kubernetes.apiServerUrl())
                .thenReturn(Optional.of(wireMockServer.baseUrl()));

        client = spy(new TokenReviewClient(config));
        // Test seam: pretend the runner's own ServiceAccount token is available
        doReturn(RUNNER_SA_TOKEN).when(client).readOwnServiceAccountToken();
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    @DisplayName("test_review_authenticated_returns_fields_and_sends_bearer")
    void testReviewAuthenticatedReturnsFields() throws Exception {
        wireMockServer.stubFor(post(urlPathEqualTo("/apis/authentication.k8s.io/v1/tokenreviews"))
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

        wireMockServer.verify(postRequestedFor(urlEqualTo("/apis/authentication.k8s.io/v1/tokenreviews"))
                .withHeader("Authorization", WireMock.equalTo("Bearer " + RUNNER_SA_TOKEN))
                .withRequestBody(equalToJson("{\"spec\":{\"token\":\"incoming-token\"}}")));
    }

    @Test
    @DisplayName("test_review_not_authenticated")
    void testReviewNotAuthenticated() throws Exception {
        wireMockServer.stubFor(post(urlPathEqualTo("/apis/authentication.k8s.io/v1/tokenreviews"))
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
        wireMockServer.stubFor(post(urlPathEqualTo("/apis/authentication.k8s.io/v1/tokenreviews"))
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> client.review("any-token"))
                .isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("test_review_expiration_omitted_is_null")
    void testReviewExpirationOmittedIsNull() throws Exception {
        wireMockServer.stubFor(post(urlPathEqualTo("/apis/authentication.k8s.io/v1/tokenreviews"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":{\"authenticated\":true,"
                                + "\"user\":{\"username\":\"system:serviceaccount:infrans:gateway\"}}}")));

        TokenReviewClient.TokenReviewResult result = client.review("incoming-token");

        assertThat(result.authenticated()).isTrue();
        assertThat(result.expiresAt()).isNull();
    }

    @Test
    @DisplayName("test_parse_namespace_extracts_third_segment")
    void testParseNamespace() {
        assertThat(TokenReviewClient.parseNamespace("system:serviceaccount:infrans:gateway"))
                .isEqualTo("infrans");
        assertThat(TokenReviewClient.parseNamespace("some-user")).isNull();
        assertThat(TokenReviewClient.parseNamespace(null)).isNull();
    }
}
