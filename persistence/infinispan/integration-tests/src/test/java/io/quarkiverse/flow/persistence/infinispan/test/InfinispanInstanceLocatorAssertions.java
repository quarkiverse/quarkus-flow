package io.quarkiverse.flow.persistence.infinispan.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import io.quarkiverse.flow.persistence.spi.InstanceLocator;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.hash.HashCommands;
import io.serverlessworkflow.impl.WorkflowStatus;
import io.serverlessworkflow.impl.marshaller.MarshallingUtils;
import io.serverlessworkflow.impl.marshaller.WorkflowBufferFactory;

final class InfinispanInstanceLocatorAssertions {

    private InfinispanInstanceLocatorAssertions() {
    }

    static void locatesIndexedInstance(InstanceLocator locator, RedisDataSource ds, WorkflowBufferFactory factory) {
        String namespace = "locator-test";
        String name = "infinispan-flow";
        String version = "1.0.0";
        String instanceId = "instance-1";
        String applicationId = "infinispan-runner-0";
        String instanceKey = applicationId + ":" + namespace + ":" + name + ":" + version + ":" + instanceId;
        String indexKey = "instanceId:" + encode(instanceId) + ":" + encode(namespace) + ":" + encode(name) + ":"
                + encode(version);

        HashCommands<String, String, byte[]> hashes = ds.hash(String.class, String.class, byte[].class);
        ds.key(String.class).del(instanceKey, indexKey);

        Instant startedAt = Instant.parse("2026-11-16T12:00:00Z");
        hashes.hset(instanceKey, "status", MarshallingUtils.writeEnum(factory, WorkflowStatus.RUNNING));
        hashes.hset(instanceKey, "date", MarshallingUtils.writeInstant(factory, startedAt));
        hashes.hset(indexKey, "applicationId", applicationId.getBytes(StandardCharsets.UTF_8));

        var result = locator.locate(namespace, name, version, instanceId);
        assertThat(result).isPresent();
        assertThat(result.get().applicationId()).isEqualTo(applicationId);
        assertThat(result.get().status()).isEqualTo(WorkflowStatus.RUNNING);
        assertThat(result.get().startedAt()).isEqualTo(startedAt);

        ds.key(String.class).del(instanceKey, indexKey);
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
