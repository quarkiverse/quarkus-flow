package org.acme.orchestrator;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.acme.orchestrator.model.BuildSpec;
import org.acme.orchestrator.model.BuildTask;
import org.acme.orchestrator.workflow.CoordinatorWorkflow;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonFormat;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.serverlessworkflow.impl.WorkflowInstance;
import io.serverlessworkflow.impl.WorkflowStatus;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration test for CoordinatorWorkflow.
 * <p>
 * This test validates that:
 * 1. The coordinator workflow executes successfully
 * 2. The forEach loop correctly emits distinct events for each task (not duplicates)
 * 3. Events are properly published to Kafka
 * <p>
 * The forEach bug (without SDK fix) would cause all emitted events to contain
 * the last item instead of distinct items.
 */
@QuarkusTest
@TestProfile(CoordinatorWorkflowIT.BroadcastProfile.class)
class CoordinatorWorkflowIT {

    private static final Logger LOG = LoggerFactory.getLogger(CoordinatorWorkflowIT.class);

    private static final JsonFormat CE_JSON = (JsonFormat) EventFormatProvider.getInstance()
            .resolveFormat(JsonFormat.CONTENT_TYPE);

    @Inject
    CoordinatorWorkflow coordinatorWorkflow;

    @Inject
    ObjectMapper objectMapper;

    // Subscribe to flow-in to capture emitted events
    @Inject
    @Channel("flow-in")
    Multi<byte[]> flowInEvents;

    private List<BuildTask> capturedTasks;

    @BeforeEach
    void setUp() {
        capturedTasks = new CopyOnWriteArrayList<>();

        // Subscribe to incoming events and parse BuildTask CloudEvents
        flowInEvents.subscribe().with(eventBytes -> {
            try {
                CloudEvent ce = CE_JSON.deserialize(eventBytes);

                // Filter for task.started events
                if (ce.getType().equals("org.acme.build.task.started")) {
                    BuildTask task = objectMapper.readValue(Objects.requireNonNull(ce.getData()).toBytes(), BuildTask.class);
                    capturedTasks.add(task);
                    LOG.debug("Captured emitted task: {} ({})", task.name(), task.id());
                }
            } catch (Exception e) {
                LOG.error("Failed to parse CloudEvent", e);
            }
        });
    }

    @Test
    @DisplayName("should_execute_coordinator_workflow_for_single_task")
    void test_single_task_execution() {
        // Given - a build spec with only one task
        BuildSpec spec = new BuildSpec(
                "single-task-project",
                "main",
                List.of("lint"));

        // When - start the coordinator workflow and wait for completion
        WorkflowInstance instance = coordinatorWorkflow.instance(spec);
        instance.start().join();

        // Then - coordinator workflow should have completed successfully
        assertThat(instance.status()).isEqualTo(WorkflowStatus.COMPLETED);

        // And - should have emitted exactly 1 event with correct task
        await()
                .atMost(5, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(capturedTasks).hasSize(1));

        assertThat(capturedTasks)
                .extracting(BuildTask::name)
                .containsExactly("lint");

        assertThat(capturedTasks)
                .extracting(BuildTask::id)
                .containsExactly("single-task-project-lint");

        LOG.info("✓ Coordinator workflow executed successfully for single task");
        LOG.info("  Emitted task: {}", capturedTasks.get(0).id());
    }

    @Test
    @DisplayName("should_execute_coordinator_workflow_for_multiple_tasks")
    void test_multiple_task_execution() {
        // Given - a build spec with three tasks
        BuildSpec spec = new BuildSpec(
                "multi-task-project",
                "main",
                List.of("lint", "test", "build"));

        // When - start the coordinator workflow and wait for completion
        WorkflowInstance instance = coordinatorWorkflow.instance(spec);
        instance.start().join();

        // Then - coordinator should have completed successfully
        assertThat(instance.status()).isEqualTo(WorkflowStatus.COMPLETED);

        // And - should have emitted exactly 3 distinct events (not duplicates!)
        // This is the CRITICAL test for the forEach bug fix
        await()
                .atMost(5, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(capturedTasks).hasSize(3));

        // Verify all three distinct task names were emitted
        assertThat(capturedTasks)
                .extracting(BuildTask::name)
                .containsExactlyInAnyOrder("lint", "test", "build");

        // Verify no duplicates (without fix, all would be "build")
        assertThat(capturedTasks)
                .extracting(BuildTask::id)
                .containsExactlyInAnyOrder(
                        "multi-task-project-lint",
                        "multi-task-project-test",
                        "multi-task-project-build");

        LOG.info("✓ Coordinator workflow executed successfully for {} tasks", spec.tasks().size());
        capturedTasks.forEach(task -> LOG.info("  - Emitted task: {} ({})", task.name(), task.id()));
    }

    @Test
    @DisplayName("should_decompose_spec_into_tasks")
    void test_task_decomposition() {
        // Given - a build spec with specific project and task names
        BuildSpec spec = new BuildSpec(
                "decompose-test",
                "feature-branch",
                List.of("lint", "test"));

        // When - start the coordinator workflow and wait for completion
        WorkflowInstance instance = coordinatorWorkflow.instance(spec);
        instance.start().join();

        // Then - coordinator should have completed successfully
        assertThat(instance.status()).isEqualTo(WorkflowStatus.COMPLETED);

        // And - should have emitted correct tasks
        await()
                .atMost(5, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(capturedTasks).hasSize(2));

        assertThat(capturedTasks)
                .extracting(BuildTask::id)
                .containsExactlyInAnyOrder("decompose-test-lint", "decompose-test-test");

        assertThat(capturedTasks)
                .extracting(BuildTask::projectName)
                .containsOnly("decompose-test");

        assertThat(capturedTasks)
                .extracting(BuildTask::gitRef)
                .containsOnly("feature-branch");

        LOG.info("✓ Coordinator decomposed BuildSpec successfully");
        LOG.info("  Expected task ID pattern: {projectName}-{taskName}");
        capturedTasks.forEach(task -> LOG.info("  Generated ID: {}", task.id()));
    }

    /**
     * Test profile that enables broadcast for flow-in channel
     * so both the workflow and the test can consume events.
     */
    public static class BroadcastProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "mp.messaging.incoming.flow-in.broadcast", "true");
        }
    }
}