package io.quarkiverse.flow.persistence.redis.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

public class FlowPersistenceRedisProcessor {

    private static final String FEATURE = "flow-persistence-redis";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

}
