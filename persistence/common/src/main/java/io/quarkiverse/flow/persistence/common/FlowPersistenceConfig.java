package io.quarkiverse.flow.persistence.common;

import java.util.List;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = FlowPersistenceConfig.PREFIX)
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
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
     *
     * Example: quarkus.flow.persistence.exclude-workflows=com.example:workflow:0.1.0,org.acme:workflow:1.2.0
     */
    Optional<List<String>> excludeWorkflows();
}