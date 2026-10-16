package io.quarkiverse.flow.quartz.dbmigration.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

class FlowDbMigrationQuartzProcessor {

    private static final String FEATURE = "flow-db-migration-quartz";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }
}
