package io.quarkiverse.flow.it;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import java.util.Map;

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
    public void testHelloEndpoint() {
        given()
                .when().get("/hello")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("message", is("hello world!"));
    }

    @Test
    public void testProblemDetails() {
        given()
                .when().get("/hello/problem-details")
                .then()
                .statusCode(503)
                .contentType(ContentType.JSON)
                .body("type", containsString("https://serverlessworkflow.io/spec/1.0.0/errors/communication"));
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
