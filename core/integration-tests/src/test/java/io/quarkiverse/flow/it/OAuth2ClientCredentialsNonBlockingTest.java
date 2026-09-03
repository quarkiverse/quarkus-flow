package io.quarkiverse.flow.it;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.is;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@QuarkusTestResource(OAuth2ClientCredentialsNonBlockingTest.WireMockTestResource.class)
class OAuth2ClientCredentialsNonBlockingTest {

    @Test
    @DisplayName("test_oauth2_workflow_completes_when_started_from_a_non_blocking_resource_method")
    void test_oauth2_workflow_completes_when_started_from_a_non_blocking_resource_method() {
        when()
                .get("/oauth2-it")
                .then()
                .statusCode(200)
                .body("email", is("test@example.com"));
    }

    public static class WireMockTestResource implements QuarkusTestResourceLifecycleManager {

        static WireMockServer server;

        @Override
        public Map<String, String> start() {
            server = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
            server.start();

            server.stubFor(post(urlPathEqualTo("/oauth2/token"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(
                                    """
                                            {
                                                "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWUsImlhdCI6MTUxNjIzOTAyMn0.KMUFsIDTnFmyG3nMiGM6H9FNFUROf3wh7SmqJp-QV30",
                                                "token_type":"Bearer",
                                                "expires_in":3600
                                            }
                                            """)));

            server.stubFor(get(urlPathEqualTo("/protected"))
                    .withHeader("Authorization", containing("Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9."))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"email\":\"test@example.com\"}")));

            return Map.of(
                    "oauth2.it.authority", server.baseUrl(),
                    "oauth2.it.protected-resource-url", server.baseUrl() + "/protected");
        }

        @Override
        public void stop() {
            if (server != null) {
                server.stop();
            }
        }
    }
}
