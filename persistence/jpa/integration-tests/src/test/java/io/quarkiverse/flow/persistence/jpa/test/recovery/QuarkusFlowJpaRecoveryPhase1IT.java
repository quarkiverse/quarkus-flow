package io.quarkiverse.flow.persistence.jpa.test.recovery;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.serverlessworkflow.impl.WorkflowInstance;
import io.serverlessworkflow.impl.WorkflowModel;

@QuarkusTest
@TestProfile(RecoveryPhase1Profile.class)
@Order(1)
@DisabledOnOs(OS.WINDOWS)
public class QuarkusFlowJpaRecoveryPhase1IT {

    @Inject
    RecoveryWorkflow workflow;

    @Test
    void shouldPersistIncompleteWorkflowBeforeCrash() {
        WorkflowInstance instance = workflow.instance(Map.of("seed", "start"));
        CompletableFuture<WorkflowModel> completion = instance.start();

        RecoveryTestState.awaitTasksCompleted("phase1", Duration.ofSeconds(20), "task1", "task2");

        Assertions.assertFalse(completion.isDone(), "Workflow should be waiting for resume event");
    }
}
