package io.quarkiverse.flow.messaging.deployment;

import java.util.Optional;

import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.flow.messaging.FlowDomainEventsPublisher;
import io.quarkiverse.flow.messaging.FlowLifecycleEventsPublisher;
import io.quarkiverse.flow.messaging.FlowMessagingConsumer;
import io.quarkiverse.flow.messaging.ObjectMapperCloudEventCustomizer;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

public class FlowMessagingProcessor {
    private static final String FEATURE = "flow-messaging";
    private static final Logger LOG = LoggerFactory.getLogger(FlowMessagingProcessor.class.getName());

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void registerDefaults(BuildProducer<AdditionalBeanBuildItem> beans, FlowMessagingBuildConfig config) {
        if (!config.defaultsEnabled()) {
            return;
        }

        final AdditionalBeanBuildItem.Builder builder = AdditionalBeanBuildItem.builder();

        Optional<String> flowIn = ConfigProvider.getConfig()
                .getOptionalValue("mp.messaging.incoming.flow-in.connector", String.class);
        if (flowIn.isPresent()) {
            builder.addBeanClass(FlowMessagingConsumer.class);
        }

        Optional<String> flowOut = ConfigProvider.getConfig()
                .getOptionalValue("mp.messaging.outgoing.flow-out.connector", String.class);
        if (flowOut.isPresent()) {
            builder.addBeanClass(FlowDomainEventsPublisher.class);
        }

        LOG.info("Flow: default engine publisher and consumer enabled.");

        if (config.lifecycleEnabled()) {
            builder.addBeanClass(FlowLifecycleEventsPublisher.class).setUnremovable();
            LOG.info("Flow: lifecycle publisher enabled (flow-lifecycle-out).");
        } else {
            LOG.info("Flow: lifecycle publisher disabled.");
        }

        beans.produce(builder.setUnremovable().build());
    }

    @BuildStep
    AdditionalBeanBuildItem additionalBean() {
        return AdditionalBeanBuildItem.unremovableOf(ObjectMapperCloudEventCustomizer.class);
    }

}
