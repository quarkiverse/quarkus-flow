package io.quarkiverse.flow.messaging.deployment;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.flow.messaging")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface FlowMessagingBuildConfig {

    /**
     * Automatically configure Kafka connector properties for the default Flow messaging channels
     * ('flow-in', 'flow-out', 'flow-lifecycle-out') in dev and test mode.
     * When enabled, the build step injects all necessary Kafka defaults so a developer
     * does not need to manually set any {@code mp.messaging.*} property.
     * All injected defaults can be overridden in {@code application.properties}.
     * <p>
     * Quarkus DevServices will automatically start a Kafka broker when
     * {@code quarkus.kafka.devservices.enabled} is not set to {@code false}.
     * <p>
     * Ignored in production.
     */
    @WithDefault("false")
    boolean devservicesKafkaEnabled();

    /**
     * Register the default consumer/publisher beans bound to 'flow-in'/'flow-out' channels.
     */
    @WithDefault("false")
    boolean defaultsEnabled();

    /**
     * Register the default events lifecycle publisher to the 'flow-lifecycle-out'. By default,
     * the application won't publish any lifecycle event to this channel.
     */
    @WithDefault("false")
    boolean lifecycleEnabled();
}
