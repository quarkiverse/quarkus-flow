package io.quarkiverse.flow.persistence.redis.test;

import static org.assertj.core.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
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
import io.quarkus.redis.datasource.keys.KeyCommands;
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
    private HashCommands<String, String, byte[]> indexCommands;
    private KeyCommands<String> keyCommands;
    private String testNamespace;
    private String testName;
    private String testVersion;
    private String testInstanceId;
    private String testApplicationId;

    @BeforeEach
    void setup() {
        hashCommands = ds.hash(String.class, String.class, byte[].class);
        indexCommands = ds.hash(String.class, String.class, byte[].class);
        keyCommands = ds.key(String.class);
        testNamespace = "test-ns";
        testName = "test-workflow";
        testVersion = "1.0.0";
        testInstanceId = "instance-123";
        testApplicationId = "app-001";
        keyCommands.del(instanceKey(testApplicationId, testNamespace, testName, testVersion, testInstanceId));
        keyCommands.del(indexKey(testInstanceId, testNamespace, testName, testVersion));
        keyCommands.del(instanceKey(testApplicationId, testNamespace, "workflow1", "1.0.0", testInstanceId));
        keyCommands.del(indexKey(testInstanceId, testNamespace, "workflow1", "1.0.0"));
        keyCommands.del(instanceKey(testApplicationId, "other-ns", "workflow2", "2.0.0", testInstanceId));
        keyCommands.del(indexKey(testInstanceId, "other-ns", "workflow2", "2.0.0"));
    }

    @Test
    @DisplayName("locate should find an instance hash when it exists under some applicationId")
    void locate_should_find_instance_when_exists() {
        // Given: an instance hash exists in Redis
        Instant startedAt = Instant.now();
        WorkflowStatus status = WorkflowStatus.RUNNING;

        // Store test instance data
        storeInstance(testApplicationId, testNamespace, testName, testVersion, testInstanceId, status, startedAt);

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
        Instant startedAt = Instant.now();
        WorkflowStatus status = WorkflowStatus.RUNNING;

        // Store both instances
        storeInstance(testApplicationId, testNamespace, "workflow1", "1.0.0", testInstanceId, status, startedAt);

        storeInstance(testApplicationId, "other-ns", "workflow2", "2.0.0", testInstanceId, status, startedAt);

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
    @DisplayName("locate should not scan for instances without a discovery index")
    void locate_should_not_find_legacy_instance_without_index() {
        String instanceKey = testApplicationId + ":" + testNamespace + ":" + testName + ":" + testVersion + ":"
                + testInstanceId;
        hashCommands.hset(instanceKey, "status", MarshallingUtils.writeEnum(factory, WorkflowStatus.RUNNING));
        hashCommands.hset(instanceKey, "date", MarshallingUtils.writeInstant(factory, Instant.now()));

        assertThat(instanceLocator.locate(testNamespace, testName, testVersion, testInstanceId)).isEmpty();
    }

    private void storeInstance(String applicationId, String namespace, String name, String version, String instanceId,
            WorkflowStatus status, Instant startedAt) {
        String instanceKey = instanceKey(applicationId, namespace, name, version, instanceId);
        hashCommands.hset(instanceKey, "status", MarshallingUtils.writeEnum(factory, status));
        hashCommands.hset(instanceKey, "date", MarshallingUtils.writeInstant(factory, startedAt));
        indexCommands.hset(indexKey(instanceId, namespace, name, version), "applicationId",
                applicationId.getBytes(StandardCharsets.UTF_8));
    }

    private String instanceKey(String applicationId, String namespace, String name, String version, String instanceId) {
        return applicationId + ":" + namespace + ":" + name + ":" + version + ":" + instanceId;
    }

    private String indexKey(String instanceId, String namespace, String name, String version) {
        return encode(instanceId) + ":" + encode(namespace) + ":" + encode(name) + ":" + encode(version);
    }

    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
