package io.quarkiverse.flow.runner.deployment;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.jandex.DotName;

import io.quarkiverse.flow.runner.FlowRunnerSourceWatchConfig;
import io.quarkiverse.flow.runner.WorkflowFileWatcher;
import io.quarkiverse.flow.runner.model.ExecutionResponse;
import io.quarkiverse.flow.runner.model.Link;
import io.quarkiverse.flow.runner.model.Links;
import io.quarkiverse.flow.runner.model.WorkflowDefinitionHeader;
import io.quarkiverse.flow.runner.security.ApiKeyAuthenticationMechanism;
import io.quarkiverse.flow.runner.security.NamespaceAuthorizationFilter;
import io.quarkiverse.flow.runner.security.NamespaceAuthorizationService;
import io.quarkiverse.flow.runner.security.PermitAllAuthenticationMechanism;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.ExcludedTypeBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.scheduler.deployment.ForceStartSchedulerBuildItem;

class FlowRunnerProcessor {

    private static final String FEATURE = "flow-runner";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    AdditionalBeanBuildItem registerBeans() {
        return AdditionalBeanBuildItem.builder()
                .setUnremovable()
                .addBeanClass(NamespaceAuthorizationService.class)
                .addBeanClass(NamespaceAuthorizationFilter.class)
                .addBeanClass(ApiKeyAuthenticationMechanism.class)
                .addBeanClass(PermitAllAuthenticationMechanism.class)
                .build();
    }

    @BuildStep
    ReflectiveClassBuildItem registerForReflection() {
        return ReflectiveClassBuildItem.builder(
                ExecutionResponse.class,
                WorkflowDefinitionHeader.class,
                Link.class,
                Links.class).methods().fields().build();
    }

    @BuildStep
    void registerFileWatcher(FlowRunnerSourceWatchConfig watchConfig,
            BuildProducer<AdditionalBeanBuildItem> additionalBeans,
            BuildProducer<ForceStartSchedulerBuildItem> forceScheduler,
            BuildProducer<ExcludedTypeBuildItem> excludedTypes) {
        if (watchConfig.enabled()) {
            additionalBeans.produce(AdditionalBeanBuildItem.builder()
                    .setUnremovable()
                    .setDefaultScope(DotName.createSimple(ApplicationScoped.class))
                    .addBeanClass(WorkflowFileWatcher.class)
                    .build());
            forceScheduler.produce(new ForceStartSchedulerBuildItem());
        } else {
            excludedTypes.produce(new ExcludedTypeBuildItem(WorkflowFileWatcher.class.getName()));
        }
    }

}
