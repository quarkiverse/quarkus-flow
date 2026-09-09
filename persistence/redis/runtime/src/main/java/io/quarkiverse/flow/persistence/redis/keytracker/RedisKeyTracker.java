package io.quarkiverse.flow.persistence.redis.keytracker;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import io.quarkiverse.flow.persistence.redis.RedisInstanceTransaction;
import io.quarkiverse.flow.persistence.redis.RedisPersistenceConfig.KeyTracking;
import io.quarkus.redis.datasource.transactions.TransactionalRedisDataSource;

/**
 * Finds the task hash keys of a workflow instance so {@link RedisInstanceTransaction} can delete them on
 * completion and reload them on restart.
 * <p>
 * Selected from {@link KeyTracking}: {@link IndexedRedisKeyTracker} keeps a Redis Set per instance (one extra
 * {@code SADD} per task write); {@link ScanRedisKeyTracker} scans the keyspace instead (free writes). The
 * modes are interoperable, so switching needs no migration.
 */
public interface RedisKeyTracker {

    /**
     * Enqueues any index maintenance for {@code taskKey}, just written for {@code instanceId}; a no-op when
     * indexing is disabled.
     */
    default void track(List<Consumer<TransactionalRedisDataSource>> operations, String instanceId, String taskKey) {
    }

    /** Task hash keys of the instance: to delete on completion, and to reload on restart. */
    Set<String> taskKeys(String instanceId);
}
