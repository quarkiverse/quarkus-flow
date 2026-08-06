package io.quarkiverse.flow.opentelemetry.deployment;

import static io.quarkus.arc.processor.DotNames.SINGLETON;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.flow.opentelemetry.runtime.OTelWorkflowExecutionListener;
import io.quarkiverse.flow.opentelemetry.runtime.SpanBuilderFactory;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.RemovedResourceBuildItem;
import io.quarkus.maven.dependency.ArtifactKey;
import io.quarkus.opentelemetry.runtime.config.build.OTelBuildConfig;
import io.serverlessworkflow.impl.events.EmittedEventDecorator;

class FlowOTelProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlowOTelProcessor.class);
    private static final String FEATURE = "flow-opentelemetry";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void produceBeans(OTelBuildConfig oTelBuildConfig, BuildProducer<AdditionalBeanBuildItem> additionalBeans,
            BuildProducer<RemovedResourceBuildItem> removedResources) {
        // depends on both Quarkus OpenTelemetry quarkus.otel.enabled=true and quarkus.otel.traces.enabled=true.
        boolean otelEnabled = oTelBuildConfig.enabled();
        boolean tracingEnabled = oTelBuildConfig.traces().enabled().orElse(true);
        if (otelEnabled && tracingEnabled) {
            additionalBeans.produce(AdditionalBeanBuildItem.builder()
                    .addBeanClass(SpanBuilderFactory.class)
                    .addBeanClass(OTelWorkflowExecutionListener.class)
                    .setDefaultScope(SINGLETON)
                    .setUnremovable()
                    .build());
        } else {
            removedResources.produce(
                    new RemovedResourceBuildItem(ArtifactKey.fromString("io.quarkiverse.flow:quarkus-flow-opentelemetry"),
                            Set.of("META-INF/services/" + EmittedEventDecorator.class.getName())));
            LOGGER.warn(
                    "Quarkus Flow extension 'quarkus-flow-opentelemetry' is present, but Quarkus OpenTelemetry is disabled. Quarkus Flow OpenTelemetry requires both quarkus.otel.enabled=true and quarkus.otel.traces.enabled=true. Current values: quarkus.otel.enabled={}, quarkus.otel.traces.enabled={}",
                    otelEnabled, tracingEnabled);
        }
    }
}
