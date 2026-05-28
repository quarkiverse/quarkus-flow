package io.quarkiverse.flow.deployment.test;

import static org.assertj.core.api.Fail.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.flow.deployment.DiscoveredWorkflowBuildItem;
import io.quarkus.builder.BuildContext;
import io.quarkus.builder.Version;
import io.quarkus.maven.dependency.Dependency;
import io.quarkus.test.ProdModeTestBuildStep;
import io.quarkus.test.QuarkusProdModeTest;
import io.restassured.RestAssured;
import io.serverlessworkflow.api.WorkflowFormat;
import io.serverlessworkflow.api.WorkflowReader;
import io.serverlessworkflow.api.types.Workflow;

public class FlowWorkflowFromFileProdTest {

    @RegisterExtension
    static final QuarkusProdModeTest devModeTest = new QuarkusProdModeTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(IdentifierResource.class))
            .setForcedDependencies(List.of(
                    Dependency.of("io.quarkus", "quarkus-rest-jackson", Version.getVersion()),
                    Dependency.of("io.quarkus", "quarkus-vertx-http", Version.getVersion())))
            .addBuildChainCustomizerEntries(
                    new QuarkusProdModeTest.BuildChainCustomizerEntry(FlowWorkflowFromFileProdTestBuildStep.class,
                            Collections.singletonList(DiscoveredWorkflowBuildItem.class), Collections.emptyList()))
            .setRun(true);

    @Test
    void should_have_a_bean_recorded_when_running_prod_mode() {
        RestAssured.given()
                .queryParam("identifier", "default:call-http:1.0.0")
                .get("/identifier/workflow-def")
                .then()
                .statusCode(200);
    }

    public static class FlowWorkflowFromFileProdTestBuildStep extends ProdModeTestBuildStep {

        public FlowWorkflowFromFileProdTestBuildStep(Map<String, Object> testContext) {
            super(testContext);
        }

        @Override
        public void execute(BuildContext context) {
            byte[] bytes = """
                    document:
                      dsl: '1.0.0'
                      namespace: default
                      name: call-http
                      version: '1.0.0'
                    do:
                      - wait30Seconds:
                          wait:
                            seconds: 30
                    """.getBytes(StandardCharsets.UTF_8);
            try {
                Workflow workflow = WorkflowReader.readWorkflow(bytes, WorkflowFormat.YAML);
                // Use null for definitionResourcePath to indicate this is a programmatic workflow
                // without an actual resource file - the old recorder will be used
                context.produce(DiscoveredWorkflowBuildItem.fromSpec(null, workflow, bytes));
            } catch (IOException e) {
                fail(e);
            }
        }
    }

}
