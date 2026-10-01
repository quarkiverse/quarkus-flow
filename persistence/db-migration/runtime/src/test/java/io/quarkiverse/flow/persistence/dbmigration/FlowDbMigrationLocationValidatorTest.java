package io.quarkiverse.flow.persistence.dbmigration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfigBuilder;

class FlowDbMigrationLocationValidatorTest {

    private final FlowDbMigrationLocationValidator validator = new FlowDbMigrationLocationValidator();

    @Test
    @DisplayName("pg_alias_with_matching_postgresql_location_does_not_throw")
    void pg_alias_with_matching_postgresql_location_does_not_throw() {
        Config config = configOf(Map.of(
                "quarkus.datasource.flow-runtime.db-kind", "pg",
                "quarkus.flyway.flow-runtime.locations", "db/flow-migration/runtime/postgresql"));

        assertThatCode(() -> validator.onStart(null, config)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("pgsql_alias_with_matching_postgresql_location_does_not_throw")
    void pgsql_alias_with_matching_postgresql_location_does_not_throw() {
        Config config = configOf(Map.of(
                "quarkus.datasource.flow-runtime.db-kind", "pgsql",
                "quarkus.flyway.flow-runtime.locations", "db/flow-migration/runtime/postgresql"));

        assertThatCode(() -> validator.onStart(null, config)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("pg_alias_with_mismatched_location_throws_naming_the_canonical_location")
    void pg_alias_with_mismatched_location_throws_naming_the_canonical_location() {
        Config config = configOf(Map.of(
                "quarkus.datasource.flow-runtime.db-kind", "pg",
                "quarkus.flyway.flow-runtime.locations", "db/flow-migration/runtime/pg"));

        assertThatThrownBy(() -> validator.onStart(null, config))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("db/flow-migration/runtime/postgresql");
    }

    @Test
    @DisplayName("literal_postgresql_with_matching_location_does_not_throw")
    void literal_postgresql_with_matching_location_does_not_throw() {
        Config config = configOf(Map.of(
                "quarkus.datasource.flow-runtime.db-kind", "postgresql",
                "quarkus.flyway.flow-runtime.locations", "db/flow-migration/runtime/postgresql"));

        assertThatCode(() -> validator.onStart(null, config)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("unsupported_db_kind_is_skipped_without_throwing")
    void unsupported_db_kind_is_skipped_without_throwing() {
        Config config = configOf(Map.of(
                "quarkus.datasource.flow-runtime.db-kind", "db2",
                "quarkus.flyway.flow-runtime.locations", "db/flow-migration/runtime/db2"));

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
