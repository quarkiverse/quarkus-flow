package io.quarkiverse.flow.messaging.deployment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.flow.messaging.FlowDomainEventsPublisher;
import io.quarkiverse.flow.messaging.FlowLifecycleEventsPublisher;
import io.quarkiverse.flow.messaging.FlowMessagingConsumer;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
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

    private static final String KAFKA = "smallrye-kafka";

    private static final String STR_DESERIALIZER = "org.apache.kafka.common.serialization.StringDeserializer";
    private static final String BYTES_DESERIALIZER = "org.apache.kafka.common.serialization.ByteArrayDeserializer";
    private static final String STR_SERIALIZER = "org.apache.kafka.common.serialization.StringSerializer";
    private static final String BYTES_SERIALIZER = "org.apache.kafka.common.serialization.ByteArraySerializer";

    private static final Logger LOG = LoggerFactory.getLogger(FlowMessagingProcessor.class.getName());

    private static void produce(BuildProducer<RunTimeConfigurationDefaultBuildItem> runtimeConfig,
            String key, String value) {
        runtimeConfig.produce(new RunTimeConfigurationDefaultBuildItem(key, value));
    }

    private static void inbound(BuildProducer<RunTimeConfigurationDefaultBuildItem> runtimeConfig,
            String topic, String keyDeserializer, String valueDeserializer) {
        String base = INCOMING_PREFIX + topic;
        produce(runtimeConfig, base + ".connector", KAFKA);
        produce(runtimeConfig, base + ".topic", topic);
        produce(runtimeConfig, base + ".key.deserializer", keyDeserializer);
        produce(runtimeConfig, base + ".value.deserializer", valueDeserializer);
    }

    private static void outbound(BuildProducer<RunTimeConfigurationDefaultBuildItem> runtimeConfig,
            String topic, String keySerializer, String valueSerializer) {
        String base = OUTGOING_PREFIX + topic;
        produce(runtimeConfig, base + ".connector", KAFKA);
        produce(runtimeConfig, base + ".topic", topic);
        produce(runtimeConfig, base + ".key.serializer", keySerializer);
        produce(runtimeConfig, base + ".value.serializer", valueSerializer);
    }

    private static void logInjectedDefaults() {
        LOG.info("Flow: dev-services Kafka - injected defaults (override in application.properties):\n"
                + "  {}defaults-enabled=true\n"
                + "  {}lifecycle-enabled=true\n"
                + "  {}{}.connector={}\n"
                + "  {}{}.topic={}\n"
                + "  {}{}.connector={}\n"
                + "  {}{}.topic={}\n"
                + "  {}{}.connector={}\n"
                + "  {}{}.topic={}",
                PROP_PREFIX, PROP_PREFIX,
                INCOMING_PREFIX, "flow-in", KAFKA,
                INCOMING_PREFIX, "flow-in", "flow-in",
                OUTGOING_PREFIX, "flow-out", KAFKA,
                OUTGOING_PREFIX, "flow-out", "flow-out",
                OUTGOING_PREFIX, "flow-lifecycle-out", KAFKA,
                OUTGOING_PREFIX, "flow-lifecycle-out", "flow-lifecycle-out");
    }

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void registerDefaults(BuildProducer<AdditionalBeanBuildItem> beans, FlowMessagingBuildConfig config) {
        if (!config.defaultsEnabled()) {
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
    void devservicesKafka(LaunchModeBuildItem launchMode,
            BuildProducer<RunTimeConfigurationDefaultBuildItem> runtimeConfig,
            BuildProducer<AdditionalBeanBuildItem> beans,
            FlowMessagingBuildConfig buildConfig) {
        boolean isDevOrTest = launchMode.getLaunchMode() == LaunchMode.DEVELOPMENT
                || launchMode.getLaunchMode() == LaunchMode.TEST;

        if (!isDevOrTest || !buildConfig.devservicesKafkaEnabled()) {
            return;
        }

        LOG.info(
                "Flow: dev-services VKafka enabled in {}, injecting runtime config defaults.",
                launchMode.getLaunchMode());

        produce(runtimeConfig, PROP_PREFIX + "defaults-enabled", "true");
        produce(runtimeConfig, PROP_PREFIX + "lifecycle-enabled", "true");

        inbound(runtimeConfig, "flow-in", STR_DESERIALIZER, BYTES_DESERIALIZER);
        outbound(runtimeConfig, "flow-out", STR_SERIALIZER, BYTES_SERIALIZER);
        outbound(runtimeConfig, "flow-lifecycle-out", STR_SERIALIZER, BYTES_SERIALIZER);

        logInjectedDefaults();

        AdditionalBeanBuildItem.Builder builder = AdditionalBeanBuildItem.builder()
                .addBeanClass(FlowMessagingConsumer.class)
                .addBeanClass(FlowDomainEventsPublisher.class)
                .addBeanClass(FlowLifecycleEventsPublisher.class);

        beans.produce(builder.setUnremovable().build());
    }
}
