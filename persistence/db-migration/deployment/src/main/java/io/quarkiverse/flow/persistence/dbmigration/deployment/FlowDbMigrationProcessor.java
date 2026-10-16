package io.quarkiverse.flow.persistence.dbmigration.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

class FlowDbMigrationProcessor {

    private static final String FEATURE = "flow-db-migration";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }
}
