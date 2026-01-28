package io.quarkiverse.flow.durable.kubernetes.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class FlowDurableKubernetesResourceTest {

    @Test
    public void testHelloEndpoint() {
        given()
                .when().get("/flow-durable-kubernetes")
                .then()
                .statusCode(200)
                .body(is("Hello flow-durable-kubernetes"));
    }
}
