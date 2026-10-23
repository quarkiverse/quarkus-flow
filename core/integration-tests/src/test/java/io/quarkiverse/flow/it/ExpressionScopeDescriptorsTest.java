package io.quarkiverse.flow.it;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.WireMockServer;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

@QuarkusTest
@QuarkusTestResource(ExpressionScopeDescriptorsTest.WireMockTestResource.class)
public class ExpressionScopeDescriptorsTest {

    public static final String USERNAME = "flow-user";
    public static final String PASSWORD = "flow-secret";

    @BeforeEach
    void setup() {
        WireMockTestResource.server.resetAll();
    }

    @Test
    @DisplayName("direct_reference_to_workflow_task_runtime_scopes_resolves_in_jvm_mode")
    void direct_reference_to_workflow_task_runtime_scopes_resolves_in_jvm_mode() {
        RestAssured.given()
                .get("/expression-scope-descriptors/direct")
                .then()
                .statusCode(200)
                .body(containsString("describeContext"));
    }

    @Test
    @DisplayName("for_loop_variable_named_task_shadows_builtin_task_scope_in_jvm_mode")
    void for_loop_variable_named_task_shadows_builtin_task_scope_in_jvm_mode() {
        RestAssured.given()
                .get("/expression-scope-descriptors/for-loop")
                .then()
                .statusCode(200);
    }

    @Test
    @DisplayName("authorization_descriptor_is_populated_after_authenticated_http_call_in_jvm_mode")
    void authorization_descriptor_is_populated_after_authenticated_http_call_in_jvm_mode() {
        WireMockTestResource.server.stubFor(get(urlPathEqualTo("/api/protected"))
                .withBasicAuth(USERNAME, PASSWORD)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));

        String expectedParameter = Base64.getEncoder().encodeToString("flow-user:flow-secret".getBytes());

        RestAssured.given()
                .queryParam("baseUrl", WireMockTestResource.server.baseUrl())
                .get("/expression-scope-descriptors/authorization")
                .then()
                .statusCode(200)
                .body(equalTo("Basic|" + expectedParameter));
    }

    public static class WireMockTestResource implements QuarkusTestResourceLifecycleManager {

        static WireMockServer server;
        static int port;

        static {
            try {
                port = HttpPortUtils.generateRandomPort();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public Map<String, String> start() {
            server = new WireMockServer(port);
            server.start();
            return Map.of(
                    "expr.username", USERNAME,
                    "expr.password", PASSWORD);
        }

        @Override
        public void stop() {
            if (server != null) {
                server.stop();
            }
        }
    }
}
