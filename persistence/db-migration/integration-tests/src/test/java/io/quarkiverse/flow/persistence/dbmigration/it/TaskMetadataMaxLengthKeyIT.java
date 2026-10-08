package io.quarkiverse.flow.persistence.dbmigration.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class TaskMetadataMaxLengthKeyIT {

    private static final String APPLICATION_ID = "a".repeat(63);
    private static final String INSTANCE_ID = "i".repeat(26);
    private static final String META_NAME = "m".repeat(128);
    private static final String JSON_POINTER = "/" + "p".repeat(678);

    @Inject
    EntityManager entityManager;

    @Test
    @TestTransaction
    @DisplayName("task_metadata_entity_accepts_max_length_primary_key_values")
    void task_metadata_entity_accepts_max_length_primary_key_values() {
        entityManager.createNativeQuery(
                "insert into workflow_instance_entity "
                        + "(application_id, instance_id, workflow_name, workflow_namespace, workflow_version, started_at) "
                        + "values (:applicationId, :instanceId, 'wf', 'ns', '1.0', :startedAt)")
                .setParameter("applicationId", APPLICATION_ID)
                .setParameter("instanceId", INSTANCE_ID)
                .setParameter("startedAt", Instant.now())
                .executeUpdate();

        entityManager.createNativeQuery(
                "insert into task_info_entity "
                        + "(application_id, workflow_instance_id, json_pointer, iteration, task_type, is_end_node) "
                        + "values (:applicationId, :workflowInstanceId, :jsonPointer, :iteration, 1, :isEndNode)")
                .setParameter("applicationId", APPLICATION_ID)
                .setParameter("workflowInstanceId", INSTANCE_ID)
                .setParameter("jsonPointer", JSON_POINTER)
                .setParameter("iteration", 0)
                .setParameter("isEndNode", Boolean.FALSE)
                .executeUpdate();

        entityManager.createNativeQuery(
                "insert into task_metadata_entity "
                        + "(meta_name, iteration, json_pointer, application_id, workflow_instance_id) "
                        + "values (:metaName, :iteration, :jsonPointer, :applicationId, :workflowInstanceId)")
                .setParameter("metaName", META_NAME)
                .setParameter("iteration", 0)
                .setParameter("jsonPointer", JSON_POINTER)
                .setParameter("applicationId", APPLICATION_ID)
                .setParameter("workflowInstanceId", INSTANCE_ID)
                .executeUpdate();

        Long count = entityManager.createQuery(
                "select count(t) from TaskMetadataEntity t where t.id.metaName = :metaName", Long.class)
                .setParameter("metaName", META_NAME)
                .getSingleResult();
        assertThat(count).isEqualTo(1L);
    }
}
