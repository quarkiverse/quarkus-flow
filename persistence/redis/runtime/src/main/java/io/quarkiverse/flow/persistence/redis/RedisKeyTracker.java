package io.quarkiverse.flow.persistence.redis;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import io.quarkiverse.flow.persistence.redis.RedisPersistenceConfig.KeyTracking;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.keys.KeyScanArgs;
import io.quarkus.redis.datasource.keys.KeyScanCursor;
import io.quarkus.redis.datasource.set.SetCommands;
import io.quarkus.redis.datasource.set.TransactionalSetCommands;
import io.quarkus.redis.datasource.transactions.TransactionalRedisDataSource;

/**
 * Finds the Redis keys of a workflow instance (its instance hash plus one hash per task) so
 * {@link RedisInstanceTransaction} can delete them on completion and reload them on restart.
 * <p>
 * Selected from {@link KeyTracking}: {@link Indexed} keeps a Redis Set per instance (one extra
 * {@code SADD} per write); {@link Scan} scans the keyspace instead (free writes). The modes are
 * interoperable, so switching needs no migration.
 */
public interface RedisKeyTracker {

    /**
     * Enqueues any index maintenance for {@code memberKey} just written under {@code indexKey}; a no-op
     * when indexing is disabled.
     */
    void track(List<Consumer<TransactionalRedisDataSource>> operations, TransactionalSetAccessor setCommands,
            String indexKey, String memberKey);

    /**
     * Keys to delete for the instance, except its own instance hash: the task hashes plus the index key.
     */
    Set<String> keysToRemove(String indexKey, String taskPrefix);

    /** Task hash keys to reload when restoring the instance. */
    Set<String> taskKeys(String indexKey, String taskPrefix);

    static RedisKeyTracker forMode(KeyTracking mode, KeyCommands<String> keyCommands,
            SetCommands<String, String> setCommands) {
        return switch (mode) {
            case INDEXED -> new Indexed(keyCommands, setCommands);
            case SCAN -> new Scan(keyCommands);
        };
    }

    /** Supplies the transaction-scoped {@link TransactionalSetCommands}, cached and owned by the caller. */
    @FunctionalInterface
    interface TransactionalSetAccessor {
        TransactionalSetCommands<String, String> get(TransactionalRedisDataSource tx);
    }

    private static Set<String> scanTaskKeys(KeyCommands<String> keyCommands, String taskPrefix) {
        Set<String> keys = new HashSet<>();
        KeyScanCursor<String> cursor = keyCommands.scan(new KeyScanArgs().match(taskPrefix + "*"));
        while (cursor.hasNext()) {
            cursor.next().forEach(keys::add);
        }
        return keys;
    }

    /**
     * Keeps a Redis Set ({@code idx:<instanceId>}) of the instance's keys, so removal and restore read
     * that Set instead of scanning the keyspace, at the price of one {@code SADD} per write.
     */
    final class Indexed implements RedisKeyTracker {

        private final KeyCommands<String> keyCommands;
        private final SetCommands<String, String> setCommands;

        Indexed(KeyCommands<String> keyCommands, SetCommands<String, String> setCommands) {
            this.keyCommands = keyCommands;
            this.setCommands = setCommands;
        }

        @Override
        public void track(List<Consumer<TransactionalRedisDataSource>> operations, TransactionalSetAccessor setCommands,
                String indexKey, String memberKey) {
            operations.add(tx -> setCommands.get(tx).sadd(indexKey, memberKey));
        }

        @Override
        public Set<String> keysToRemove(String indexKey, String taskPrefix) {
            Set<String> keys = new HashSet<>(setCommands.smembers(indexKey));
            if (keys.isEmpty()) {
                // instance written before indexing was enabled: fall back to a one-time keyspace SCAN
                keys.addAll(scanTaskKeys(keyCommands, taskPrefix));
            } else {
                keys.add(indexKey);
            }
            return keys;
        }

        @Override
        public Set<String> taskKeys(String indexKey, String taskPrefix) {
            Set<String> members = setCommands.smembers(indexKey);
            Set<String> keys = new HashSet<>();
            if (members.isEmpty()) {
                // instance written before indexing was enabled: fall back to a one-time keyspace SCAN
                keys.addAll(scanTaskKeys(keyCommands, taskPrefix));
            } else {
                for (String member : members) {
                    if (member.startsWith(taskPrefix)) {
                        keys.add(member);
                    }
                }
            }
            return keys;
        }
    }

    /** Keeps no index: removal and restore scan the keyspace, and writes carry no extra cost. */
    final class Scan implements RedisKeyTracker {

        private final KeyCommands<String> keyCommands;

        Scan(KeyCommands<String> keyCommands) {
            this.keyCommands = keyCommands;
        }

        @Override
        public void track(List<Consumer<TransactionalRedisDataSource>> operations, TransactionalSetAccessor setCommands,
                String indexKey, String memberKey) {
            // indexing disabled: nothing to maintain
        }

        @Override
        public Set<String> keysToRemove(String indexKey, String taskPrefix) {
            Set<String> keys = scanTaskKeys(keyCommands, taskPrefix);
            // remove a stale index too, in case this instance previously ran under the 'indexed' mode
            keys.add(indexKey);
            return keys;
        }

        @Override
        public Set<String> taskKeys(String indexKey, String taskPrefix) {
            return scanTaskKeys(keyCommands, taskPrefix);
        }
    }
}
