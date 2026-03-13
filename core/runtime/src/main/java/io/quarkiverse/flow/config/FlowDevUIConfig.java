package io.quarkiverse.flow.config;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.flow.devui")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface FlowDevUIConfig {

    /**
     * Storage type for workflow instances in dev mode.
     */
    enum StorageType {
        /**
         * In-memory storage. Data is lost when the application restarts.
         */
        IN_MEMORY,

        /**
         * File-based storage using H2 MVStore. Data is persisted to disk
         * and survives application restarts.
         */
        MVSTORE
    }

    /**
     * The type of storage to use for workflow instances in dev mode.
     * <ul>
     * <li><b>in-memory</b>: Data is stored in memory and lost on restart</li>
     * <li><b>mvstore</b>: Data is persisted to a file using H2 MVStore</li>
     * </ul>
     */
    @WithDefault("mvstore")
    StorageType storageType();

    /**
     * MVStore-specific configuration options.
     * Only applicable when {@code storage-type=mvstore}.
     */
    MVStore mvstore();

    @ConfigGroup
    interface MVStore {

        /**
         * Path to the MVStore database file.
         * <p>
         * Only used when {@code storage-type=mvstore}.
         */
        @WithDefault("target/flow-devui.mv.db")
        String dbPath();
    }
}
