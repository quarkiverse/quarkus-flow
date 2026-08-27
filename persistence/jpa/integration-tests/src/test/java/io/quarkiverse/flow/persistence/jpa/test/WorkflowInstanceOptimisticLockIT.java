package io.quarkiverse.flow.persistence.jpa.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import jakarta.inject.Inject;
import jakarta.persistence.OptimisticLockException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import io.quarkiverse.flow.persistence.jpa.WorkflowInstanceEntity;
import io.quarkiverse.flow.persistence.jpa.WorkflowInstanceKey;
import io.quarkiverse.flow.persistence.jpa.WorkflowInstanceRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.serverlessworkflow.impl.WorkflowDefinitionId;
import io.serverlessworkflow.impl.WorkflowStatus;

/**
 * Reproduces #864: without a @Version column, two concurrent readers of the same
 * WorkflowInstanceEntity row can each write back their changes and the second write
 * silently overwrites the first (lost update). With @Version in place, the stale
 * writer must fail with OptimisticLockException instead of clobbering the other write.
 */
@QuarkusTest
@DisabledOnOs(OS.WINDOWS)
public class WorkflowInstanceOptimisticLockIT {

    @Inject
    WorkflowInstanceRepository repository;

    @Test
    @DisplayName("test_concurrent_updates_to_same_instance_fail_fast_instead_of_losing_a_write")
    void concurrent_updates_to_same_instance_fail_fast_instead_of_losing_a_write() {
        String applicationId = "app-864";
        String instanceId = "instance-864";
        WorkflowInstanceKey key = new WorkflowInstanceKey(instanceId, applicationId);
        WorkflowDefinitionId definitionId = new WorkflowDefinitionId("ns", "hello", "1.0");

        QuarkusTransaction.requiringNew().run(() -> repository
                .persist(new WorkflowInstanceEntity(applicationId, definitionId, instanceId, Instant.now(), null)));

        WorkflowInstanceEntity readerA = QuarkusTransaction.requiringNew().call(() -> repository.findById(key));
        WorkflowInstanceEntity readerB = QuarkusTransaction.requiringNew().call(() -> repository.findById(key));

        QuarkusTransaction.requiringNew().run(() -> {
            readerB.setStatus(WorkflowStatus.RUNNING);
            repository.getEntityManager().merge(readerB);
        });

        assertThatThrownBy(() -> QuarkusTransaction.requiringNew().run(() -> {
            readerA.setStatus(WorkflowStatus.COMPLETED);
            repository.getEntityManager().merge(readerA);
        })).isInstanceOf(OptimisticLockException.class);

        WorkflowInstanceEntity persisted = QuarkusTransaction.requiringNew().call(() -> repository.findById(key));
        assertThat(persisted.getStatus()).isEqualTo(WorkflowStatus.RUNNING);
    }
}
