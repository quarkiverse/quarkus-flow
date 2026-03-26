package io.quarkiverse.flow.deployment.test.devui;

import java.util.Map;

import org.assertj.core.api.Assertions;
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

public class LifecycleManagementDevUIJsonRPCTest extends DevUIJsonRPCTest {

    @RegisterExtension
    static final QuarkusDevModeTest devMode = new QuarkusDevModeTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset("quarkus.http.port=0"), "application.properties")
                    .addClasses(GreetingResource.class, DevUIWorkflow.class));

    public LifecycleManagementDevUIJsonRPCTest() {
        super("quarkus-flow", "http://localhost:8080");
    }

    @Test
    void should_not_get_due_to_pagination_size_as_zero() throws Exception {

        executeDevUIWorkflow(1);

        JsonNode root = super.executeJsonRPCMethod("listAllWorkflowInstances", Map.of(
                "page", 0,
                "size", 0,
                "sort", "START_TIME_ASC"));

        JsonArray array = new JsonArray(root.toString());

        Assertions.assertThat(array)
                .as("should not have items because the page is 0 and the size is 0")
                .isEmpty();
    }

    @Test
    void paginate_options_should_work_as_expected() throws Exception {

        executeDevUIWorkflow(5);

        SoftAssertions softly = new SoftAssertions();

        JsonNode sizeJson = super.executeJsonRPCMethod("listAllWorkflowInstances", Map.of(
                "page", 0,
                "size", 3,
                "sort", "START_TIME_ASC"));

        JsonArray array = new JsonArray(sizeJson.toString());

        softly.assertThat(array.size()).isEqualTo(3)
                .as("should have only three workflow instances because we are in the page 0 and the size is 5");

        JsonNode pageJson = super.executeJsonRPCMethod("listAllWorkflowInstances", Map.of(
                "page", 1,
                "size", 10,
                "sort", "START_TIME_ASC"));

        JsonArray pageArray = new JsonArray(pageJson.toString());

        softly.assertThat(pageArray)
                .as("should not have workflow instances because the page is 1 (there is no item in page 1 with size 10")
                .isEmpty();

        softly.assertAll();
    }

    @Test
    void should_get_by_status() throws Exception {
        executeDevUIWorkflow(1);

        JsonNode root = this.executeJsonRPCMethod("findByStatus", Map.of("status", "COMPLETED"));

        SoftAssertions softly = new SoftAssertions();

        for (JsonNode jsonNode : root) {
            softly.assertThat(jsonNode.get("status").asText()).isEqualTo("COMPLETED");
        }

        JsonNode faulted = this.executeJsonRPCMethod("findByStatus", Map.of("status", "FAULTED", "page", 0, "size", 10));

        softly.assertThat(faulted)
                .as("should not have faulted workflows")
                .isEmpty();

        softly.assertAll();
    }

    @Test
    void should_get_workflow_instance_by_id() throws Exception {

        executeDevUIWorkflow(1);

        JsonNode root = this.executeJsonRPCMethod("findByStatus", Map.of("status", "COMPLETED", "page", 0, "size", 10));
        JsonNode workflowInstance = root.valueStream().findFirst().orElseThrow();

        SoftAssertions softly = new SoftAssertions();

        String instanceId = workflowInstance.get("instanceId").asText();
        softly.assertThat(instanceId).isNotNull();
        softly.assertThat(instanceId).isNotBlank();

        JsonNode jsonNode = this.executeJsonRPCMethod("findByWorkflowInstanceId", Map.of(
                "instanceId", instanceId));

        softly.assertThat(jsonNode.get("instanceId").asText()).isEqualTo(instanceId).as(
                "the instance ID must be the same value");

        softly.assertAll();
    }

    private static void executeDevUIWorkflow(int times) {
        for (int i = 0; i < times; i++) {
            RestAssured.given()
                    .get("/hello")
                    .then()
                    .statusCode(200);
        }
    }
}
