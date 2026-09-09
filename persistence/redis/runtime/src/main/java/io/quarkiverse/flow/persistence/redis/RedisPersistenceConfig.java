package io.quarkiverse.flow.persistence.redis;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.flow.persistence.redis")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface RedisPersistenceConfig {

    enum KeyTracking {
        INDEXED("indexed"),
        SCAN("scan");

        private final String value;

        KeyTracking(String value) {
            this.value = value;
        }

        public String toString() {
            return this.value;
        }
    }

    /**
     * How the Redis persistence store tracks which keys belong to a workflow
     * instance, so it can remove them on instance completion and reload them when
     * restoring pending instances after a restart.
     * <p>
     * The choice trades write throughput against removal/restore cost:
     * <ul>
     * <li>{@code indexed} (default) keeps a per-instance Redis Set so removal and
     * restore avoid scanning the keyspace, at the cost of one extra {@code SADD}
     * on every write. Best for many short-lived instances or frequent removal.</li>
     * <li>{@code scan} keeps no index and falls back to a keyspace {@code SCAN} for
     * removal and restore. Best for few, long-lived instances with many tasks,
     * where removal is rare.</li>
     * </ul>
     * Both modes remain interoperable: switching to {@code indexed} lets existing
     * unindexed instances fall back to a one-time {@code SCAN}, and instances
     * written while {@code indexed} are still removable under {@code scan}.
     */
    @WithDefault("indexed")
    KeyTracking keyTracking();

}
