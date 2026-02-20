package io.quarkiverse.flow.persistence.redis.deployment;

import java.util.Map;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
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

@QuarkusTestResource(DurableListenWorkflowTest.RedisResource.class)
public class DurableListenWorkflowTest {

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
    void shouldBeDurableAfterRestart() {

        RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/durable/listen")
                .then()
                .statusCode(202);

        devMode.modifyResourceFile("application.properties", s -> s.replace("INFO", "DEBUG"));

        // trigger live reload
        RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/durable/emit")
                .then()
                .statusCode(200);

        Awaitility.await()
                .untilAsserted(() -> {
                    int size = devMode.getLogRecords()
                            .stream().filter(l -> l.getMessage().contains("Printing decision: "))
                            .toList()
                            .size();
                    Assertions.assertEquals(1, size);
                });
    }

    public static class RedisResource implements QuarkusTestResourceLifecycleManager {

        static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7"))
                .withLogConsumer(outputFrame -> {
                    Log.info(outputFrame.getBytes());
                })
                .withExposedPorts(6379);

        @Override
        public Map<String, String> start() {
            REDIS.start();
            String host = REDIS.getHost();
            Integer port = REDIS.getMappedPort(6379);
            return Map.of(
                    "quarkus.redis.hosts", "redis://" + host + ":" + port);
        }

        @Override
        public void stop() {
            REDIS.close();
        }
    }
}
