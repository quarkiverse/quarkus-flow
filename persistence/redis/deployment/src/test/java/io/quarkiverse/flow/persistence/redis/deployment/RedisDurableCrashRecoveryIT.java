package io.quarkiverse.flow.persistence.redis.deployment;

import java.util.Map;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import io.quarkiverse.flow.persistence.test.AbstractDurableCrashRecoveryIT;
import io.quarkiverse.flow.persistence.test.durable.RecoveryEmitWorkflow;
import io.quarkiverse.flow.persistence.test.durable.RecoveryResource;
import io.quarkiverse.flow.persistence.test.durable.RecoveryWorkflow;
import io.quarkus.logging.Log;
import io.quarkus.test.QuarkusDevModeTest;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

@QuarkusTestResource(RedisDurableCrashRecoveryIT.RedisResource.class)
@DisabledOnOs(OS.WINDOWS)
public class RedisDurableCrashRecoveryIT extends AbstractDurableCrashRecoveryIT {
    private static final String REDIS_IMAGE_NAME = System.getProperty("redis.test.image-name", "valkey/valkey:7.2-alpine");

    private static String redisImageName() {
        return REDIS_IMAGE_NAME;
    }

    @RegisterExtension
    static QuarkusDevModeTest devMode = new QuarkusDevModeTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RecoveryWorkflow.class, RecoveryEmitWorkflow.class, RecoveryResource.class)
                    .addAsResource("durable-recovery-application.properties", "application.properties"));

    @Override
    protected QuarkusDevModeTest getDevModeTest() {
        return devMode;
    }

    @Override
    protected void resetCounters() {
        RecoveryResource.reset();
    }

    public static class RedisResource implements QuarkusTestResourceLifecycleManager {
        private static final Logger LOGGER = LoggerFactory.getLogger(RedisResource.class);

        static final GenericContainer<?> REDIS = new GenericContainer<>(
                DockerImageName.parse(RedisDurableCrashRecoveryIT.redisImageName()))
                .withLogConsumer(outputFrame -> {
                    Log.info(outputFrame.getBytes());
                })
                .withExposedPorts(6379);

        @Override
        public Map<String, String> start() {
            LOGGER.info("Starting Redis container");
            REDIS.start();
            String host = REDIS.getHost();
            Integer port = REDIS.getFirstMappedPort();
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
