package io.quarkiverse.flow.deployment.test;

import java.util.List;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.builder.Version;
import io.quarkus.maven.dependency.Dependency;
import io.quarkus.test.QuarkusProdModeTest;
import io.restassured.RestAssured;

public class FlowWorkflowFromFileProdTest {

    private static final String WORKFLOW_YAML = """
            document:
              dsl: '1.0.0'
              namespace: default
              name: call-http
              version: '1.0.0'
            do:
              - wait30Seconds:
                  wait:
                    seconds: 30
            """;

    @RegisterExtension
    static final QuarkusProdModeTest devModeTest = new QuarkusProdModeTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(IdentifierResource.class)
                    .addAsResource(new StringAsset(WORKFLOW_YAML), "flow/call-http.yaml"))
            .setForcedDependencies(List.of(
                    Dependency.of("io.quarkus", "quarkus-rest-jackson", Version.getVersion()),
                    Dependency.of("io.quarkus", "quarkus-vertx-http", Version.getVersion())))
            .setRun(true);

    @Test
    void should_have_a_bean_recorded_when_running_prod_mode() {
        RestAssured.given()
                .queryParam("identifier", "default:call-http:1.0.0")
                .get("/identifier/workflow-def")
                .then()
                .statusCode(200);
    }
}
