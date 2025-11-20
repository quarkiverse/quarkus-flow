package io.quarkiverse.flow.deployment.test;

import org.hamcrest.Matchers;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusDevModeTest;
import io.restassured.RestAssured;

public class FlowWorkflowFromFileDevModeTest {

    @RegisterExtension
    static final QuarkusDevModeTest devModeTest = new QuarkusDevModeTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset("""
                            quarkus.flow.definitions.dir=resources/flow
                            quarkus.flow.tracing.enabled=false
                            """), "application.properties")
                    .addClass(IdentifierResource.class)
                    .addAsResource(new StringAsset("""
                            document:
                              dsl: '1.0.0'
                              namespace: default
                              name: wait-duration-inline
                              version: '0.1.0'
                            do:
                              - wait30Seconds:
                                  wait:
                                    seconds: 30
                            """), "flow/wait-duration-inline.yaml"));

    @Test
    void shouldReloadIdentifier() {

        RestAssured.given()
                .queryParam("identifier", "default:wait-duration-inline")
                .get("/identifier")
                .then()
                .statusCode(200)
                .body(Matchers.is("default:wait-duration-inline"));

        devModeTest.modifyResourceFile("flow/wait-duration-inline.yaml",
                // replace name: default
                content -> content.replace("default", "quarkiverse"));

        RestAssured.given()
                .queryParam("identifier", "quarkiverse:wait-duration-inline")
                .get("/identifier")
                .then()
                .statusCode(200)
                .body(Matchers.is("quarkiverse:wait-duration-inline"));

        // Old identifier should no longer be available
        RestAssured.given()
                .queryParam("identifier", "default:wait-duration-inline")
                .get("/identifier")
                .then()
                .statusCode(404);
    }

}
