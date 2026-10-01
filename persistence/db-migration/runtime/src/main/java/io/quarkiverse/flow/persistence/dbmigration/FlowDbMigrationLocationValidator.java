package io.quarkiverse.flow.persistence.dbmigration;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import org.eclipse.microprofile.config.Config;

import io.quarkus.runtime.StartupEvent;

/**
 * quarkus.datasource."flow-runtime".db-kind and quarkus.flyway."flow-runtime".locations can
 * both be set behind a runtime-activated config profile (e.g. QUARKUS_PROFILE=postgresql
 * selecting one of several bundled dialects at container start) - a build-time check only ever
 * sees the unprofiled default and silently skips any profile-gated config, so this must be
 * validated once the active profile is actually resolved, i.e. at runtime.
 */
@ApplicationScoped
public class FlowDbMigrationLocationValidator {

    private static final String DATASOURCE_NAME = "flow-runtime";
    private static final Set<String> SUPPORTED_DB_KINDS = Set.of("h2", "mysql", "postgresql", "oracle", "mssql");

    void onStart(@Observes StartupEvent event, Config config) {
        Optional<String> dbKind = config.getOptionalValue("quarkus.datasource." + DATASOURCE_NAME + ".db-kind", String.class)
                .map(kind -> kind.toLowerCase(Locale.ROOT));
        if (dbKind.isEmpty() || !SUPPORTED_DB_KINDS.contains(dbKind.get())) {
            return;
        }

        List<String> locations = config.getOptionalValues("quarkus.flyway." + DATASOURCE_NAME + ".locations", String.class)
                .orElse(List.of());
        String expectedLocation = "db/flow-migration/runtime/" + dbKind.get();
        boolean pointsAtExpectedLocation = locations.stream()
                .map(location -> location.startsWith("classpath:") ? location.substring("classpath:".length()) : location)
                .anyMatch(expectedLocation::equals);

        if (!pointsAtExpectedLocation) {
            throw new IllegalStateException(
                    "quarkus-flow-db-migration is configured (quarkus.datasource." + DATASOURCE_NAME + ".db-kind is set to "
                            + dbKind.get() + "), but quarkus.flyway.\"" + DATASOURCE_NAME + "\".locations is " + locations
                            + " instead of [" + expectedLocation + "]. Set quarkus.flyway.\"" + DATASOURCE_NAME
                            + "\".locations=" + expectedLocation
                            + " (see the Database Schema Migration guide) so Flyway applies this extension's " + dbKind.get()
                            + " migration scripts through the isolated flow-runtime datasource.");
        }
    }
}
