package io.quarkiverse.flow.persistence.test;

import java.time.Duration;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.persistence.test.durable.RecoveryResource;
import io.quarkus.test.QuarkusDevModeTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;

/**
 * Abstract base test for crash recovery workflows backed by persistence.
 * <p>
 * The test verifies the durable workflow resumes from the first incomplete task after a restart.
 */
public abstract class AbstractDurableCrashRecoveryIT {

    protected abstract QuarkusDevModeTest getDevModeTest();

    protected abstract void resetCounters();

    @BeforeEach
    void resetState() {
        resetCounters();
    }

    @Test
    public void workflow_should_resume_from_first_incomplete_task_after_restart() {
        RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/durable/recovery/start")
                .then()
                .statusCode(202);

        Awaitility.await()
                .atMost(Duration.ofSeconds(20))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> {
                    RecoveryResource.RecoveryResult recoveryResult = RestAssured.given()
                            .get("/durable/recovery/status")
                            .then()
                            .statusCode(200)
                            .extract()
                            .body()
                            .as(RecoveryResource.RecoveryResult.class);

                    Assertions.assertEquals(1, recoveryResult.task1(),
                            "task1 should have been executed before the restart");
                    Assertions.assertEquals(1, recoveryResult.task2(),
                            "task2 should have been executed before the restart");
                    Assertions.assertEquals(0, recoveryResult.task3(),
                            "task3 should still be waiting before the restart");
                    Assertions.assertEquals(0, recoveryResult.task4(),
                            "task4 should not execute before the restart");
                    Assertions.assertEquals(0, recoveryResult.task5(),
                            "task5 should not execute before the restart");
                });

        getDevModeTest().modifyResourceFile("application.properties", s -> s.replace("INFO", "DEBUG"));

        Awaitility.await()
                .atMost(Duration.ofSeconds(20))
                .pollDelay(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> RestAssured.given()
                        .get("/durable/recovery/healthz")
                        .then()
                        .statusCode(200));

        RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/durable/recovery/emit")
                .then()
                .statusCode(200);

        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    RecoveryResource.RecoveryResult recoveryResult = RestAssured.given()
                            .get("/durable/recovery/status")
                            .then()
                            .statusCode(200)
                            .extract()
                            .body()
                            .as(RecoveryResource.RecoveryResult.class);

                    Assertions.assertEquals(0, recoveryResult.task1(),
                            "task1 should not run again after the restart");
                    Assertions.assertEquals(0, recoveryResult.task2(),
                            "task2 should not run again after the restart");
                    Assertions.assertEquals(1, recoveryResult.task3(),
                            "task3 should complete exactly once after the event is emitted");
                    Assertions.assertEquals(1, recoveryResult.task4(),
                            "task4 should complete after task3");
                    Assertions.assertEquals(1, recoveryResult.task5(),
                            "task5 should complete after task4");
                });
    }
}
