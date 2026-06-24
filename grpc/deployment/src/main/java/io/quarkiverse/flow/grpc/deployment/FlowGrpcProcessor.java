package io.quarkiverse.flow.grpc.deployment;

import io.quarkiverse.flow.providers.GrpcChannelProvider;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

public class FlowGrpcProcessor {

    private static final String FEATURE = "flow-grpc";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    AdditionalBeanBuildItem registerRuntimeServices() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClass(GrpcChannelProvider.class)
                .build();
    }
}
