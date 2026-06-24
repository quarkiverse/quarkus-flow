package io.quarkiverse.flow.grpc.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusIntegrationTest;

@QuarkusIntegrationTest
public class GrpcGreetingFlowIT {

    @Test
    void should_use_quarkus_named_grpc_client_channel() {
        given()
                .queryParam("name", "Quarkus")
                .when()
                .get("/grpc-greeting")
                .then()
                .statusCode(200)
                .body(containsString("Hello Quarkus"));
    }
}
