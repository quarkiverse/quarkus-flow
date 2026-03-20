package io.quarkiverse.flow.persistence.redis.deployment;

import java.time.Duration;
import java.util.Map;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.shaded.org.awaitility.Awaitility;
import org.testcontainers.utility.DockerImageName;

import io.quarkiverse.flow.persistence.redis.deployment.durable.DurableResource;
import io.quarkiverse.flow.persistence.redis.deployment.durable.EmitWorkflow;
import io.quarkiverse.flow.persistence.redis.deployment.durable.ListenWorkflow;
import io.quarkus.logging.Log;
import io.quarkus.test.QuarkusDevModeTest;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;

@QuarkusTestResource(DurableListenWorkflowIT.RedisResource.class)
@DisabledOnOs(OS.WINDOWS)
public class DurableListenWorkflowIT {

    public static final String EVENT_NAME = "org.acme.user.decision.Decision";

    @RegisterExtension
    static QuarkusDevModeTest devMode = new QuarkusDevModeTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(ListenWorkflow.class, EmitWorkflow.class, DurableResource.class)
                    .addAsResource("durable-application.properties", "application.properties"));

    /**
     * 1. The ListenWorkflow will execute all the steps until the listen();
     * 2. The application will restart
     * 3. The EmitWorkflow will emit the <code>EVENT_NAME</code> expected by ListenWorkflow
     * 4. The ListenWorkflow must start at the listen point
     * 5. The ListenWorkflow must handle and proceed
     */
    @Test
    void listen_workflow_should_be_restored_after_app_restart() throws InterruptedException {

        // 1. Start the listen workflow
        RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/durable/listen")
                .then()
                .statusCode(202);

        // Wait for async persistence to complete before triggering reload
        // This ensures completed tasks (set, printMessage) are persisted to Redis
        Thread.sleep(2000);

        // 2. Trigger dev mode reload by modifying properties
        devMode.modifyResourceFile("application.properties", s -> s.replace("INFO", "DEBUG"));

        // 3. Wait for reload to complete and workflow restoration to happen
        // The FlowPersistenceRestore bean will scan Redis and restore workflows asynchronously
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

                    Assertions.assertEquals(1, durableResult.printMessage());
                    Assertions.assertEquals(1, durableResult.printDecision());
                });
    }

    public static class RedisResource implements QuarkusTestResourceLifecycleManager {
        private static final Logger LOGGER = LoggerFactory.getLogger(RedisResource.class);

        static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7"))
                .withLogConsumer(outputFrame -> {
                    Log.info(outputFrame.getBytes());
                })
                .withExposedPorts(6379);

        @Override
        public Map<String, String> start() {
            LOGGER.info("Starting Redis container");
            REDIS.start();
            String host = REDIS.getHost();
            Integer port = REDIS.getMappedPort(6379);
            return Map.of(
                    "quarkus.redis.hosts", "redis://" + host + ":" + port);
        }

        @Override
        public void stop() {
            LOGGER.info("Stopping Redis container");
            REDIS.close();
        }
    }
}
