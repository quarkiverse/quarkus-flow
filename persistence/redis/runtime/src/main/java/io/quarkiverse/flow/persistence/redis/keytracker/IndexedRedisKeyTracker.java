package io.quarkiverse.flow.persistence.redis.keytracker;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.set.SetCommands;
import io.quarkus.redis.datasource.transactions.TransactionalRedisDataSource;

/**
 * Keeps a Redis Set ({@code idx:<instanceId>}) of the instance's task keys, so removal and restore read that
 * Set instead of scanning the keyspace, at the price of one {@code SADD} per task write.
 * <p>
 * The Set is trusted only for instances created under {@code indexed} mode, which carry a completeness
 * marker ({@code idx:<instanceId>:sync}) written with their instance data. An instance created under
 * {@code scan} has no marker, so it is scanned via {@link ScanRedisKeyTracker} even after a switch to
 * {@code indexed} &mdash; its index is never back-filled and stays partial until the instance is removed.
 */
final class IndexedRedisKeyTracker extends ScanRedisKeyTracker {

    private final SetCommands<String, String> setCommands;

    IndexedRedisKeyTracker(KeyCommands<String> keyCommands, SetCommands<String, String> setCommands) {
        super(keyCommands);
        this.setCommands = setCommands;
    }

    @Override
    public void markComplete(List<Consumer<TransactionalRedisDataSource>> operations, String instanceId) {
        operations.add(tx -> tx.value(String.class, String.class).set(RedisKeyUtils.indexMarkerKey(instanceId), "1"));
    }

    @Override
    public void track(List<Consumer<TransactionalRedisDataSource>> operations, String instanceId, String taskKey) {
        operations.add(tx -> tx.set(String.class, String.class).sadd(RedisKeyUtils.indexKey(instanceId), taskKey));
    }

    @Override
    public Set<String> taskKeys(String instanceId) {
        if (keyCommands.exists(RedisKeyUtils.indexMarkerKey(instanceId))) {
            return setCommands.smembers(RedisKeyUtils.indexKey(instanceId));
        }
        // created before indexing was enabled: the Set may be partial, so scan the keyspace
        return super.taskKeys(instanceId);
    }
}
