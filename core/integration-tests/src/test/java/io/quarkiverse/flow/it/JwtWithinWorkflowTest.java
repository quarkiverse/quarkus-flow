package io.quarkiverse.flow.it;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;

import java.io.IOException;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.WireMockServer;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;

@QuarkusTest
@QuarkusTestResource(JwtWithinWorkflowTest.WireMockTestResource.class)
public class JwtWithinWorkflowTest {

    @Test
    public void should_send_jwt_to_downstream_server() {
        String token = RestAssured.given()
                .post("/submissions/token")
                .then()
                .extract().body()
                .asString();

        RestAssured.given()
                .body("""
                        { "title": "Quarkus Flow" }
                        """)
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + token)
                .post("/submissions")
                .then()
                .statusCode(201);
    }

    public static class WireMockTestResource implements QuarkusTestResourceLifecycleManager {

        WireMockServer wireMockServer;

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
            wireMockServer = new WireMockServer(port);
            wireMockServer.start();

            wireMockServer.stubFor(
                    get("/reviewers")
                            .withHeader("Authorization", containing("Bearer"))
                            .willReturn(ok().withHeader("Content-Type", "application/json")
                                    .withBody("""
                                            [{ "name": "John Doe" }]
                                            """)));

            return Map.of("wiremock.url", wireMockServer.baseUrl());
        }

        @Override
        public void stop() {
            wireMockServer.stop();
        }
    }
}
