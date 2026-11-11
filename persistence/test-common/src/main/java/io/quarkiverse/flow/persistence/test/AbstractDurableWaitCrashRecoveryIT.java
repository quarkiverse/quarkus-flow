package io.quarkiverse.flow.persistence.test;

import java.time.Duration;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.persistence.test.durable.RecoveryWaitResource;
import io.quarkus.test.QuarkusDevModeTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;

/**
 * Abstract base test for crash recovery of a durable workflow that is suspended on a timed
 * {@code wait} task (as opposed to an event-driven {@code listen} task) when the restart happens.
 * <p>
 * The test verifies the persisted instance is picked back up and the wait is resumed to
 * completion after a restart, rather than being left stuck in memory forever.
 */
public abstract class AbstractDurableWaitCrashRecoveryIT {

    protected abstract QuarkusDevModeTest getDevModeTest();

    protected abstract void resetCounters();

    @BeforeEach
    void resetState() {
        resetCounters();
    }

    @Test
    public void workflow_should_resume_a_pending_wait_task_after_restart() {
        RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/durable/recovery-wait/start")
                .then()
                .statusCode(202);

        Awaitility.await()
                .atMost(Duration.ofSeconds(20))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> {
                    RecoveryWaitResource.RecoveryWaitResult result = RestAssured.given()
                            .get("/durable/recovery-wait/status")
                            .then()
                            .statusCode(200)
                            .extract()
                            .body()
                            .as(RecoveryWaitResource.RecoveryWaitResult.class);

                    Assertions.assertEquals(1, result.task1(),
                            "task1 should have been executed before the restart");
                    Assertions.assertEquals(1, result.task2(),
                            "task2 should have been executed before the restart");
                    Assertions.assertEquals(0, result.task3(),
                            "the wait task should still be pending before the restart");
                    Assertions.assertEquals(0, result.task4(),
                            "task4 should not execute before the restart");
                });

        getDevModeTest().modifyResourceFile("application.properties", s -> s.replace("INFO", "DEBUG"));

        Awaitility.await()
                .atMost(Duration.ofSeconds(20))
                .pollDelay(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> RestAssured.given()
                        .get("/durable/recovery-wait/healthz")
                        .then()
                        .statusCode(200));

        Awaitility.await()
                .atMost(Duration.ofSeconds(40))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    RecoveryWaitResource.RecoveryWaitResult result = RestAssured.given()
                            .get("/durable/recovery-wait/status")
                            .then()
                            .statusCode(200)
                            .extract()
                            .body()
                            .as(RecoveryWaitResource.RecoveryWaitResult.class);

                    Assertions.assertEquals(0, result.task1(),
                            "task1 should not run again after the restart");
                    Assertions.assertEquals(0, result.task2(),
                            "task2 should not run again after the restart");
                    Assertions.assertEquals(1, result.task3(),
                            "the wait task should complete exactly once after the restart");
                    Assertions.assertEquals(1, result.task4(),
                            "task4 should complete after the wait is resumed");
                });
    }
}
