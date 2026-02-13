package io.quarkiverse.flow.it;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

@QuarkusTest
class ProposalResourceTest {

    @Test
    void should_resolve_workflow_identifier_when_the_workflow_bean_is_a_proxy() {
        RestAssured.given()
                .accept("text/plain")
                .post("/proposals")
                .then()
                .body(Matchers.containsString("Proposal created with ID: 1"))
                .extract()
                .body()
                .asString();
    }

}
