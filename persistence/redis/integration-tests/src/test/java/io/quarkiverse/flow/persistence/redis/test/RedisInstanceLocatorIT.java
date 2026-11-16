package io.quarkiverse.flow.persistence.redis.test;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.Optional;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import io.quarkiverse.flow.persistence.spi.InstanceLocation;
import io.quarkiverse.flow.persistence.spi.InstanceLocator;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.hash.HashCommands;
import io.quarkus.test.junit.QuarkusTest;
import io.serverlessworkflow.impl.WorkflowStatus;
import io.serverlessworkflow.impl.marshaller.MarshallingUtils;
import io.serverlessworkflow.impl.marshaller.WorkflowBufferFactory;

@QuarkusTest
@DisabledOnOs(OS.WINDOWS)
@DisplayName("RedisInstanceLocator")
public class RedisInstanceLocatorIT {

    @Inject
    InstanceLocator instanceLocator;

    @Inject
    RedisDataSource ds;

    @Inject
    WorkflowBufferFactory factory;

    private HashCommands<String, String, byte[]> hashCommands;
    private String testNamespace;
    private String testName;
    private String testVersion;
    private String testInstanceId;
    private String testApplicationId;

    @BeforeEach
    void setup() {
        hashCommands = ds.hash(String.class, String.class, byte[].class);
        testNamespace = "test-ns";
        testName = "test-workflow";
        testVersion = "1.0.0";
        testInstanceId = "instance-123";
        testApplicationId = "app-001";
    }

    @Test
    @DisplayName("locate should find an instance hash when it exists under some applicationId")
    void locate_should_find_instance_when_exists() {
        // Given: an instance hash exists in Redis
        String instanceKey = testApplicationId + ":" + testNamespace + ":" + testName + ":" + testVersion + ":"
                + testInstanceId;
        Instant startedAt = Instant.now();
        WorkflowStatus status = WorkflowStatus.RUNNING;

        // Store test instance data
        hashCommands.hset(instanceKey, "status", MarshallingUtils.writeEnum(factory, status));
        hashCommands.hset(instanceKey, "date", MarshallingUtils.writeInstant(factory, startedAt));

        // When: we locate the instance
        Optional<InstanceLocation> result = instanceLocator.locate(testNamespace, testName, testVersion,
                testInstanceId);

        // Then: we get the instance location
        assertThat(result).isPresent();
        InstanceLocation location = result.get();
        assertThat(location.namespace()).isEqualTo(testNamespace);
        assertThat(location.name()).isEqualTo(testName);
        assertThat(location.version()).isEqualTo(testVersion);
        assertThat(location.instanceId()).isEqualTo(testInstanceId);
        assertThat(location.applicationId()).isEqualTo(testApplicationId);
        assertThat(location.status()).isEqualTo(status);
        // Compare with tolerance to account for nanosecond vs microsecond precision differences
        assertThat(location.startedAt().toEpochMilli()).isEqualTo(startedAt.toEpochMilli());
    }

    @Test
    @DisplayName("locate should return empty when instance is not found")
    void locate_should_return_empty_when_not_found() {
        // Given: no instance exists

        // When: we locate a non-existent instance
        Optional<InstanceLocation> result = instanceLocator.locate(testNamespace, testName, testVersion,
                "non-existent-id");

        // Then: we get empty
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("locate should not return instance with same instanceId but different workflow id")
    void locate_should_enforce_workflow_isolation() {
        // Given: two instances with same instanceId but different workflow ids
        String instance_key_1 = testApplicationId + ":" + testNamespace + ":workflow1:1.0.0:" + testInstanceId;
        String instance_key_2 = testApplicationId + ":other-ns:workflow2:2.0.0:" + testInstanceId;
        Instant startedAt = Instant.now();
        WorkflowStatus status = WorkflowStatus.RUNNING;

        // Store both instances
        hashCommands.hset(instance_key_1, "status", MarshallingUtils.writeEnum(factory, status));
        hashCommands.hset(instance_key_1, "date", MarshallingUtils.writeInstant(factory, startedAt));

        hashCommands.hset(instance_key_2, "status", MarshallingUtils.writeEnum(factory, status));
        hashCommands.hset(instance_key_2, "date", MarshallingUtils.writeInstant(factory, startedAt));

        // When: we locate with the first workflow id
        Optional<InstanceLocation> result = instanceLocator.locate(testNamespace, "workflow1", "1.0.0",
                testInstanceId);

        // Then: we get only the first instance, not the second
        assertThat(result).isPresent();
        assertThat(result.get().name()).isEqualTo("workflow1");

        // And: locating with the second workflow id returns a different instance
        Optional<InstanceLocation> result2 = instanceLocator.locate("other-ns", "workflow2", "2.0.0",
                testInstanceId);
        assertThat(result2).isPresent();
        assertThat(result2.get().namespace()).isEqualTo("other-ns");
    }

    @Test
    @DisplayName("locate should not mistake a blob-prefixed key with matching suffix for the instance")
    void locate_should_skip_blob_prefixed_keys() {
        // Given: a blob key with a matching instanceId suffix
        String instanceKey = testApplicationId + ":" + testNamespace + ":" + testName + ":" + testVersion + ":"
                + testInstanceId;
        String blobKey = "blob:" + testApplicationId + ":" + testNamespace + ":" + testName + ":" + testVersion + ":"
                + testInstanceId;
        Instant startedAt = Instant.now();
        WorkflowStatus status = WorkflowStatus.RUNNING;

        // Store the blob key (which should be skipped)
        hashCommands.hset(blobKey, "dummy", "data".getBytes());

        // Store the actual instance key
        hashCommands.hset(instanceKey, "status", MarshallingUtils.writeEnum(factory, status));
        hashCommands.hset(instanceKey, "date", MarshallingUtils.writeInstant(factory, startedAt));

        // When: we locate the instance
        Optional<InstanceLocation> result = instanceLocator.locate(testNamespace, testName, testVersion,
                testInstanceId);

        // Then: we get the instance, not the blob
        assertThat(result).isPresent();
        InstanceLocation location = result.get();
        assertThat(location.applicationId()).isEqualTo(testApplicationId);
        assertThat(location.status()).isEqualTo(status);
        // Compare with tolerance to account for nanosecond vs microsecond precision differences
        assertThat(location.startedAt().toEpochMilli()).isEqualTo(startedAt.toEpochMilli());
    }

    @Test
    @DisplayName("locate should not mistake a CE-prefixed key with matching suffix for the instance")
    void locate_should_skip_ce_prefixed_keys() {
        // Given: a cloud event key with a matching pattern suffix
        String instanceKey = testApplicationId + ":" + testNamespace + ":" + testName + ":" + testVersion + ":"
                + testInstanceId;
        String ceKey = "CE:" + testApplicationId + ":" + testNamespace + ":" + testName + ":" + testVersion + ":"
                + testInstanceId;
        Instant startedAt = Instant.now();
        WorkflowStatus status = WorkflowStatus.RUNNING;

        // Store the CE key (which should be skipped)
        hashCommands.hset(ceKey, "source", "event-source".getBytes());

        // Store the actual instance key
        hashCommands.hset(instanceKey, "status", MarshallingUtils.writeEnum(factory, status));
        hashCommands.hset(instanceKey, "date", MarshallingUtils.writeInstant(factory, startedAt));

        // When: we locate the instance
        Optional<InstanceLocation> result = instanceLocator.locate(testNamespace, testName, testVersion,
                testInstanceId);

        // Then: we get the instance, not the CE key
        assertThat(result).isPresent();
        InstanceLocation location = result.get();
        assertThat(location.applicationId()).isEqualTo(testApplicationId);
        assertThat(location.status()).isEqualTo(status);
        // Compare with tolerance to account for nanosecond vs microsecond precision differences
        assertThat(location.startedAt().toEpochMilli()).isEqualTo(startedAt.toEpochMilli());
    }
}
