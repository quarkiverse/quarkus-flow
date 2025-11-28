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
                            quarkus.flow.definitions.dir=src/main/resources/flow
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
    void should_reload_workflow_def_identifier() {

        String oldIdentifier = "default:wait-duration-inline";
        String path = "/identifier/workflow-def";

        identifierMustMatch(path, oldIdentifier);

        devModeTest.modifyResourceFile("flow/wait-duration-inline.yaml",
                // replace namespace: default
                content -> content.replace("default", "quarkiverse"));

        identifierMustMatch(path, "quarkiverse:wait-duration-inline");

        // Old identifier should no longer be available
        shouldNoLongerBeAvailable(path, oldIdentifier);
    }

    @Test
    void should_reload_flow_identifier() {

        String oldIdentifier = "default.WaitDurationInline";
        String path = "/identifier/flow";

        identifierMustMatch(path, oldIdentifier);

        devModeTest.modifyResourceFile("flow/wait-duration-inline.yaml",
                // replace name wait-duration-inline to wait-please
                content -> content.replace("wait-duration-inline", "wait-please"));

        identifierMustMatch(path, "default.WaitPlease");

        // Old identifier should no longer be available
        shouldNoLongerBeAvailable(path, oldIdentifier);
    }

    private static void identifierMustMatch(String path, String identifier) {
        RestAssured.given()
                .queryParam("identifier", identifier)
                .get(path)
                .then()
                .statusCode(200)
                .body(Matchers.is(identifier));
    }

    private static void shouldNoLongerBeAvailable(String path, String oldIdentifier) {
        RestAssured.given()
                .queryParam("identifier", oldIdentifier)
                .get(path)
                .then()
                .statusCode(404);
    }

}
