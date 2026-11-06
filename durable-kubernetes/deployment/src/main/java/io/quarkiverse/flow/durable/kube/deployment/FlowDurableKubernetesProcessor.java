package io.quarkiverse.flow.durable.kube.deployment;

import io.quarkiverse.flow.durable.kube.DeploymentPoolTopologyResolver;
import io.quarkiverse.flow.durable.kube.Fabric8KubeInfoStrategy;
import io.quarkiverse.flow.durable.kube.InjectLeaseWorkflowApplicationBuilderCustomizer;
import io.quarkiverse.flow.durable.kube.LeaseAcquisitionHealthCheck;
import io.quarkiverse.flow.durable.kube.LeaseService;
import io.quarkiverse.flow.durable.kube.LocalStrategy;
import io.quarkiverse.flow.durable.kube.MemberLeaseCoordinator;
import io.quarkiverse.flow.durable.kube.PoolLeaderController;
import io.quarkiverse.flow.durable.kube.PoolMemberController;
import io.quarkiverse.flow.durable.kube.config.FlowDurableKubeConfig;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

class FlowDurableKubernetesProcessor {

    private static final String FEATURE = "flow-durable-kubernetes";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    AdditionalBeanBuildItem durableKubeBeans() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClasses(
                        PoolLeaderController.class,
                        PoolMemberController.class,
                        LeaseService.class,
                        Fabric8KubeInfoStrategy.class,
                        FlowDurableKubeConfig.class,
                        MemberLeaseCoordinator.class,
                        InjectLeaseWorkflowApplicationBuilderCustomizer.class,
                        DeploymentPoolTopologyResolver.class,
                        LeaseAcquisitionHealthCheck.class,
                        LocalStrategy.class)
                .setUnremovable()
                .build();
    }

}
