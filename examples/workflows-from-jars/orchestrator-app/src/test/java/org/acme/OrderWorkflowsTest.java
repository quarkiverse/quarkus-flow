package org.acme;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class OrderWorkflowsTest {

    @Test
    @DisplayName("test_workflow_from_payments_library_executes")
    void test_workflow_from_payments_library_executes() {
        given()
                .queryParam("orderId", "42")
                .queryParam("amount", 100)
                .when()
                .get("/orders/authorize")
                .then()
                .statusCode(200)
                .body("status", is("AUTHORIZED"))
                .body("orderId", is("42"));
    }

    @Test
    @DisplayName("test_workflow_from_shipping_library_executes")
    void test_workflow_from_shipping_library_executes() {
        given()
                .queryParam("weightKg", 10)
                .when()
                .get("/orders/shipping-quote")
                .then()
                .statusCode(200)
                .body("carrier", is("FlowExpress"));
    }

    @Test
    @DisplayName("test_application_discount_overrides_library_discount")
    void test_application_discount_overrides_library_discount() {
        // payments-workflows ships a "standard" policy for payments:discount:1.0.0;
        // the application redefines it as "promotional", and the application wins
        given()
                .queryParam("amount", 100)
                .when()
                .get("/orders/discount")
                .then()
                .statusCode(200)
                .body("policy", is("promotional"));
    }
}
