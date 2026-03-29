package io.quarkiverse.flow.persistence.test;

import java.time.Duration;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.persistence.test.durable.DurableResource;
import io.quarkus.test.QuarkusDevModeTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;

/**
 * Abstract base test class for durable workflow persistence providers.
 * <p>
 * This test verifies that workflows can be persisted and restored across application restarts.
 * The test scenario:
 * <ol>
 * <li>Start a workflow that executes tasks and then waits for an event (listen)</li>
 * <li>Trigger application restart (dev mode reload)</li>
 * <li>Emit the event that the workflow is waiting for</li>
 * <li>Verify the workflow resumes from the listen point and completes successfully</li>
 * </ol>
 */
public abstract class AbstractDurableListenWorkflowIT {

    /**
     * Get the QuarkusDevModeTest instance configured by the subclass.
     * This should be a static field annotated with @RegisterExtension.
     *
     * @return the dev mode test instance
     */
    protected abstract QuarkusDevModeTest getDevModeTest();

    /**
     * Test that a workflow waiting for an event can be restored after application restart.
     * <p>
     * Test steps:
     * <ol>
     * <li>Start the listen workflow - it executes tasks and waits for an event</li>
     * <li>Wait for persistence to complete</li>
     * <li>Trigger dev mode reload to simulate application restart</li>
     * <li>Wait for application to restart and workflows to be restored</li>
     * <li>Emit the event that the workflow is waiting for</li>
     * <li>Verify the workflow resumes and completes successfully</li>
     * </ol>
     */
    @Test
    public void listen_workflow_should_be_restored_after_app_restart() {

        // 1. Start the listen workflow
        RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/durable/listen")
                .then()
                .statusCode(202);

        // Wait until the first task has definitely run and been persisted before reload.
        Awaitility.await()
                .atMost(Duration.ofSeconds(20))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> {
                    DurableResource.DurableResult durableResult = RestAssured.given()
                            .get("/durable/status")
                            .then()
                            .statusCode(200)
                            .extract()
                            .body()
                            .as(DurableResource.DurableResult.class);

                    Assertions.assertEquals(1, durableResult.printMessage(),
                            "printMessage should have been called once before reload");
                    Assertions.assertEquals(0, durableResult.printDecision(),
                            "printDecision should not run before event emission");
                });

        // 2. Trigger dev mode reload by modifying properties
        getDevModeTest().modifyResourceFile("application.properties", s -> s.replace("INFO", "DEBUG"));

        // 3. Wait for reload to complete and workflow restoration to happen
        // The FlowPersistenceRestore bean will scan the persistence store and restore workflows asynchronously
        Awaitility.await()
                .atMost(Duration.ofSeconds(20))
                .pollDelay(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    // Verify the application has reloaded and is responsive
                    RestAssured.given()
                            .get("/durable/healthz")
                            .then()
                            .statusCode(200);
                });

        // 4. Emit the event that the workflow is waiting for
        RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/durable/emit")
                .then()
                .statusCode(200);

        // 5. Wait for the restored workflow to receive the event and complete
        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    DurableResource.DurableResult durableResult = RestAssured.given()
                            .get("/durable/status")
                            .then()
                            .statusCode(200)
                            .extract()
                            .body()
                            .as(DurableResource.DurableResult.class);

                    Assertions.assertEquals(0, durableResult.printMessage(),
                            "printMessage should have been called once before reload");

                    Assertions.assertEquals(1, durableResult.printDecision(),
                            "printDecision should have been called exactly once after event emission");
                });
    }
}
