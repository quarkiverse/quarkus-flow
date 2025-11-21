package org.acme.http;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class CustomerProfileResourceTest {

    @Test
    void profileEndpointShouldReturnProfileFromWorkflow() {
        given()
                .when()
                .get("/api/profile")
                .then()
                .statusCode(200)
                .body("$", notNullValue())
                .body("customerId", notNullValue())
                .body("name", notNullValue())
                .body("tier", notNullValue())
                .body("lastUpdatedAt", notNullValue())
                .body("servedBy", notNullValue())
                .body("name", equalTo("Jane Doe"))
                .body("tier", equalTo("GOLD"))
                .body("servedBy", equalTo("alice"));
    }
}
