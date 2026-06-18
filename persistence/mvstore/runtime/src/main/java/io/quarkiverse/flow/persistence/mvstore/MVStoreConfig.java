package io.quarkiverse.flow.persistence.mvstore;

import io.quarkiverse.flow.persistence.common.FlowPersistenceConfig;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = FlowPersistenceConfig.PREFIX + ".mvstore")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface MVStoreConfig {

    /**
     * Path of the file holding MVStore data in the file system
     */
    String dbPath();
}
