package io.quarkiverse.flow.config;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.flow.devui")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface FlowDevUIConfig {

    FlowDevUIBackendConfig backend();

    MVStoreConfig mvstore();

    /**
     * The type of storage to use for workflow instances in dev mode.
     * <ul>
     * <li><b>in-memory</b>: Data is stored in memory and lost on restart</li>
     * <li><b>mvstore</b>: Data is persisted to a file using H2 MVStore</li>
     * </ul>
     */
    @WithDefault("mvstore")
    MVStoreConfig.StorageType storageType();

}
