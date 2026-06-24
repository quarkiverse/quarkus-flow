package org.acme.grpc;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class GrpcGreetingResourceTest {

    @Test
    void should_route_through_named_quarkus_grpc_client() {
        given()
                .queryParam("name", "Quarkus")
                .when().get("/grpc-greeting")
                .then()
                .statusCode(200)
                .body(containsString("Hello Quarkus"));
    }
}
