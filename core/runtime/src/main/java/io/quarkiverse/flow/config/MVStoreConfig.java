package io.quarkiverse.flow.config;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface MVStoreConfig {

    String DEFAULT_MVSTORE_DB_PATH = "target/flow-devui.mv.db";

    /**
     * Path to the MVStore database file.
     * <p>
     * Only used when {@code quarkus.flow.devui.storage-type} is set to {@code mvstore}
     */
    @WithDefault(DEFAULT_MVSTORE_DB_PATH)
    String dbPath();

    /**
     * Storage type for workflow instances in Dev mode.
     */
    enum StorageType {
        /**
         * In-memory storage. Data is lost when the application restarts.
         */
        IN_MEMORY("in-memory"),

        /**
         * File-based storage using H2 MVStore. Data is persisted to disk
         * and survives application restarts.
         */
        MVSTORE("mvstore");

        private final String configValue;

        StorageType(String configValue) {
            this.configValue = configValue;
        }

        @Override
        public String toString() {
            return configValue;
        }
    }
}