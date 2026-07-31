package io.quarkiverse.flow.messaging.deployment;

import java.util.List;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.flow.messaging.FlowDomainEventsPublisher;
import io.quarkiverse.flow.messaging.FlowLifecycleEventsPublisher;
import io.quarkiverse.flow.messaging.FlowMessagingConsumer;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.Feature;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.builditem.RunTimeConfigurationDefaultBuildItem;
import io.quarkus.runtime.LaunchMode;

public class FlowMessagingProcessor {
    private static final String FEATURE = "flow-messaging";
    private static final String PROP_PREFIX = "quarkus.flow.messaging.";

    private static final String INCOMING_PREFIX = "mp.messaging.incoming.";
    private static final String OUTGOING_PREFIX = "mp.messaging.outgoing.";

    private static final String KAFKA_CONNECTOR = "smallrye-kafka";
    private static final String AMQP_CONNECTOR = "smallrye-amqp";

    private static final String STR_DESERIALIZER = "org.apache.kafka.common.serialization.StringDeserializer";
    private static final String STR_SERIALIZER = "org.apache.kafka.common.serialization.StringSerializer";

    private static final Logger LOG = LoggerFactory.getLogger(FlowMessagingProcessor.class.getName());

    private static void produce(BuildProducer<RunTimeConfigurationDefaultBuildItem> runtimeConfig,
            String key, String value) {
        runtimeConfig.produce(new RunTimeConfigurationDefaultBuildItem(key, value));
    }

    private static void kafkaInbound(BuildProducer<RunTimeConfigurationDefaultBuildItem> runtimeConfig,
            String channel) {
        String base = INCOMING_PREFIX + channel;
        produce(runtimeConfig, base + ".connector", KAFKA_CONNECTOR);
        produce(runtimeConfig, base + ".topic", channel);
        produce(runtimeConfig, base + ".key.deserializer", STR_DESERIALIZER);
        produce(runtimeConfig, base + ".value.deserializer", STR_DESERIALIZER);
    }

    private static void kafkaOutbound(BuildProducer<RunTimeConfigurationDefaultBuildItem> runtimeConfig,
            String channel) {
        String base = OUTGOING_PREFIX + channel;
        produce(runtimeConfig, base + ".connector", KAFKA_CONNECTOR);
        produce(runtimeConfig, base + ".topic", channel);
        produce(runtimeConfig, base + ".value.serializer", STR_SERIALIZER);
    }

    private static void amqpChannel(BuildProducer<RunTimeConfigurationDefaultBuildItem> runtimeConfig,
            String prefix, String channel) {
        String base = prefix + channel;
        produce(runtimeConfig, base + ".connector", AMQP_CONNECTOR);
        produce(runtimeConfig, base + ".address", channel);
    }

    private static boolean hasFeature(List<FeatureBuildItem> features, Feature feature) {
        return features.stream().anyMatch(f -> feature.getName().equals(f.getName()));
    }

    private static boolean flowChannelsManuallyConfigured() {
        Config config = ConfigProvider.getConfig();
        return config.getOptionalValue(INCOMING_PREFIX + "flow-in.connector", String.class).isPresent()
                || config.getOptionalValue(OUTGOING_PREFIX + "flow-out.connector", String.class).isPresent()
                || config.getOptionalValue(OUTGOING_PREFIX + "flow-lifecycle-out.connector", String.class).isPresent();
    }

    /**
     * Called when the connector detection is inconclusive (no supported connector, or both present):
     * quietly steps aside if the user already configured the default channels, warns otherwise.
     */
    private static void logSkippedAutoConfiguration(boolean kafka) {
        if (flowChannelsManuallyConfigured()) {
            LOG.debug("Flow: '{}devservices-messaging-enabled' is set and the default channels are already "
                    + "configured; skipping automatic channel configuration.", PROP_PREFIX);
        } else if (!kafka) {
            LOG.warn("Flow: '{}devservices-messaging-enabled' is set but no supported messaging connector "
                    + "(Kafka or AMQP) is present. No channel configuration was injected. "
                    + "Add 'quarkus-messaging-kafka' or 'quarkus-messaging-amqp', or configure the "
                    + "'mp.messaging.*' channels manually.",
                    PROP_PREFIX);
        } else {
            LOG.warn("Flow: '{}devservices-messaging-enabled' is set but both the Kafka and AMQP connectors "
                    + "are present. No channel configuration was injected because the target "
                    + "connector is ambiguous. Configure the 'mp.messaging.*' channels manually.",
                    PROP_PREFIX);
        }
    }

    private static void logInjectedDefaults(String connector) {
        LOG.debug("Flow: dev-services messaging - injected defaults (override in application.properties):\n"
                + "  {}defaults-enabled=true\n"
                + "  {}lifecycle-enabled=true\n"
                + "  {}{}.connector={}\n"
                + "  {}{}.connector={}\n"
                + "  {}{}.connector={}",
                PROP_PREFIX, PROP_PREFIX,
                INCOMING_PREFIX, "flow-in", connector,
                OUTGOING_PREFIX, "flow-out", connector,
                OUTGOING_PREFIX, "flow-lifecycle-out", connector);
    }

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    private static boolean isDevOrTest(LaunchModeBuildItem launchMode) {
        return launchMode.getLaunchMode() == LaunchMode.DEVELOPMENT
                || launchMode.getLaunchMode() == LaunchMode.TEST;
    }

    @BuildStep
    void registerDefaults(LaunchModeBuildItem launchMode, BuildProducer<AdditionalBeanBuildItem> beans,
            FlowMessagingBuildConfig config) {
        if (!config.defaultsEnabled()) {
            return;
        }

        // the devservicesMessaging step owns bean registration when the devservices flag is active
        if (config.devservicesMessagingEnabled() && isDevOrTest(launchMode)) {
            return;
        }

        // Register messaging beans unconditionally - channel configuration is runtime-based
        // SmallRye Reactive Messaging will handle missing channels gracefully
        final AdditionalBeanBuildItem.Builder builder = AdditionalBeanBuildItem.builder()
                .addBeanClass(FlowMessagingConsumer.class)
                .addBeanClass(FlowDomainEventsPublisher.class);

        LOG.info("Flow: default engine publisher and consumer enabled.");

        if (config.lifecycleEnabled()) {
            builder.addBeanClass(FlowLifecycleEventsPublisher.class);
            LOG.info("Flow: lifecycle publisher enabled (flow-lifecycle-out).");
        } else {
            LOG.info("Flow: lifecycle publisher disabled.");
        }

        beans.produce(builder.setUnremovable().build());
    }

    @BuildStep
    void devservicesMessaging(LaunchModeBuildItem launchMode,
            List<FeatureBuildItem> features,
            BuildProducer<RunTimeConfigurationDefaultBuildItem> runtimeConfig,
            BuildProducer<AdditionalBeanBuildItem> beans,
            FlowMessagingBuildConfig buildConfig) {
        if (!isDevOrTest(launchMode) || !buildConfig.devservicesMessagingEnabled()) {
            return;
        }

        boolean kafka = hasFeature(features, Feature.MESSAGING_KAFKA);
        boolean amqp = hasFeature(features, Feature.MESSAGING_AMQP);
        boolean exactlyOneConnector = kafka != amqp;

        if (!exactlyOneConnector) {
            logSkippedAutoConfiguration(kafka);
            return;
        }

        String connector = kafka ? KAFKA_CONNECTOR : AMQP_CONNECTOR;
        LOG.info("Flow: dev-services messaging enabled in {}, injecting runtime config defaults for the '{}' connector.",
                launchMode.getLaunchMode(), connector);

        produce(runtimeConfig, PROP_PREFIX + "defaults-enabled", "true");
        produce(runtimeConfig, PROP_PREFIX + "lifecycle-enabled", "true");

        if (kafka) {
            kafkaInbound(runtimeConfig, "flow-in");
            kafkaOutbound(runtimeConfig, "flow-out");
            kafkaOutbound(runtimeConfig, "flow-lifecycle-out");
        } else {
            amqpChannel(runtimeConfig, INCOMING_PREFIX, "flow-in");
            amqpChannel(runtimeConfig, OUTGOING_PREFIX, "flow-out");
            amqpChannel(runtimeConfig, OUTGOING_PREFIX, "flow-lifecycle-out");
        }

        logInjectedDefaults(connector);

        AdditionalBeanBuildItem.Builder builder = AdditionalBeanBuildItem.builder()
                .addBeanClass(FlowMessagingConsumer.class)
                .addBeanClass(FlowDomainEventsPublisher.class)
                .addBeanClass(FlowLifecycleEventsPublisher.class);

        beans.produce(builder.setUnremovable().build());
    }
}
