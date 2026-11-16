package io.quarkiverse.flow.persistence.redis;

import java.util.Map;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.persistence.spi.InstanceLocation;
import io.quarkiverse.flow.persistence.spi.InstanceLocator;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.hash.HashCommands;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.keys.KeyScanArgs;
import io.quarkus.redis.datasource.keys.KeyScanCursor;
import io.serverlessworkflow.impl.WorkflowStatus;
import io.serverlessworkflow.impl.marshaller.MarshallingUtils;
import io.serverlessworkflow.impl.marshaller.WorkflowBufferFactory;

@ApplicationScoped
public class RedisInstanceLocator implements InstanceLocator {

    private static final String SEPARATOR = ":";
    private static final String DATE = "date";
    private static final String STATUS = "status";
    private static final String BLOB_PREFIX = "blob:";
    private static final String CE_PREFIX = "CE:";

    private final RedisDataSource ds;
    private final WorkflowBufferFactory factory;
    private final KeyCommands<String> keyCommands;
    private final HashCommands<String, String, byte[]> hashCommands;

    public RedisInstanceLocator(RedisDataSource ds, WorkflowBufferFactory factory) {
        this.ds = ds;
        this.factory = factory;
        this.keyCommands = ds.key(String.class);
        this.hashCommands = ds.hash(String.class, String.class, byte[].class);
    }

    @Override
    public Optional<InstanceLocation> locate(String namespace, String name, String version, String instanceId) {
        String matchPattern = "*:" + namespace + ":" + name + ":" + version + ":" + instanceId;
        KeyScanCursor<String> cursor = keyCommands.scan(new KeyScanArgs().match(matchPattern).count(500));

        while (cursor.hasNext()) {
            for (String key : cursor.next()) {
                // Skip companion keys that are not instance hashes
                if (key.startsWith(BLOB_PREFIX) || key.startsWith(CE_PREFIX)) {
                    continue;
                }

                // Validate key structure: should have exactly 5 colon-separated segments
                String[] segments = key.split("\\Q" + SEPARATOR + "\\E", -1);
                if (segments.length != 5 || !segments[4].equals(instanceId)) {
                    continue;
                }

                String applicationId = segments[0];

                // Read instance data from the hash
                Map<String, byte[]> instanceData = hashCommands.hgetall(key);
                if (instanceData.isEmpty()) {
                    continue;
                }

                byte[] statusBytes = instanceData.get(STATUS);
                byte[] dateBytes = instanceData.get(DATE);

                if (statusBytes == null || dateBytes == null) {
                    continue;
                }

                WorkflowStatus status = MarshallingUtils.readEnum(factory, statusBytes, WorkflowStatus.class);
                java.time.Instant startedAt = MarshallingUtils.readInstant(factory, dateBytes);

                return Optional.of(new InstanceLocation(namespace, name, version, instanceId, applicationId, status,
                        startedAt));
            }
        }

        return Optional.empty();
    }
}
