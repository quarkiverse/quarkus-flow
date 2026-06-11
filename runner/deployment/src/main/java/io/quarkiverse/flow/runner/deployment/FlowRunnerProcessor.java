package io.quarkiverse.flow.runner.deployment;

import io.quarkiverse.flow.runner.model.ExecutionResponse;
import io.quarkiverse.flow.runner.model.Link;
import io.quarkiverse.flow.runner.model.Links;
import io.quarkiverse.flow.runner.model.WorkflowDefinitionHeader;
import io.quarkiverse.flow.runner.security.ApiKeyAuthenticationMechanism;
import io.quarkiverse.flow.runner.security.NamespaceAuthorizationFilter;
import io.quarkiverse.flow.runner.security.NamespaceAuthorizationService;
import io.quarkiverse.flow.runner.security.PermitAllAuthenticationMechanism;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;

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
        // Register model classes for reflection (JSON serialization)
        return ReflectiveClassBuildItem.builder(
                ExecutionResponse.class,
                WorkflowDefinitionHeader.class,
                Link.class,
                Links.class).methods().fields().build();
    }

}
