package io.quarkiverse.flow.persistence.mvstore.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.serverlessworkflow.impl.persistence.mvstore.MVStorePersistenceStore;

public class FlowPersistenceMVstoreProcessor {

    private static final String FEATURE = "flow-persistence-mvstore";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    AdditionalBeanBuildItem additionalBean() {
        return AdditionalBeanBuildItem.builder()
                .setUnremovable()
                .addBeanClass(MVStorePersistenceStore.class)
                .build();
    }
}
