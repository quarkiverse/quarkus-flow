package io.quarkiverse.flow.deployment.test.devui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.File;
import java.time.Duration;
import java.util.Map;

import org.assertj.core.api.SoftAssertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkus.devui.tests.DevUIJsonRPCTest;
import io.quarkus.test.QuarkusDevModeTest;
import io.restassured.RestAssured;
import io.vertx.core.json.JsonArray;

public class MVStoreWorkflowInstanceStoreDevModeTest extends DevUIJsonRPCTest {

    private static final String DB_PATH = "target/test-mvstore-devmode.mv.db";

    @RegisterExtension
    static final QuarkusDevModeTest devMode = new QuarkusDevModeTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(GreetingResource.class, DevUIWorkflow.class, Message.class)
                    .addAsResource(new StringAsset(
                            "quarkus.flow.devui.storage-type=mvstore\n" +
                                    "quarkus.flow.devui.mvstore.db-path=" + DB_PATH + "\n"),
                            "application.properties"));

    public MVStoreWorkflowInstanceStoreDevModeTest() {
        super("quarkus-flow");
    }

    @Test
    void should_persist_workflow_instance_to_mvstore() throws Exception {
        String instanceId = RestAssured.given()
                .get("/hello/async")
                .then()
                .statusCode(202)
                .extract()
                .path("id");

        assertThat(instanceId).isNotNull();

        await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    JsonNode result = super.executeJsonRPCMethod("findByWorkflowInstanceId", Map.of(
                            "instanceId", instanceId));
                    assertThat(result).isNotNull();
                    assertThat(result.isNull()).as("Result should not be a null node").isFalse();
                    assertThat(result.has("status")).as("Result should have status field").isTrue();
                    assertThat(result.get("status").asText()).isEqualTo("COMPLETED");
                });

        // Verify the MVStore file exists
        File dbFile = new File(DB_PATH);
        assertThat(dbFile)
                .as("MVStore database file should exist after workflow execution")
                .exists();

        assertThat(dbFile.length())
                .as("MVStore database file should have data")
                .isGreaterThan(0);

        JsonNode result = super.executeJsonRPCMethod("findByWorkflowInstanceId", Map.of(
                "instanceId", instanceId));

        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(result.get("instanceId").asText())
                .as("Instance ID should match")
                .isEqualTo(instanceId);
        softly.assertThat(result.get("workflowName").asText())
                .as("Workflow name should be helloQuarkus")
                .isEqualTo("helloQuarkus");
        softly.assertThat(result.get("status").asText())
                .as("Workflow status should be COMPLETED")
                .isEqualTo("COMPLETED");

        softly.assertAll();
    }

    @Test
    void should_persist_multiple_workflow_instances_to_mvstore() throws Exception {
        int numberOfWorkflows = 3;
        String[] instanceIds = new String[numberOfWorkflows];

        for (int i = 0; i < numberOfWorkflows; i++) {
            instanceIds[i] = RestAssured.given()
                    .get("/hello/async")
                    .then()
                    .statusCode(202)
                    .extract()
                    .path("id");
        }

        for (String instanceId : instanceIds) {
            await().atMost(Duration.ofSeconds(10))
                    .pollInterval(Duration.ofMillis(500))
                    .untilAsserted(() -> {
                        JsonNode result = super.executeJsonRPCMethod("findByWorkflowInstanceId", Map.of(
                                "instanceId", instanceId));
                        assertThat(result).isNotNull();
                        assertThat(result.get("status").asText()).isEqualTo("COMPLETED");
                    });
        }

        JsonNode result = super.executeJsonRPCMethod("listAllWorkflowInstances", Map.of(
                "page", 0,
                "size", 10,
                "sort", "START_TIME_ASC"));

        JsonArray instances = new JsonArray(result.toString());

        assertThat(instances.size())
                .as("Should have at least %d workflow instances persisted", numberOfWorkflows)
                .isGreaterThanOrEqualTo(numberOfWorkflows);
    }

    @Test
    void should_find_workflow_instance_by_status() throws Exception {
        String instanceId = RestAssured.given()
                .get("/hello/async")
                .then()
                .statusCode(202)
                .extract()
                .path("id");

        await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    JsonNode result = super.executeJsonRPCMethod("findByWorkflowInstanceId", Map.of(
                            "instanceId", instanceId));
                    assertThat(result).isNotNull();
                    assertThat(result.get("status").asText()).isEqualTo("COMPLETED");
                });

        JsonNode result = super.executeJsonRPCMethod("findByStatus", Map.of(
                "status", "COMPLETED",
                "page", 0,
                "size", 10));

        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(result.size())
                .as("Should have at least one completed workflow instance")
                .isGreaterThanOrEqualTo(1);

        for (int i = 0; i < result.size(); i++) {
            softly.assertThat(result.get(i).get("status").asText())
                    .as("Instance %d should have COMPLETED status", i)
                    .isEqualTo("COMPLETED");
        }

        softly.assertAll();
    }

    @Test
    void should_retrieve_workflow_instance_by_id() throws Exception {
        String instanceId = RestAssured.given()
                .get("/hello/async")
                .then()
                .statusCode(202)
                .extract()
                .path("id");

        await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    JsonNode result = super.executeJsonRPCMethod("findByWorkflowInstanceId", Map.of(
                            "instanceId", instanceId));
                    assertThat(result).isNotNull();
                    assertThat(result.get("status").asText()).isEqualTo("COMPLETED");
                });

        JsonNode result = super.executeJsonRPCMethod("findByWorkflowInstanceId", Map.of(
                "instanceId", instanceId));

        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(result.get("instanceId").asText())
                .as("Retrieved instance ID should match")
                .isEqualTo(instanceId);
        softly.assertThat(result.get("workflowName").asText())
                .as("Workflow name should be helloQuarkus")
                .isEqualTo("helloQuarkus");

        softly.assertAll();
    }

}
