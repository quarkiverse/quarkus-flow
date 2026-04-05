package io.quarkiverse.flow.config;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface FlowDevUIBackendConfig {

    StorageConfig storage();

    @ConfigGroup
    interface StorageConfig {

        /**
         * Enables or disables backend persistence for Dev UI workflow data.
         * <p>
         * When disabled, no storage is used and no workflow metadata is persisted (DevUI only).
         * <p>
         */
        @WithDefault("false")
        boolean enabled();
    }
}
