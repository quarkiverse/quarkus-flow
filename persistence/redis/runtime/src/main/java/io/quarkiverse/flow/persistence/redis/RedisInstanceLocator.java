package io.quarkiverse.flow.persistence.redis;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.persistence.spi.InstanceLocation;
import io.quarkiverse.flow.persistence.spi.InstanceLocator;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.hash.HashCommands;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.serverlessworkflow.impl.WorkflowStatus;
import io.serverlessworkflow.impl.marshaller.MarshallingUtils;
import io.serverlessworkflow.impl.marshaller.WorkflowBufferFactory;

@ApplicationScoped
public class RedisInstanceLocator implements InstanceLocator {

    private static final String STATUS = "status";
    private static final String DATE = "date";
    static final String APPLICATION_ID = "applicationId";

    private final WorkflowBufferFactory factory;
    private final HashCommands<String, String, byte[]> hashCommands;
    private final HashCommands<String, String, byte[]> indexCommands;
    private final KeyCommands<String> keyCommands;

    public RedisInstanceLocator(RedisDataSource ds, WorkflowBufferFactory factory) {
        this.factory = factory;
        this.hashCommands = ds.hash(String.class, String.class, byte[].class);
        this.indexCommands = ds.hash(String.class, String.class, byte[].class);
        this.keyCommands = ds.key(String.class);
    }

    @Override
    public Optional<InstanceLocation> locate(String namespace, String name, String version, String instanceId) {
        String indexKey = indexKey(namespace, name, version, instanceId);
        byte[] applicationIdBytes = indexCommands.hget(indexKey, APPLICATION_ID);
        if (applicationIdBytes == null) {
            return Optional.empty();
        }
        String applicationId = new String(applicationIdBytes, StandardCharsets.UTF_8);

        String instanceKey = instanceKey(applicationId, namespace, name, version, instanceId);
        Map<String, byte[]> instanceData = hashCommands.hgetall(instanceKey);
        byte[] statusBytes = instanceData.get(STATUS);
        byte[] dateBytes = instanceData.get(DATE);
        if (statusBytes == null || dateBytes == null) {
            // The instance may have been removed after its index was written.
            keyCommands.del(indexKey);
            return Optional.empty();
        }

        WorkflowStatus status = MarshallingUtils.readEnum(factory, statusBytes, WorkflowStatus.class);
        java.time.Instant startedAt = MarshallingUtils.readInstant(factory, dateBytes);
        return Optional.of(new InstanceLocation(namespace, name, version, instanceId, applicationId, status, startedAt));
    }

    static String indexKey(String namespace, String name, String version, String instanceId) {
        return "instanceId:" + encode(instanceId) + ":" + encode(namespace) + ":" + encode(name) + ":" + encode(version);
    }

    static String instanceKey(String applicationId, String namespace, String name, String version, String instanceId) {
        return applicationId + ":" + namespace + ":" + name + ":" + version + ":" + instanceId;
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
