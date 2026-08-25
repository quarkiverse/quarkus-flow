package io.quarkiverse.flow.runner.instances;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Collection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.runner.model.InstanceSnapshot;
import io.serverlessworkflow.api.types.Document;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowContextData;
import io.serverlessworkflow.impl.WorkflowDefinitionData;
import io.serverlessworkflow.impl.WorkflowInstanceData;
import io.serverlessworkflow.impl.WorkflowStatus;
import io.serverlessworkflow.impl.lifecycle.WorkflowCancelledEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowCompletedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowFailedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowResumedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowStartedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowStatusEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowSuspendedEvent;

@DisplayName("ActiveInstanceRegistry Tests")
class ActiveInstanceRegistryTest {

    private ActiveInstanceRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ActiveInstanceRegistry();
    }

    // --- helpers ---

    private WorkflowContextData contextData(String instanceId, String name, String namespace,
            String version, WorkflowStatus status, Instant startedAt) {
        WorkflowInstanceData instanceData = mock(WorkflowInstanceData.class);
        when(instanceData.id()).thenReturn(instanceId);
        when(instanceData.status()).thenReturn(status);
        when(instanceData.startedAt()).thenReturn(startedAt);

        Document doc = mock(Document.class);
        when(doc.getName()).thenReturn(name);
        when(doc.getNamespace()).thenReturn(namespace);
        when(doc.getVersion()).thenReturn(version);

        Workflow workflow = mock(Workflow.class);
        when(workflow.getDocument()).thenReturn(doc);

        WorkflowDefinitionData defData = mock(WorkflowDefinitionData.class);
        when(defData.workflow()).thenReturn(workflow);

        WorkflowContextData ctx = mock(WorkflowContextData.class);
        when(ctx.instanceData()).thenReturn(instanceData);
        when(ctx.definition()).thenReturn(defData);
        return ctx;
    }

    private WorkflowStartedEvent startedEvent(String instanceId, String name, String namespace,
            String version) {
        return new WorkflowStartedEvent(contextData(instanceId, name, namespace, version,
                WorkflowStatus.RUNNING, Instant.now()));
    }

    // --- tests ---

    @Test
    @DisplayName("test_registry_is_empty_on_creation")
    void test_registry_is_empty_on_creation() {
        assertThat(registry.activeInstances()).isEmpty();
    }

    @Test
    @DisplayName("test_started_instance_is_tracked")
    void test_started_instance_is_tracked() {
        registry.onWorkflowStarted(startedEvent("inst-1", "my-flow", "default", "1.0.0"));

        Collection<InstanceSnapshot> instances = registry.activeInstances();
        assertThat(instances).hasSize(1);

        InstanceSnapshot snap = instances.iterator().next();
        assertThat(snap.instanceId()).isEqualTo("inst-1");
        assertThat(snap.workflowName()).isEqualTo("my-flow");
        assertThat(snap.workflowNamespace()).isEqualTo("default");
        assertThat(snap.workflowVersion()).isEqualTo("1.0.0");
        assertThat(snap.status()).isEqualTo(WorkflowStatus.RUNNING);
        assertThat(snap.startedAt()).isNotNull();
    }

    @Test
    @DisplayName("test_completed_instance_is_removed")
    void test_completed_instance_is_removed() {
        registry.onWorkflowStarted(startedEvent("inst-1", "my-flow", "default", "1.0.0"));
        assertThat(registry.activeInstances()).hasSize(1);

        WorkflowContextData ctx = contextData("inst-1", "my-flow", "default", "1.0.0",
                WorkflowStatus.COMPLETED, Instant.now());
        registry.onWorkflowCompleted(new WorkflowCompletedEvent(ctx, null));

        assertThat(registry.activeInstances()).isEmpty();
    }

    @Test
    @DisplayName("test_failed_instance_is_removed")
    void test_failed_instance_is_removed() {
        registry.onWorkflowStarted(startedEvent("inst-1", "my-flow", "default", "1.0.0"));

        WorkflowContextData ctx = contextData("inst-1", "my-flow", "default", "1.0.0",
                WorkflowStatus.FAULTED, Instant.now());
        registry.onWorkflowFailed(new WorkflowFailedEvent(ctx, new RuntimeException("boom")));

        assertThat(registry.activeInstances()).isEmpty();
    }

    @Test
    @DisplayName("test_cancelled_instance_is_removed")
    void test_cancelled_instance_is_removed() {
        registry.onWorkflowStarted(startedEvent("inst-1", "my-flow", "default", "1.0.0"));

        WorkflowContextData ctx = contextData("inst-1", "my-flow", "default", "1.0.0",
                WorkflowStatus.CANCELLED, Instant.now());
        registry.onWorkflowCancelled(new WorkflowCancelledEvent(ctx));

        assertThat(registry.activeInstances()).isEmpty();
    }

    @Test
    @DisplayName("test_status_change_updates_snapshot")
    void test_status_change_updates_snapshot() {
        registry.onWorkflowStarted(startedEvent("inst-1", "my-flow", "default", "1.0.0"));

        WorkflowContextData ctx = contextData("inst-1", "my-flow", "default", "1.0.0",
                WorkflowStatus.SUSPENDED, Instant.now());
        registry.onWorkflowStatusChanged(
                new WorkflowStatusEvent(ctx, WorkflowStatus.SUSPENDED, WorkflowStatus.RUNNING));

        InstanceSnapshot snap = registry.activeInstances().iterator().next();
        assertThat(snap.status()).isEqualTo(WorkflowStatus.SUSPENDED);
        // Other fields are unchanged
        assertThat(snap.instanceId()).isEqualTo("inst-1");
        assertThat(snap.workflowName()).isEqualTo("my-flow");
    }

    @Test
    @DisplayName("test_status_change_for_unknown_instance_is_ignored")
    void test_status_change_for_unknown_instance_is_ignored() {
        WorkflowContextData ctx = contextData("unknown-id", "my-flow", "default", "1.0.0",
                WorkflowStatus.SUSPENDED, Instant.now());

        // Should not throw
        registry.onWorkflowStatusChanged(
                new WorkflowStatusEvent(ctx, WorkflowStatus.SUSPENDED, WorkflowStatus.RUNNING));

        assertThat(registry.activeInstances()).isEmpty();
    }

    @Test
    @DisplayName("test_workflow_suspended_updates_status")
    void test_workflow_suspended_updates_status() {
        registry.onWorkflowStarted(startedEvent("inst-1", "my-flow", "default", "1.0.0"));

        WorkflowContextData ctx = contextData("inst-1", "my-flow", "default", "1.0.0",
                WorkflowStatus.SUSPENDED, Instant.now());
        registry.onWorkflowSuspended(new WorkflowSuspendedEvent(ctx));

        InstanceSnapshot snap = registry.activeInstances().iterator().next();
        assertThat(snap.status()).isEqualTo(WorkflowStatus.SUSPENDED);
        assertThat(snap.instanceId()).isEqualTo("inst-1");
    }

    @Test
    @DisplayName("test_workflow_resumed_updates_status")
    void test_workflow_resumed_updates_status() {
        registry.onWorkflowStarted(startedEvent("inst-1", "my-flow", "default", "1.0.0"));

        WorkflowContextData suspendedCtx = contextData("inst-1", "my-flow", "default", "1.0.0",
                WorkflowStatus.SUSPENDED, Instant.now());
        registry.onWorkflowSuspended(new WorkflowSuspendedEvent(suspendedCtx));

        WorkflowContextData runningCtx = contextData("inst-1", "my-flow", "default", "1.0.0",
                WorkflowStatus.RUNNING, Instant.now());
        registry.onWorkflowResumed(new WorkflowResumedEvent(runningCtx));

        InstanceSnapshot snap = registry.activeInstances().iterator().next();
        assertThat(snap.status()).isEqualTo(WorkflowStatus.RUNNING);
        assertThat(snap.instanceId()).isEqualTo("inst-1");
    }

    @Test
    @DisplayName("test_events_with_null_instance_id_are_ignored")
    void test_events_with_null_instance_id_are_ignored() {
        WorkflowContextData ctx = contextData(null, "my-flow", "default", "1.0.0",
                WorkflowStatus.RUNNING, Instant.now());

        // Should not throw
        registry.onWorkflowStarted(new WorkflowStartedEvent(ctx));
        registry.onWorkflowStatusChanged(new WorkflowStatusEvent(ctx, WorkflowStatus.SUSPENDED, WorkflowStatus.RUNNING));
        registry.onWorkflowSuspended(new WorkflowSuspendedEvent(ctx));
        registry.onWorkflowResumed(new WorkflowResumedEvent(ctx));
        registry.onWorkflowCompleted(new WorkflowCompletedEvent(ctx, null));
        registry.onWorkflowFailed(new WorkflowFailedEvent(ctx, new RuntimeException("boom")));
        registry.onWorkflowCancelled(new WorkflowCancelledEvent(ctx));

        assertThat(registry.activeInstances()).isEmpty();
    }

    @Test
    @DisplayName("test_multiple_instances_tracked_independently")
    void test_multiple_instances_tracked_independently() {
        registry.onWorkflowStarted(startedEvent("inst-1", "flow-a", "ns1", "1.0.0"));
        registry.onWorkflowStarted(startedEvent("inst-2", "flow-b", "ns1", "2.0.0"));
        registry.onWorkflowStarted(startedEvent("inst-3", "flow-a", "ns2", "1.0.0"));

        assertThat(registry.activeInstances()).hasSize(3);

        // Complete one
        WorkflowContextData ctx = contextData("inst-2", "flow-b", "ns1", "2.0.0",
                WorkflowStatus.COMPLETED, Instant.now());
        registry.onWorkflowCompleted(new WorkflowCompletedEvent(ctx, null));

        assertThat(registry.activeInstances()).hasSize(2);
        assertThat(registry.activeInstances().stream().map(InstanceSnapshot::instanceId))
                .containsExactlyInAnyOrder("inst-1", "inst-3");
    }
}
