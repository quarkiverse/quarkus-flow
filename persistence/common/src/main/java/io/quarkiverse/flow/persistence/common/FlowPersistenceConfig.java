package io.quarkiverse.flow.persistence.common;

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
}
