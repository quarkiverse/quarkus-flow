package io.quarkiverse.flow.config;

import io.quarkus.runtime.annotations.ConfigGroup;

@ConfigGroup
public interface MVStoreConfig {

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