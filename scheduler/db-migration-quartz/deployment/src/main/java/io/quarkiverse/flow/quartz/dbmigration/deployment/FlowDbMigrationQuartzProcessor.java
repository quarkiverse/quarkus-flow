package io.quarkiverse.flow.quartz.dbmigration.deployment;

import io.quarkiverse.flow.quartz.dbmigration.FlowDbMigrationQuartzLocationValidator;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

class FlowDbMigrationQuartzProcessor {

    private static final String FEATURE = "flow-db-migration-quartz";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    /**
     * The "flow-quartz" datasource's Flyway location is validated at runtime
     * (FlowDbMigrationQuartzLocationValidator) rather than here, since it can be set behind a
     * runtime-activated config profile - a build step only ever sees the unprofiled default and
     * would silently skip any profile-gated config.
     */
    @BuildStep
    AdditionalBeanBuildItem additionalBean() {
        return AdditionalBeanBuildItem.unremovableOf(FlowDbMigrationQuartzLocationValidator.class);
    }
}
