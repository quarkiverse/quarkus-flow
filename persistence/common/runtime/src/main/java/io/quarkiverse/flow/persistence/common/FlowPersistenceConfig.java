package io.quarkiverse.flow.persistence.common;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import io.quarkiverse.flow.persistence.common.hashing.HashingPersistenceConfig;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = FlowPersistenceConfig.PREFIX)
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface FlowPersistenceConfig {

    String PREFIX = "quarkus.flow.persistence";

    /**
     * Enable auto restoration of stored workflow instances after restart
     */
    @WithDefault("true")
    boolean autoRestore();

    /**
     * List of workflow IDs to exclude from persistence, in {@code namespace:name:version} format.
     * Workflows in this list will execute in-memory only and will not be persisted.
     * <p>
     * Example: quarkus.flow.persistence.exclude-workflows=com.example:workflow:0.1.0,org.acme:workflow:1.2.0
     */
    Optional<List<String>> excludeWorkflows();

    /**
     * Interval at which Quarkus Flow periodically re-scans the persistence store for workflow
     * instances belonging to this runner's application ID that are not currently tracked in
     * memory, and restores them. Covers instances missed during startup restore (e.g. a crash
     * between the DB write and in-memory tracking) and, in the future, instances reassigned to
     * this runner by an external rebalancer.
     * <p>
     * If not set, periodic scanning is disabled; instances are only restored once at startup.
     */
    Optional<Duration> scanInterval();

    /**
     * Configuration related with hashing functionality
     */
    HashingPersistenceConfig hashing();

}
