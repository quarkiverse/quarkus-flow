package io.quarkiverse.flow.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class JqExpressionResourceTest {

    @Test
    void workflow_with_jq_expression_must_execute_in_event_loop() {
        given()
                .queryParam("name", "james bond")
                .get("/jq-expression")
                .then()
                .statusCode(200)
                .body("message", equalTo("JAMES BOND"));
    }
}
