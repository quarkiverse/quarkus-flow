package io.quarkiverse.flow.persistence.redis.keytracker;

import java.util.Set;

import io.quarkus.redis.datasource.keys.KeyCommands;

/**
 * Resolves an instance's task keys with a one-time keyspace {@code SCAN} of {@code <instanceId>:*}. Keeps no
 * index, so writes stay free.
 */
class ScanRedisKeyTracker implements RedisKeyTracker {

    private final KeyCommands<String> keyCommands;

    ScanRedisKeyTracker(KeyCommands<String> keyCommands) {
        this.keyCommands = keyCommands;
    }

    @Override
    public Set<String> taskKeys(String instanceId) {
        return RedisKeyUtils.scanTaskKeys(keyCommands, instanceId);
    }
}
