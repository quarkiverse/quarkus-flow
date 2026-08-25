package io.quarkiverse.flow.runner.it;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(DefaultTestProfile.class)
class InstancesResourceIT {

    @Test
    @DisplayName("test_active_instances_returns_application_id_and_instances_list")
    void test_active_instances_returns_application_id_and_instances_list() {
        Map<String, Object> response = given()
                .when()
                .get("/q/flow/instances")
                .then()
                .statusCode(200)
                .extract()
                .as(Map.class);

        assertThat(response).containsKey("applicationId");
        assertThat(response.get("applicationId")).isNotNull().asString().isNotBlank();
        assertThat(response).containsKey("instances");
        assertThat(response.get("instances")).isInstanceOf(java.util.List.class);
    }

    @Test
    @DisplayName("test_running_instance_appears_in_active_instances")
    void test_running_instance_appears_in_active_instances() {
        // Start the long-running workflow asynchronously (it sleeps 5 seconds)
        given()
                .contentType("application/json")
                .body(Map.of("testId", "instances-test-1"))
                .queryParam("wait", "false")
                .when()
                .post("/q/flow/exec/test-namespace/long-running/1.0.0")
                .then()
                .statusCode(202);

        // The instance should appear in /q/flow/instances while still running
        await()
                .atMost(Duration.ofSeconds(3))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    Map<String, Object> response = given()
                            .when()
                            .get("/q/flow/instances")
                            .then()
                            .statusCode(200)
                            .extract()
                            .as(Map.class);

                    java.util.List<?> instances = (java.util.List<?>) response.get("instances");
                    assertThat(instances).isNotEmpty();
                });
    }

    @Test
    @DisplayName("test_active_instance_has_required_fields")
    void test_active_instance_has_required_fields() {
        // Start a long-running workflow
        given()
                .contentType("application/json")
                .body(Map.of("testId", "instances-fields-test"))
                .queryParam("wait", "false")
                .when()
                .post("/q/flow/exec/test-namespace/long-running/1.0.0")
                .then()
                .statusCode(202);

        // Wait until it appears in the registry
        await()
                .atMost(Duration.ofSeconds(3))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    Map<String, Object> response = given()
                            .when()
                            .get("/q/flow/instances")
                            .then()
                            .statusCode(200)
                            .extract()
                            .as(Map.class);

                    java.util.List<Map<String, Object>> instances = (java.util.List<Map<String, Object>>) response
                            .get("instances");
                    assertThat(instances).isNotEmpty();

                    Map<String, Object> instance = instances.get(0);
                    assertThat(instance).containsKey("instanceId");
                    assertThat(instance).containsKey("workflowName");
                    assertThat(instance).containsKey("workflowNamespace");
                    assertThat(instance).containsKey("workflowVersion");
                    assertThat(instance).containsKey("status");
                    assertThat(instance).containsKey("startedAt");

                    assertThat(instance.get("workflowName")).isEqualTo("long-running");
                    assertThat(instance.get("workflowNamespace")).isEqualTo("test-namespace");
                    assertThat(instance.get("workflowVersion")).isEqualTo("1.0.0");
                    assertThat(instance.get("instanceId")).isNotNull();
                    assertThat(instance.get("startedAt")).isNotNull();
                });
    }

    @Test
    @DisplayName("test_filter_by_workflow_name_returns_only_matching_instances")
    void test_filter_by_workflow_name_returns_only_matching_instances() {
        // Start long-running workflow
        given()
                .contentType("application/json")
                .body(Map.of("testId", "filter-name-test"))
                .queryParam("wait", "false")
                .when()
                .post("/q/flow/exec/test-namespace/long-running/1.0.0")
                .then()
                .statusCode(202);

        await()
                .atMost(Duration.ofSeconds(3))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    // Filter by correct name → finds instances
                    Map<String, Object> response = given()
                            .queryParam("workflowName", "long-running")
                            .when()
                            .get("/q/flow/instances")
                            .then()
                            .statusCode(200)
                            .extract()
                            .as(Map.class);

                    java.util.List<?> instances = (java.util.List<?>) response.get("instances");
                    assertThat(instances).isNotEmpty();
                });

        // Filter by non-existent name → empty list
        Map<String, Object> response = given()
                .queryParam("workflowName", "non-existent-workflow")
                .when()
                .get("/q/flow/instances")
                .then()
                .statusCode(200)
                .extract()
                .as(Map.class);

        java.util.List<?> instances = (java.util.List<?>) response.get("instances");
        assertThat(instances).isEmpty();
    }

    @Test
    @DisplayName("test_terminal_status_filter_returns_400")
    void test_terminal_status_filter_returns_400() {
        given()
                .queryParam("status", "COMPLETED")
                .when()
                .get("/q/flow/instances")
                .then()
                .statusCode(400);

        given()
                .queryParam("status", "FAULTED")
                .when()
                .get("/q/flow/instances")
                .then()
                .statusCode(400);

        given()
                .queryParam("status", "CANCELLED")
                .when()
                .get("/q/flow/instances")
                .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("test_unknown_status_filter_returns_400")
    void test_unknown_status_filter_returns_400() {
        given()
                .queryParam("status", "BOGUS")
                .when()
                .get("/q/flow/instances")
                .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("test_completed_instance_is_no_longer_in_active_instances")
    void test_completed_instance_is_no_longer_in_active_instances() {
        // Execute a fast workflow synchronously so it finishes before we query
        given()
                .contentType("application/json")
                .body(Map.of("name", "Completed Test"))
                .queryParam("wait", "true")
                .when()
                .post("/q/flow/exec/test-namespace/simple-greeting/1.0.0")
                .then()
                .statusCode(200);

        // The fast workflow completed — it should NOT be in the active instances list
        Map<String, Object> response = given()
                .queryParam("workflowName", "simple-greeting")
                .when()
                .get("/q/flow/instances")
                .then()
                .statusCode(200)
                .extract()
                .as(Map.class);

        java.util.List<?> instances = (java.util.List<?>) response.get("instances");
        assertThat(instances).isEmpty();
    }
}
