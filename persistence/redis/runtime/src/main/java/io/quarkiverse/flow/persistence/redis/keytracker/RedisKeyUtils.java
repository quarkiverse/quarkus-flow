package io.quarkiverse.flow.persistence.redis.keytracker;

import java.util.HashSet;
import java.util.Set;

import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.keys.KeyScanArgs;
import io.quarkus.redis.datasource.keys.KeyScanCursor;

public final class RedisKeyUtils {

    private static final String SEPARATOR = ":";
    private static final String INDEX_PREFIX = "idx" + SEPARATOR;

    private RedisKeyUtils() {
    }

    /** Set key holding the task keys of {@code instanceId}. */
    public static String indexKey(String instanceId) {
        return INDEX_PREFIX + instanceId;
    }

    /**
     * Marker key present only for instances whose index has been maintained since creation. Its absence
     * means the index may be partial (the instance predates {@code indexed} mode), so callers must scan.
     */
    public static String indexMarkerKey(String instanceId) {
        return INDEX_PREFIX + instanceId + SEPARATOR + "sync";
    }

    /** Common prefix of every task hash key of {@code instanceId}. */
    public static String taskPrefix(String instanceId) {
        return instanceId + SEPARATOR;
    }

    static Set<String> scanTaskKeys(KeyCommands<String> keyCommands, String instanceId) {
        Set<String> keys = new HashSet<>();
        KeyScanCursor<String> cursor = keyCommands.scan(new KeyScanArgs().match(taskPrefix(instanceId) + "*"));
        while (cursor.hasNext()) {
            cursor.next().forEach(keys::add);
        }
        return keys;
    }
}
