package io.quarkiverse.flow.runner.it;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.runner.model.AsyncExecutionResponse;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(DefaultTestProfile.class)
@DisplayName("Instance Lifecycle Integration Tests")
class InstanceLifecycleIT {

    private static final String VERSIONED_BASE = "/q/flow/exec/test-namespace/long-running/1.0.0/";
    private static final String LATEST_BASE = "/q/flow/exec/test-namespace/long-running/";
    private static final String INSTANCES = "/q/flow/test-namespace/long-running/1.0.0/instances";

    @Test
    @DisplayName("test_suspend_resume_cancel_with_versioned_routes")
    void test_suspend_resume_cancel_with_versioned_routes() {
        String instanceId = startLongRunning("lifecycle-versioned");

        post(VERSIONED_BASE + instanceId + "/suspend", 200);
        awaitStatus(instanceId, "SUSPENDED");
        assertThat(listInstanceIds("SUSPENDED")).contains(instanceId);

        post(VERSIONED_BASE + instanceId + "/resume", 200);
        awaitStatus(instanceId, "WAITING", "RUNNING");

        post(VERSIONED_BASE + instanceId + "/cancel", 200);
        awaitRemoved(instanceId);
    }

    @Test
    @DisplayName("test_suspend_resume_cancel_with_latest_routes")
    void test_suspend_resume_cancel_with_latest_routes() {
        String instanceId = startLongRunning("lifecycle-latest");

        post(LATEST_BASE + instanceId + "/suspend", 200);
        awaitStatus(instanceId, "SUSPENDED");

        post(LATEST_BASE + instanceId + "/resume", 200);
        awaitStatus(instanceId, "WAITING", "RUNNING");

        post(LATEST_BASE + instanceId + "/cancel", 200);
        awaitRemoved(instanceId);
    }

    @Test
    @DisplayName("test_repeated_suspend_and_resume_return_304")
    void test_repeated_suspend_and_resume_return_304() {
        String instanceId = startLongRunning("lifecycle-not-modified");

        // Resuming an instance that is not suspended is a no-op
        post(VERSIONED_BASE + instanceId + "/resume", 304);

        post(VERSIONED_BASE + instanceId + "/suspend", 200);
        post(VERSIONED_BASE + instanceId + "/suspend", 304);

        post(VERSIONED_BASE + instanceId + "/resume", 200);
        post(VERSIONED_BASE + instanceId + "/resume", 304);

        post(VERSIONED_BASE + instanceId + "/cancel", 200);
    }

    @Test
    @DisplayName("test_suspended_instance_does_not_complete_until_resumed")
    void test_suspended_instance_does_not_complete_until_resumed() {
        String instanceId = startLongRunning("lifecycle-hold");

        post(VERSIONED_BASE + instanceId + "/suspend", 200);

        // The long-running workflow waits 5 seconds; a suspended instance must still be active afterwards
        await()
                .during(Duration.ofSeconds(6))
                .atMost(Duration.ofSeconds(8))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> assertThat(statusOf(instanceId)).contains("SUSPENDED"));

        post(VERSIONED_BASE + instanceId + "/resume", 200);
        awaitRemoved(instanceId);
    }

    @Test
    @DisplayName("test_operations_on_cancelled_instance_return_404")
    void test_operations_on_cancelled_instance_return_404() {
        String instanceId = startLongRunning("lifecycle-cancelled");

        post(VERSIONED_BASE + instanceId + "/cancel", 200);
        awaitRemoved(instanceId);

        post(VERSIONED_BASE + instanceId + "/suspend", 404);
        post(VERSIONED_BASE + instanceId + "/resume", 404);
        post(VERSIONED_BASE + instanceId + "/cancel", 404);
    }

    @Test
    @DisplayName("test_operations_on_unknown_instance_or_version_return_404")
    void test_operations_on_unknown_instance_or_version_return_404() {
        post(VERSIONED_BASE + "unknown-instance/suspend", 404);
        post(VERSIONED_BASE + "unknown-instance/resume", 404);
        post(VERSIONED_BASE + "unknown-instance/cancel", 404);
        post(LATEST_BASE + "unknown-instance/cancel", 404);
        post("/q/flow/exec/test-namespace/long-running/9.9.9/unknown-instance/cancel", 404);
    }

    private static String startLongRunning(String testId) {
        return given()
                .contentType("application/json")
                .body(Map.of("testId", testId))
                .when()
                .post("/q/flow/exec/test-namespace/long-running/1.0.0")
                .then()
                .statusCode(202)
                .extract()
                .as(AsyncExecutionResponse.class)
                .instanceId();
    }

    private static void post(String path, int expectedStatus) {
        given()
                .contentType("application/json")
                .when()
                .post(path)
                .then()
                .statusCode(expectedStatus);
    }

    private static void awaitStatus(String instanceId, String... expected) {
        await()
                .atMost(Duration.ofSeconds(3))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> assertThat(statusOf(instanceId)).hasValueSatisfying(
                        status -> assertThat(status).isIn((Object[]) expected)));
    }

    private static void awaitRemoved(String instanceId) {
        await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> assertThat(statusOf(instanceId)).isEmpty());
    }

    private static Optional<String> statusOf(String instanceId) {
        return instances(null).stream()
                .filter(instance -> instanceId.equals(instance.get("instanceId")))
                .map(instance -> (String) instance.get("status"))
                .findFirst();
    }

    private static List<String> listInstanceIds(String status) {
        return instances(status).stream()
                .map(instance -> (String) instance.get("instanceId"))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> instances(String status) {
        var request = given();
        if (status != null) {
            request = request.queryParam("status", status);
        }
        Map<String, Object> response = request
                .when()
                .get(INSTANCES)
                .then()
                .statusCode(200)
                .extract()
                .as(Map.class);
        return (List<Map<String, Object>>) response.get("instances");
    }
}
