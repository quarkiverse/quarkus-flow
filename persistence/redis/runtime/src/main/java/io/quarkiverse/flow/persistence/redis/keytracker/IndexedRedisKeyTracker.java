package io.quarkiverse.flow.persistence.redis.keytracker;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.set.SetCommands;
import io.quarkus.redis.datasource.transactions.TransactionalRedisDataSource;

/**
 * Keeps a Redis Set ({@code idx:<instanceId>}) of the instance's task keys, so removal and restore read that
 * Set instead of scanning the keyspace, at the price of one {@code SADD} per task write. Instances written
 * before indexing was enabled hold no Set and fall back to {@link ScanRedisKeyTracker}.
 */
final class IndexedRedisKeyTracker extends ScanRedisKeyTracker {

    private final SetCommands<String, String> setCommands;

    IndexedRedisKeyTracker(KeyCommands<String> keyCommands, SetCommands<String, String> setCommands) {
        super(keyCommands);
        this.setCommands = setCommands;
    }

    @Override
    public void track(List<Consumer<TransactionalRedisDataSource>> operations, String instanceId, String taskKey) {
        operations.add(tx -> tx.set(String.class, String.class).sadd(RedisKeyUtils.indexKey(instanceId), taskKey));
    }

    @Override
    public Set<String> taskKeys(String instanceId) {
        Set<String> members = setCommands.smembers(RedisKeyUtils.indexKey(instanceId));
        return members.isEmpty() ? super.taskKeys(instanceId) : members;
    }
}
