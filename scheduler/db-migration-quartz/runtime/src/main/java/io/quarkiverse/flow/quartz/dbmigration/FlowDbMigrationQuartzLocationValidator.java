package io.quarkiverse.flow.quartz.dbmigration;

import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import org.eclipse.microprofile.config.Config;

import io.quarkus.datasource.common.runtime.DatabaseKind.SupportedDatabaseKind;
import io.quarkus.runtime.StartupEvent;

/**
 * Mirrors FlowDbMigrationLocationValidator: the "flow-quartz" named datasource's
 * Flyway config can be set behind a runtime-activated profile, so it can only be checked
 * once that profile is resolved, i.e. at runtime rather than at build.
 * <p>
 * Unlike its sibling, this extension bundles a single, PostgreSQL-specific migration script
 * (it relies on the {@code BOOL}/{@code BYTEA} column types), so any other db-kind - including
 * one unrecognized by Quarkus - is rejected here rather than being allowed to fail later when
 * Flyway actually runs the script.
 */
@ApplicationScoped
public class FlowDbMigrationQuartzLocationValidator {

    private static final String DATASOURCE_NAME = "flow-quartz";
    private static final String EXPECTED_LOCATION = "db/flow-migration/quartz";

    void onStart(@Observes StartupEvent event, Config config) {
        Optional<String> dbKind = config.getOptionalValue("quarkus.datasource." + DATASOURCE_NAME + ".db-kind", String.class);
        if (dbKind.isEmpty()) {
            return;
        }

        if (SupportedDatabaseKind.from(dbKind.get()).filter(SupportedDatabaseKind.POSTGRESQL::equals).isEmpty()) {
            throw new IllegalStateException(
                    "quarkus-flow-db-migration-quartz is configured (quarkus.datasource." + DATASOURCE_NAME
                            + ".db-kind is set to " + dbKind.get()
                            + "), but it only ships a PostgreSQL migration script for the QRTZ_* schema. Set "
                            + "quarkus.datasource." + DATASOURCE_NAME + ".db-kind=postgresql, or do not configure the "
                            + DATASOURCE_NAME + " datasource (see the Database Schema Migration guide).");
        }

        List<String> locations = config
                .getOptionalValues("quarkus.flyway." + DATASOURCE_NAME + ".locations", String.class)
                .orElse(List.of());
        boolean pointsAtExpectedLocation = locations.stream()
                .map(location -> location.startsWith("classpath:") ? location.substring("classpath:".length()) : location)
                .anyMatch(EXPECTED_LOCATION::equals);

        if (!pointsAtExpectedLocation) {
            throw new IllegalStateException(
                    "quarkus-flow-db-migration-quartz is configured (quarkus.datasource." + DATASOURCE_NAME
                            + ".db-kind is set), but quarkus.flyway.\"" + DATASOURCE_NAME + "\".locations is " + locations
                            + " instead of [" + EXPECTED_LOCATION + "]. Set quarkus.flyway.\"" + DATASOURCE_NAME
                            + "\".locations=" + EXPECTED_LOCATION
                            + " (see the Database Schema Migration guide) so Flyway applies the QRTZ_* migration script "
                            + "through the isolated flow-quartz datasource.");
        }
    }
}
