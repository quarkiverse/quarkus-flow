package io.quarkiverse.flow.it;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
@QuarkusTestResource(HelloResourceTest.WireMockTestResource.class)
public class HelloResourceTest {

    @Test
    @DisplayName("Should inject WorkflowDefinition and Flow with normal identifier (flow:echo-name)")
    public void should_inject_flow_with_normal_identifier() {
        shouldExecuteGetRequestForEchoName("/hello/workflow-def/echo-name");
        shouldExecuteGetRequestForEchoName("/hello/flow/echo-name");
    }

    @Test
    @DisplayName("Should execute echo-name.yaml (src/test/flow) correctly")
    public void test_hello_endpoint() {
        given()
                .when().get("/hello")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("message", is("hello world!"));
    }

    @Test
    @DisplayName("Should execute ProblematicWorkflow correctly (Problem Details + OpenAPI)")
    public void test_problem_details() {
        given()
                .when().get("/hello/problem-details")
                .then()
                .statusCode(503)
                .contentType(ContentType.JSON)
                .body("type", containsString("https://serverlessworkflow.io/spec/1.0.0/errors/communication"));
    }

    private static void shouldExecuteGetRequestForEchoName(String path) {
        given()
                .queryParam("name", "Matheus Cruz")
                .get(path)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("message", containsString("Matheus Cruz"));
    }

    public static class WireMockTestResource implements QuarkusTestResourceLifecycleManager {

        static WireMockServer mock = new WireMockServer(WireMockConfiguration.wireMockConfig().port(9876));

        @Override
        public Map<String, String> start() {
            mock.start();
            mock.addStubMapping(get("/problematic")
                    .willReturn(aResponse()
                            .withStatus(503)
                            .withHeader("Content-Type", "application/json"))
                    .build());
            return Map.of();
        }

        @Override
        public void stop() {
            mock.stop();
        }
    }
}
