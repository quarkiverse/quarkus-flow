package io.quarkiverse.flow.persistence.dbmigration.it;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkus.hibernate.orm.PersistenceUnit;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Runs under every database profile (see application.properties). Application startup
 * already failed if quarkus.hibernate-orm.schema-management.strategy=validate found any mismatch
 * between the schema created by this extension's Flyway scripts and the JPA entity mappings
 * shipped by quarkus-flow-jpa; querying each mapped table below confirms the schema is not
 * just structurally valid but actually queryable through those mappings.
 */
@QuarkusTest
class FlowJpaSchemaMigrationIT {

    @Inject
    @PersistenceUnit("flow-runtime")
    EntityManager entityManager;

    @Test
    @DisplayName("flyway_migrated_schema_passes_hibernate_validate_and_is_queryable")
    void flyway_migrated_schema_passes_hibernate_validate_and_is_queryable() {
        assertThat(countOf("WorkflowInstanceEntity")).isZero();
        assertThat(countOf("CloudEventEntity")).isZero();
        assertThat(countOf("TaskInfoEntity")).isZero();
    }

    private long countOf(String entityName) {
        return (long) entityManager.createQuery("select count(e) from " + entityName + " e").getSingleResult();
    }
}
