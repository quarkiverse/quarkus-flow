package io.quarkiverse.flow.dsl;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.junit.QuarkusTest;
import io.serverlessworkflow.api.types.OAuth2AuthenticationData;
import io.serverlessworkflow.api.types.OAuth2AuthenticationDataClient;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowApplication;

@QuarkusTest
@QuarkusTestResource(FuncOAuth2HttpTest.WireMockTestResource.class)
public class FuncOAuth2HttpTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private record OAuth2Client(String clientId, String clientSecret, String baseUrl) {
    }

    @Inject
    WorkflowApplication app;

    @BeforeEach
    void setup() {
        WireMockTestResource.server.resetAll();

        WireMockTestResource.server.stubFor(post(urlPathEqualTo("/joogle-auth/oauth2/token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(tokenResponse(fakeJwt()))));

        WireMockTestResource.server.stubFor(post(urlPathEqualTo("/jahoo-auth/oauth2/token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(tokenResponse(fakeJwt()))));

        WireMockTestResource.server.stubFor(get(urlPathEqualTo("/joogle"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"email\":\"/joogle@example.com\"}")));

        WireMockTestResource.server.stubFor(get(urlPathEqualTo("/jahoo"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"email\":\"/jahoo@example.com\"}")));
    }

    @Test
    @DisplayName("Two named OAuth2 client-credentials authentications, each used by a forked HTTP call")
    void test_multiple_oauth2_clients() throws Exception {
        String base = WireMockTestResource.server.baseUrl();

        OAuth2Client joogle = new OAuth2Client("joogle-client-id", "joogle-client-secret", base + "/joogle-auth");
        OAuth2Client jahoo = new OAuth2Client("jahoo-client-id", "jahoo-client-secret", base + "/jahoo-auth");

        Workflow workflow = FlowWorkflowBuilder.workflow("multiple-oauth2-clients", "quarkus-flow")
                .use(
                        use -> use.authentications(
                                auth -> {
                                    auth.authentication(
                                            "joogle",
                                            a -> a.oauth2(
                                                    oauth2 -> oauth2
                                                            .client(
                                                                    client -> client
                                                                            .id(joogle.clientId())
                                                                            .secret(joogle.clientSecret())
                                                                            .authentication(
                                                                                    OAuth2AuthenticationDataClient.ClientAuthentication.CLIENT_SECRET_POST))
                                                            .authority(joogle.baseUrl())
                                                            .grant(
                                                                    OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS)
                                                            .build()));
                                    auth.authentication(
                                            "jahoo",
                                            a -> a.oauth2(
                                                    oauth2 -> oauth2
                                                            .client(
                                                                    client -> client
                                                                            .id(jahoo.clientId())
                                                                            .secret(jahoo.clientSecret()))
                                                            .authority(jahoo.baseUrl())
                                                            .grant(
                                                                    OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS)
                                                            .build()));
                                }))
                .tasks(
                        FlowDSL.fork(
                                FlowDSL.http()
                                        .get()
                                        .uri(URI.create(base + "/joogle"), FlowDSL.use("joogle")),
                                FlowDSL.http()
                                        .get()
                                        .uri(URI.create(base + "/jahoo"), FlowDSL.use("jahoo"))),
                        FlowDSL.function("merge", o -> o))
                .build();

        app.workflowDefinition(workflow).instance(Map.of()).start().join();

        List<LoggedRequest> joogleTokenRequests = WireMockTestResource.server
                .findAll(postRequestedFor(urlPathEqualTo("/joogle-auth/oauth2/token")));
        List<LoggedRequest> jahooTokenRequests = WireMockTestResource.server
                .findAll(postRequestedFor(urlPathEqualTo("/jahoo-auth/oauth2/token")));

        assertThat(joogleTokenRequests)
                .as("joogle token request")
                .hasSize(1);
        String joogleTokenBody = joogleTokenRequests.get(0).getBodyAsString();
        assertThat(joogleTokenBody)
                .contains("grant_type=client_credentials")
                .contains("client_id=joogle-client-id")
                .contains("client_secret=joogle-client-secret");

        assertThat(jahooTokenRequests)
                .as("jahoo token request")
                .hasSize(1);
        String jahooTokenBody = jahooTokenRequests.get(0).getBodyAsString();
        assertThat(jahooTokenBody)
                .contains("grant_type=client_credentials")
                .contains("client_id=jahoo-client-id")
                .contains("client_secret=jahoo-client-secret");

        List<LoggedRequest> joogleApiRequests = WireMockTestResource.server
                .findAll(getRequestedFor(urlPathEqualTo("/joogle")));
        List<LoggedRequest> jahooApiRequests = WireMockTestResource.server
                .findAll(getRequestedFor(urlPathEqualTo("/jahoo")));

        assertThat(joogleApiRequests.get(0).getHeader("Authorization")).as("joogle bearer").startsWith("Bearer ");
        assertThat(jahooApiRequests.get(0).getHeader("Authorization")).as("jahoo bearer").startsWith("Bearer ");
    }

    @Test
    @DisplayName("Custom endpoints.token overrides the default /oauth2/token path")
    void test_custom_token_endpoint() throws Exception {
        String base = WireMockTestResource.server.baseUrl();

        OAuth2Client joogle = new OAuth2Client("joogle-client-id", "joogle-client-secret", base + "/joogle-auth");
        String customTokenPath = "/auth/realms/joogle/protocol/openid-connect/token";

        WireMockTestResource.server.stubFor(post(urlEqualTo("/joogle-auth" + customTokenPath))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(tokenResponse(fakeJwt()))));

        Workflow workflow = FlowWorkflowBuilder.workflow("custom-token-endpoint", "quarkus-flow")
                .use(
                        FlowDSL.auth(
                                "joogle",
                                FlowDSL.oauth2(
                                        oauth2 -> oauth2
                                                .endpoints(e -> e.token(customTokenPath))
                                                .client(
                                                        client -> client
                                                                .id(joogle.clientId())
                                                                .secret(joogle.clientSecret())
                                                                .authentication(
                                                                        OAuth2AuthenticationDataClient.ClientAuthentication.CLIENT_SECRET_POST))
                                                .authority(joogle.baseUrl())
                                                .grant(
                                                        OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS))))
                .tasks(
                        FlowDSL.http().get().uri(URI.create(base + "/joogle"), FlowDSL.use("joogle")))
                .build();

        app.workflowDefinition(workflow).instance(Map.of()).start().join();

        List<LoggedRequest> customTokenRequests = WireMockTestResource.server
                .findAll(postRequestedFor(urlEqualTo("/joogle-auth" + customTokenPath)));
        List<LoggedRequest> defaultTokenRequests = WireMockTestResource.server
                .findAll(postRequestedFor(urlPathEqualTo("/joogle-auth/oauth2/token")));

        assertThat(customTokenRequests)
                .as("token request hit the custom endpoint")
                .hasSize(1);
        assertThat(customTokenRequests.get(0).getBodyAsString())
                .contains("client_id=joogle-client-id");
        assertThat(defaultTokenRequests).isEmpty();
    }

    private static String tokenResponse(String jwt) {
        return """
                {
                  "access_token": "%s",
                  "token_type": "Bearer",
                  "expires_in": 3600
                }
                """
                .formatted(jwt);
    }

    private static String fakeJwt() {
        try {
            long now = Instant.now().getEpochSecond();
            String header = MAPPER.writeValueAsString(Map.of("alg", "RS256", "typ", "Bearer", "kid", "test"));
            String payload = MAPPER.writeValueAsString(Map.of("sub", "test-subject", "exp", now + 3600, "iat", now));
            return b64Url(header) + "." + b64Url(payload) + ".sig";
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String b64Url(String s) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    public static class WireMockTestResource implements QuarkusTestResourceLifecycleManager {

        static WireMockServer server;

        @Override
        public Map<String, String> start() {
            server = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
            server.start();
            return Map.of();
        }

        @Override
        public void stop() {
            if (server != null) {
                server.stop();
            }
        }
    }
}
