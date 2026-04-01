package io.quarkiverse.flow.durable.kube.deployment;

import io.quarkiverse.flow.durable.kube.DeploymentPoolTopologyResolver;
import io.quarkiverse.flow.durable.kube.DevModeKubeInfoStrategy;
import io.quarkiverse.flow.durable.kube.DevPoolTopologyResolver;
import io.quarkiverse.flow.durable.kube.Fabric8KubeInfoStrategy;
import io.quarkiverse.flow.durable.kube.InjectLeaseWorkflowApplicationBuilderCustomizer;
import io.quarkiverse.flow.durable.kube.LeaseAcquisitionHealthCheck;
import io.quarkiverse.flow.durable.kube.LeaseService;
import io.quarkiverse.flow.durable.kube.MemberLeaseCoordinator;
import io.quarkiverse.flow.durable.kube.PoolLeaderController;
import io.quarkiverse.flow.durable.kube.PoolMemberController;
import io.quarkiverse.flow.durable.kube.config.FlowDurableKubeConfig;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.scheduler.deployment.ForceStartSchedulerBuildItem;

class FlowDurableKubernetesProcessor {

    private static final String FEATURE = "flow-durable-kubernetes";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    /**
     * Forces the internal Quarkus Scheduler to run since our module creates schedulers programmatically.
     *
     * @see <a href="https://quarkus.io/guides/all-builditems#scheduler">Build Items - Scheduler</a>
     */
    @BuildStep
    ForceStartSchedulerBuildItem forceStartSchedulerBuildItem() {
        return new ForceStartSchedulerBuildItem();
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
                        LeaseAcquisitionHealthCheck.class)
                .setUnremovable()
                .build();
    }

    @BuildStep
    void devModeKubeInfoStrategy(
            LaunchModeBuildItem launchMode,
            BuildProducer<AdditionalBeanBuildItem> additionalBeans) {

        // Proper OR logic: Check if we are in DEV or TEST mode
        boolean isDevOrTest = launchMode.getLaunchMode() == LaunchMode.DEVELOPMENT ||
                launchMode.getLaunchMode() == LaunchMode.TEST;

        if (isDevOrTest) {
            // Explicitly hand the classes to Arc.
            // Arc will then read the @IfBuildProperty annotation on the class to make the final decision.
            additionalBeans.produce(AdditionalBeanBuildItem.builder()
                    .addBeanClasses(
                            DevModeKubeInfoStrategy.class,
                            DevPoolTopologyResolver.class)
                    .setUnremovable()
                    .build());
        }
    }
}
