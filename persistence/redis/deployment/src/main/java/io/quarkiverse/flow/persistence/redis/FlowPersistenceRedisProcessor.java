package io.quarkiverse.flow.persistence.redis;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

public class FlowPersistenceRedisProcessor {

    private static final String FEATURE = "flow-persistence-redis";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    AdditionalBeanBuildItem additionalBean() {
        return AdditionalBeanBuildItem.builder()
                .setUnremovable()
                .addBeanClass(RedisInstanceStore.class)
                .build();
    }
}
