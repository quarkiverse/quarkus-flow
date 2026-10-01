package io.quarkiverse.flow.persistence.dbmigration.deployment;

import java.util.Locale;
import java.util.Set;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import io.quarkiverse.flow.persistence.dbmigration.FlowDbMigrationLocationValidator;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Produce;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.pkg.builditem.ArtifactResultBuildItem;

class FlowDbMigrationProcessor {

    private static final String FEATURE = "flow-db-migration";
    private static final Set<String> ALLOWED_SCHEMA_MANAGEMENT_STRATEGIES = Set.of("none", "validate");

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    /**
     * The db-kind/locations match is validated at runtime (FlowDbMigrationLocationValidator)
     * rather than here, since both can be set behind a runtime-activated config profile - a build
     * step only ever sees the unprofiled default and would silently skip any profile-gated config.
     */
    @BuildStep
    AdditionalBeanBuildItem additionalBean() {
        return AdditionalBeanBuildItem.unremovableOf(FlowDbMigrationLocationValidator.class);
    }

    /**
     * Unlike the db-kind/locations check above, this one is safe at build time: it only needs to
     * reject a bad value, not resolve a profile-gated one, and quarkus.hibernate-orm.* is already a
     * build-time-fixed property in Quarkus Hibernate ORM regardless of runtime profile.
     */
    @BuildStep
    @Produce(ArtifactResultBuildItem.class)
    void failOnUnmanagedHibernateSchemaGeneration() {
        Config config = ConfigProvider.getConfig();
        String strategy = config.getOptionalValue("quarkus.hibernate-orm.schema-management.strategy", String.class)
                .or(() -> config.getOptionalValue("quarkus.hibernate-orm.database.generation", String.class))
                .map(value -> value.toLowerCase(Locale.ROOT))
                .orElse(null);
        if (strategy != null && !ALLOWED_SCHEMA_MANAGEMENT_STRATEGIES.contains(strategy)) {
            throw new IllegalStateException(
                    "quarkus-flow-db-migration is on the classpath, but quarkus.hibernate-orm.schema-management.strategy="
                            + strategy + " lets Hibernate ORM manage DDL too, which can race this extension's Flyway "
                            + "migration. Set it to \"none\" or \"validate\" (see the Database Schema Migration guide).");
        }
    }
}
