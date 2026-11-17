package io.quarkiverse.flow.persistence.redis;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.persistence.spi.InstanceLocation;
import io.quarkiverse.flow.persistence.spi.InstanceLocator;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.hash.HashCommands;

@ApplicationScoped
public class RedisInstanceLocator implements InstanceLocator {

    static final String APPLICATION_ID = "applicationId";

    private final HashCommands<String, String, byte[]> indexCommands;

    public RedisInstanceLocator(RedisDataSource ds) {
        this.indexCommands = ds.hash(String.class, String.class, byte[].class);
    }

    @Override
    public Optional<InstanceLocation> locate(String namespace, String name, String version, String instanceId) {
        String indexKey = indexKey(namespace, name, version, instanceId);
        byte[] applicationIdBytes = indexCommands.hget(indexKey, APPLICATION_ID);
        if (applicationIdBytes == null) {
            return Optional.empty();
        }
        String applicationId = new String(applicationIdBytes, StandardCharsets.UTF_8);

        return Optional.of(new InstanceLocation(namespace, name, version, instanceId, applicationId));
    }

    static String indexKey(String namespace, String name, String version, String instanceId) {
        return encode(instanceId) + ":" + encode(namespace) + ":" + encode(name) + ":" + encode(version);
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
