package io.quarkiverse.flow.runner.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.runner.instances.ActiveInstanceRegistry;
import io.quarkiverse.flow.runner.model.ActiveInstancesResponse;
import io.quarkiverse.flow.runner.model.InstanceSnapshot;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowStatus;

@DisplayName("InstancesResource Tests")
class InstancesResourceTest {

    private InstancesResource resource;
    private WorkflowApplication mockApplication;

    @BeforeEach
    void setUp() {
        resource = new InstancesResource();
        mockApplication = mock(WorkflowApplication.class);

        resource.registry = new ActiveInstanceRegistry();
        resource.application = mockApplication;

        when(mockApplication.id()).thenReturn("runner-pod-0");
    }

    // --- helpers ---

    private InstanceSnapshot snap(String id, String name, String namespace, String version,
            WorkflowStatus status) {
        return new InstanceSnapshot(id, name, namespace, version, status, Instant.now());
    }

    private void seedRegistry(InstanceSnapshot... snapshots) {
        resource.registry = new StubRegistry(List.of(snapshots));
    }

    @SuppressWarnings("unchecked")
    private ActiveInstancesResponse body(Response response) {
        return (ActiveInstancesResponse) response.getEntity();
    }

    // --- tests ---

    @Test
    @DisplayName("test_returns_empty_instances_and_application_id_when_registry_empty")
    void test_returns_empty_instances_and_application_id_when_registry_empty() {
        Response response = resource.listActiveInstances(null, null);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(body(response).applicationId()).isEqualTo("runner-pod-0");
        assertThat(body(response).instances()).isEmpty();
    }

    @Test
    @DisplayName("test_returns_all_instances_when_no_filters")
    void test_returns_all_instances_when_no_filters() {
        seedRegistry(
                snap("i1", "flow-a", "default", "1.0.0", WorkflowStatus.RUNNING),
                snap("i2", "flow-b", "default", "1.0.0", WorkflowStatus.WAITING),
                snap("i3", "flow-a", "default", "2.0.0", WorkflowStatus.SUSPENDED));

        Response response = resource.listActiveInstances(null, null);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(body(response).instances()).hasSize(3);
        assertThat(body(response).applicationId()).isEqualTo("runner-pod-0");
    }

    @Test
    @DisplayName("test_filters_by_workflow_name")
    void test_filters_by_workflow_name() {
        seedRegistry(
                snap("i1", "flow-a", "default", "1.0.0", WorkflowStatus.RUNNING),
                snap("i2", "flow-b", "default", "1.0.0", WorkflowStatus.RUNNING),
                snap("i3", "flow-a", "default", "2.0.0", WorkflowStatus.SUSPENDED));

        Response response = resource.listActiveInstances("flow-a", null);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(body(response).instances()).hasSize(2);
        assertThat(body(response).instances()).extracting(InstanceSnapshot::workflowName)
                .containsOnly("flow-a");
    }

    @Test
    @DisplayName("test_filters_by_status")
    void test_filters_by_status() {
        seedRegistry(
                snap("i1", "flow-a", "default", "1.0.0", WorkflowStatus.RUNNING),
                snap("i2", "flow-b", "default", "1.0.0", WorkflowStatus.SUSPENDED),
                snap("i3", "flow-a", "default", "2.0.0", WorkflowStatus.RUNNING));

        Response response = resource.listActiveInstances(null, "RUNNING");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(body(response).instances()).hasSize(2);
        assertThat(body(response).instances()).extracting(InstanceSnapshot::status)
                .containsOnly(WorkflowStatus.RUNNING);
    }

    @Test
    @DisplayName("test_filters_by_both_workflow_name_and_status")
    void test_filters_by_both_workflow_name_and_status() {
        seedRegistry(
                snap("i1", "flow-a", "default", "1.0.0", WorkflowStatus.RUNNING),
                snap("i2", "flow-a", "default", "1.0.0", WorkflowStatus.SUSPENDED),
                snap("i3", "flow-b", "default", "1.0.0", WorkflowStatus.RUNNING));

        Response response = resource.listActiveInstances("flow-a", "RUNNING");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(body(response).instances()).hasSize(1);
        assertThat(body(response).instances().get(0).instanceId()).isEqualTo("i1");
    }

    @Test
    @DisplayName("test_unknown_status_returns_400")
    void test_unknown_status_returns_400() {
        seedRegistry(snap("i1", "flow-a", "default", "1.0.0", WorkflowStatus.RUNNING));

        Response response = resource.listActiveInstances(null, "NOT_A_STATUS");

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getEntity()).asString().contains("NOT_A_STATUS");
    }

    @Test
    @DisplayName("test_terminal_status_completed_returns_400")
    void test_terminal_status_completed_returns_400() {
        Response response = resource.listActiveInstances(null, "COMPLETED");

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getEntity()).asString().contains("COMPLETED");
    }

    @Test
    @DisplayName("test_terminal_status_faulted_returns_400")
    void test_terminal_status_faulted_returns_400() {
        Response response = resource.listActiveInstances(null, "FAULTED");

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getEntity()).asString().contains("FAULTED");
    }

    @Test
    @DisplayName("test_terminal_status_cancelled_returns_400")
    void test_terminal_status_cancelled_returns_400() {
        Response response = resource.listActiveInstances(null, "CANCELLED");

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getEntity()).asString().contains("CANCELLED");
    }

    @Test
    @DisplayName("test_status_filter_is_case_insensitive")
    void test_status_filter_is_case_insensitive() {
        seedRegistry(
                snap("i1", "flow-a", "default", "1.0.0", WorkflowStatus.SUSPENDED),
                snap("i2", "flow-b", "default", "1.0.0", WorkflowStatus.RUNNING));

        Response response = resource.listActiveInstances(null, "suspended");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(body(response).instances()).hasSize(1);
        assertThat(body(response).instances().get(0).instanceId()).isEqualTo("i1");
    }

    @Test
    @DisplayName("test_blank_status_filter_returns_all")
    void test_blank_status_filter_returns_all() {
        seedRegistry(
                snap("i1", "flow-a", "default", "1.0.0", WorkflowStatus.RUNNING),
                snap("i2", "flow-b", "default", "1.0.0", WorkflowStatus.SUSPENDED));

        Response response = resource.listActiveInstances(null, "  ");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(body(response).instances()).hasSize(2);
    }

    @Test
    @DisplayName("test_no_match_returns_empty_instances_with_application_id")
    void test_no_match_returns_empty_instances_with_application_id() {
        seedRegistry(snap("i1", "flow-a", "default", "1.0.0", WorkflowStatus.RUNNING));

        Response response = resource.listActiveInstances("non-existent-flow", null);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(body(response).instances()).isEmpty();
        assertThat(body(response).applicationId()).isEqualTo("runner-pod-0");
    }

    /**
     * Stub registry that returns a predefined list of snapshots.
     */
    private static class StubRegistry extends ActiveInstanceRegistry {
        private final List<InstanceSnapshot> snapshots;

        StubRegistry(List<InstanceSnapshot> snapshots) {
            this.snapshots = snapshots;
        }

        @Override
        public Collection<InstanceSnapshot> activeInstances() {
            return snapshots;
        }
    }
}
