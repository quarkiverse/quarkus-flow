package io.quarkiverse.flow.messaging;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.flow.messaging")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface FlowMessagingConfig {
    /**
     * Register default consumer/publisher beans bound to 'flow-in'/'flow-out' channels
     */
    @WithDefault("false")
    boolean defaultsEnabled();

    /**
     * Register the default events lifecycle publisher to the 'flow-lifecycle-out'. By default, the application won't publish
     * any lifecycle event to this channel.
     */
    @WithDefault("false")
    boolean lifecycleEnabled();
}
