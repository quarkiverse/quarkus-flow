package io.quarkiverse.flow.quartz.dbmigration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfigBuilder;

class FlowDbMigrationQuartzLocationValidatorTest {

    private final FlowDbMigrationQuartzLocationValidator validator = new FlowDbMigrationQuartzLocationValidator();

    @Test
    @DisplayName("postgresql_with_matching_location_does_not_throw")
    void postgresql_with_matching_location_does_not_throw() {
        Config config = configOf(Map.of(
                "quarkus.datasource.flow-quartz.db-kind", "postgresql",
                "quarkus.flyway.flow-quartz.locations", "db/flow-migration/quartz"));

        assertThatCode(() -> validator.onStart(null, config)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("pg_alias_with_matching_location_does_not_throw")
    void pg_alias_with_matching_location_does_not_throw() {
        Config config = configOf(Map.of(
                "quarkus.datasource.flow-quartz.db-kind", "pg",
                "quarkus.flyway.flow-quartz.locations", "db/flow-migration/quartz"));

        assertThatCode(() -> validator.onStart(null, config)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("postgresql_with_mismatched_location_throws_naming_the_expected_location")
    void postgresql_with_mismatched_location_throws_naming_the_expected_location() {
        Config config = configOf(Map.of(
                "quarkus.datasource.flow-quartz.db-kind", "postgresql",
                "quarkus.flyway.flow-quartz.locations", "db/some-other-location"));

        assertThatThrownBy(() -> validator.onStart(null, config))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("db/flow-migration/quartz");
    }

    @Test
    @DisplayName("mysql_db_kind_throws_rejecting_the_unsupported_dialect")
    void mysql_db_kind_throws_rejecting_the_unsupported_dialect() {
        Config config = configOf(Map.of(
                "quarkus.datasource.flow-quartz.db-kind", "mysql",
                "quarkus.flyway.flow-quartz.locations", "db/flow-migration/quartz"));

        assertThatThrownBy(() -> validator.onStart(null, config))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mysql")
                .hasMessageContaining("PostgreSQL");
    }

    @Test
    @DisplayName("unrecognized_db_kind_throws_rejecting_the_unsupported_dialect")
    void unrecognized_db_kind_throws_rejecting_the_unsupported_dialect() {
        Config config = configOf(Map.of(
                "quarkus.datasource.flow-quartz.db-kind", "db2",
                "quarkus.flyway.flow-quartz.locations", "db/flow-migration/quartz"));

        assertThatThrownBy(() -> validator.onStart(null, config))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("db2")
                .hasMessageContaining("PostgreSQL");
    }

    @Test
    @DisplayName("unconfigured_db_kind_is_skipped_without_throwing")
    void unconfigured_db_kind_is_skipped_without_throwing() {
        Config config = configOf(Map.of());

        assertThatCode(() -> validator.onStart(null, config)).doesNotThrowAnyException();
    }

    private static Config configOf(Map<String, String> properties) {
        return new SmallRyeConfigBuilder()
                .setAddDefaultSources(false)
                .setAddDefaultInterceptors(false)
                .withSources(new PropertiesConfigSource(properties, "test", 100))
                .build();
    }
}
