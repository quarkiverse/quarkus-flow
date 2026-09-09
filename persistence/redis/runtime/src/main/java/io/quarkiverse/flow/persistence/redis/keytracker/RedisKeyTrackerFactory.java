package io.quarkiverse.flow.persistence.redis.keytracker;

import io.quarkiverse.flow.persistence.redis.RedisPersistenceConfig.KeyTracking;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;

public final class RedisKeyTrackerFactory {

    private RedisKeyTrackerFactory() {
    }

    /**
     * The tracker for {@code mode}. {@code keyCommands} is shared by every mode; the Set commands are
     * resolved from {@code ds} only when {@link KeyTracking#INDEXED} actually needs them.
     */
    public static RedisKeyTracker forMode(KeyTracking mode, RedisDataSource ds, KeyCommands<String> keyCommands) {
        return switch (mode) {
            case INDEXED -> new IndexedRedisKeyTracker(keyCommands, ds.set(String.class, String.class));
            case SCAN -> new ScanRedisKeyTracker(keyCommands);
        };
    }
}
