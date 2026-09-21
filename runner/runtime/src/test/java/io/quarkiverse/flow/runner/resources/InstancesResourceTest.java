package io.quarkiverse.flow.runner.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.runner.model.ActiveInstancesResponse;
import io.quarkiverse.flow.runner.model.InstanceSnapshot;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowDefinitionId;
import io.serverlessworkflow.impl.WorkflowInstance;
import io.serverlessworkflow.impl.WorkflowModel;
import io.serverlessworkflow.impl.WorkflowStatus;

@DisplayName("InstancesResource Tests")
class InstancesResourceTest {

    private InstancesResource resource;
    private WorkflowApplication mockApplication;

    @BeforeEach
    void setUp() {
        resource = new InstancesResource();
        mockApplication = mock(WorkflowApplication.class);
        resource.application = mockApplication;

        WorkflowDefinitionLookup lookup = new WorkflowDefinitionLookup();
        lookup.application = mockApplication;
        resource.definitionLookup = lookup;

        when(mockApplication.id()).thenReturn("runner-pod-0");
        when(mockApplication.workflowDefinitions()).thenReturn(Map.of());
    }

    // --- helpers ---

    private record Seed(String id, String name, String namespace, String version, WorkflowStatus status) {
    }

    private Seed seed(String id, String name, String namespace, String version, WorkflowStatus status) {
        return new Seed(id, name, namespace, version, status);
    }

    private WorkflowInstance instance(String id, WorkflowStatus status) {
        WorkflowInstance instance = mock(WorkflowInstance.class);
        when(instance.id()).thenReturn(id);
        when(instance.status()).thenReturn(status);
        when(instance.startedAt()).thenReturn(Instant.now());
        WorkflowModel input = mock(WorkflowModel.class);
        when(input.asJavaObject()).thenReturn(Map.of("key", "value"));
        when(instance.input()).thenReturn(input);
        return instance;
    }

    private void seedDefinitions(Seed... seeds) {
        Map<WorkflowDefinitionId, List<WorkflowInstance>> grouped = new LinkedHashMap<>();
        for (Seed s : seeds) {
            WorkflowDefinitionId id = new WorkflowDefinitionId(s.namespace(), s.name(), s.version());
            grouped.computeIfAbsent(id, k -> new ArrayList<>()).add(instance(s.id(), s.status()));
        }

        Map<WorkflowDefinitionId, WorkflowDefinition> definitions = new LinkedHashMap<>();
        grouped.forEach((id, instances) -> {
            WorkflowDefinition definition = mock(WorkflowDefinition.class);
            when(definition.id()).thenReturn(id);
            when(definition.activeInstances()).thenReturn(instances);
            definitions.put(id, definition);
        });

        when(mockApplication.workflowDefinitions()).thenReturn(definitions);
    }

    @SuppressWarnings("unchecked")
    private ActiveInstancesResponse body(Response response) {
        return (ActiveInstancesResponse) response.getEntity();
    }

    // --- tests: GET /q/flow/instances ---

    @Test
    @DisplayName("test_returns_empty_instances_and_application_id_when_registry_empty")
    void test_returns_empty_instances_and_application_id_when_registry_empty() {
        Response response = resource.listActiveInstances(null, false);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(body(response).applicationId()).isEqualTo("runner-pod-0");
        assertThat(body(response).instances()).isEmpty();
    }

    @Test
    @DisplayName("test_returns_all_instances_across_definitions_when_no_status_filter")
    void test_returns_all_instances_across_definitions_when_no_status_filter() {
        seedDefinitions(
                seed("i1", "flow-a", "default", "1.0.0", WorkflowStatus.RUNNING),
                seed("i2", "flow-b", "default", "1.0.0", WorkflowStatus.WAITING),
                seed("i3", "flow-a", "default", "2.0.0", WorkflowStatus.SUSPENDED));

        Response response = resource.listActiveInstances(null, false);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(body(response).instances()).hasSize(3);
        assertThat(body(response).applicationId()).isEqualTo("runner-pod-0");
    }

    @Test
    @DisplayName("test_filters_by_status")
    void test_filters_by_status() {
        seedDefinitions(
                seed("i1", "flow-a", "default", "1.0.0", WorkflowStatus.RUNNING),
                seed("i2", "flow-b", "default", "1.0.0", WorkflowStatus.SUSPENDED),
                seed("i3", "flow-a", "default", "2.0.0", WorkflowStatus.RUNNING));

        Response response = resource.listActiveInstances(WorkflowStatus.RUNNING, false);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(body(response).instances()).hasSize(2);
        assertThat(body(response).instances()).extracting(InstanceSnapshot::status)
                .containsOnly(WorkflowStatus.RUNNING);
    }

    @Test
    @DisplayName("test_terminal_status_completed_throws_invalid_status_filter_exception")
    void test_terminal_status_completed_throws_invalid_status_filter_exception() {
        assertThatThrownBy(() -> resource.listActiveInstances(WorkflowStatus.COMPLETED, false))
                .isInstanceOf(InvalidStatusFilterException.class)
                .hasMessageContaining("COMPLETED");
    }

    @Test
    @DisplayName("test_terminal_status_faulted_throws_invalid_status_filter_exception")
    void test_terminal_status_faulted_throws_invalid_status_filter_exception() {
        assertThatThrownBy(() -> resource.listActiveInstances(WorkflowStatus.FAULTED, false))
                .isInstanceOf(InvalidStatusFilterException.class)
                .hasMessageContaining("FAULTED");
    }

    @Test
    @DisplayName("test_terminal_status_cancelled_throws_invalid_status_filter_exception")
    void test_terminal_status_cancelled_throws_invalid_status_filter_exception() {
        assertThatThrownBy(() -> resource.listActiveInstances(WorkflowStatus.CANCELLED, false))
                .isInstanceOf(InvalidStatusFilterException.class)
                .hasMessageContaining("CANCELLED");
    }

    @Test
    @DisplayName("test_includeInput_false_omits_input")
    void test_includeInput_false_omits_input() {
        seedDefinitions(seed("i1", "flow-a", "default", "1.0.0", WorkflowStatus.RUNNING));

        Response response = resource.listActiveInstances(null, false);

        assertThat(body(response).instances().get(0).input()).isNull();
    }

    @Test
    @DisplayName("test_includeInput_true_populates_input")
    void test_includeInput_true_populates_input() {
        seedDefinitions(seed("i1", "flow-a", "default", "1.0.0", WorkflowStatus.RUNNING));

        Response response = resource.listActiveInstances(null, true);

        assertThat(body(response).instances().get(0).input()).isEqualTo(Map.of("key", "value"));
    }

    // --- tests: GET /q/flow/{namespace}/{name}/instances (latest version) ---

    @Test
    @DisplayName("test_latest_version_endpoint_resolves_highest_version")
    void test_latest_version_endpoint_resolves_highest_version() {
        seedDefinitions(
                seed("i1", "flow-a", "default", "1.0.0", WorkflowStatus.RUNNING),
                seed("i2", "flow-a", "default", "2.0.0", WorkflowStatus.RUNNING),
                seed("i3", "flow-b", "default", "1.0.0", WorkflowStatus.RUNNING));

        Response response = resource.listActiveInstancesForWorkflow("default", "flow-a", null, false);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(body(response).instances()).hasSize(1);
        assertThat(body(response).instances().get(0).instanceId()).isEqualTo("i2");
    }

    @Test
    @DisplayName("test_latest_version_endpoint_no_match_returns_empty_instances_with_application_id")
    void test_latest_version_endpoint_no_match_returns_empty_instances_with_application_id() {
        seedDefinitions(seed("i1", "flow-a", "default", "1.0.0", WorkflowStatus.RUNNING));

        Response response = resource.listActiveInstancesForWorkflow("default", "unknown-flow", null, false);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(body(response).instances()).isEmpty();
        assertThat(body(response).applicationId()).isEqualTo("runner-pod-0");
    }

    // --- tests: GET /q/flow/{namespace}/{name}/{version}/instances ---

    @Test
    @DisplayName("test_scoped_endpoint_filters_by_namespace_name_and_version")
    void test_scoped_endpoint_filters_by_namespace_name_and_version() {
        seedDefinitions(
                seed("i1", "flow-a", "default", "1.0.0", WorkflowStatus.RUNNING),
                seed("i2", "flow-a", "default", "2.0.0", WorkflowStatus.RUNNING),
                seed("i3", "flow-a", "other-ns", "1.0.0", WorkflowStatus.RUNNING),
                seed("i4", "flow-b", "default", "1.0.0", WorkflowStatus.RUNNING));

        Response response = resource.listActiveInstancesForWorkflowVersion("default", "flow-a", "1.0.0", null, false);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(body(response).instances()).hasSize(1);
        assertThat(body(response).instances().get(0).instanceId()).isEqualTo("i1");
    }

    @Test
    @DisplayName("test_scoped_endpoint_filters_by_status")
    void test_scoped_endpoint_filters_by_status() {
        seedDefinitions(
                seed("i1", "flow-a", "default", "1.0.0", WorkflowStatus.RUNNING),
                seed("i2", "flow-a", "default", "1.0.0", WorkflowStatus.SUSPENDED));

        Response response = resource.listActiveInstancesForWorkflowVersion("default", "flow-a", "1.0.0",
                WorkflowStatus.SUSPENDED, false);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(body(response).instances()).hasSize(1);
        assertThat(body(response).instances().get(0).instanceId()).isEqualTo("i2");
    }

    @Test
    @DisplayName("test_scoped_endpoint_no_match_returns_empty_instances_with_application_id")
    void test_scoped_endpoint_no_match_returns_empty_instances_with_application_id() {
        seedDefinitions(seed("i1", "flow-a", "default", "1.0.0", WorkflowStatus.RUNNING));

        Response response = resource.listActiveInstancesForWorkflowVersion("default", "flow-a", "9.9.9", null, false);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(body(response).instances()).isEmpty();
        assertThat(body(response).applicationId()).isEqualTo("runner-pod-0");
    }

    @Test
    @DisplayName("test_scoped_endpoint_terminal_status_throws_invalid_status_filter_exception")
    void test_scoped_endpoint_terminal_status_throws_invalid_status_filter_exception() {
        seedDefinitions(seed("i1", "flow-a", "default", "1.0.0", WorkflowStatus.RUNNING));

        assertThatThrownBy(
                () -> resource.listActiveInstancesForWorkflowVersion("default", "flow-a", "1.0.0",
                        WorkflowStatus.CANCELLED, false))
                .isInstanceOf(InvalidStatusFilterException.class)
                .hasMessageContaining("CANCELLED");
    }

    @Test
    @DisplayName("test_scoped_endpoint_includeInput_true_populates_input")
    void test_scoped_endpoint_includeInput_true_populates_input() {
        seedDefinitions(seed("i1", "flow-a", "default", "1.0.0", WorkflowStatus.RUNNING));

        Response response = resource.listActiveInstancesForWorkflowVersion("default", "flow-a", "1.0.0", null, true);

        assertThat(body(response).instances().get(0).input()).isEqualTo(Map.of("key", "value"));
    }
}
