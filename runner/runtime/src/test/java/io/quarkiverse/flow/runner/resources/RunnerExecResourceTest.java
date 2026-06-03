package io.quarkiverse.flow.runner.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.runner.model.ExecutionResponse;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowDefinitionId;
import io.serverlessworkflow.impl.WorkflowInstance;
import io.serverlessworkflow.impl.WorkflowModel;
import io.serverlessworkflow.impl.WorkflowStatus;

@DisplayName("RunnerExecResource Tests")
class RunnerExecResourceTest {

    private RunnerExecResource resource;
    private WorkflowApplication mockApplication;

    @BeforeEach
    void setUp() {
        resource = new RunnerExecResource();
        mockApplication = mock(WorkflowApplication.class);
        resource.application = mockApplication;
    }

    @Test
    @DisplayName("test_execute_workflow_latest_version_returns_404_when_not_found")
    void test_execute_workflow_latest_version_returns_404_when_not_found() {
        // Given
        when(mockApplication.workflowDefinitions()).thenReturn(Map.of());

        // When
        Response response = resource.executeWorkflow("test-ns", "test-wf", false, Map.of()).await().indefinitely();

        // Then
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getEntity()).asString().contains("not found");
    }

    @Test
    @DisplayName("test_execute_workflow_specific_version_returns_404_when_not_found")
    void test_execute_workflow_specific_version_returns_404_when_not_found() {
        // Given
        when(mockApplication.workflowDefinitions()).thenReturn(Map.of());

        // When
        Response response = resource.executeWorkflow("test-ns", "test-wf", "1.0.0", false, Map.of()).await()
                .indefinitely();

        // Then
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getEntity()).asString().contains("not found");
    }

    @Test
    @DisplayName("test_execute_workflow_async_returns_202_with_instance_id")
    void test_execute_workflow_async_returns_202_with_instance_id() {
        // Given
        WorkflowDefinition mockDefinition = mock(WorkflowDefinition.class);
        WorkflowInstance mockInstance = mock(WorkflowInstance.class);
        WorkflowModel mockModel = mock(WorkflowModel.class);

        when(mockInstance.id()).thenReturn("instance-123");
        when(mockInstance.status()).thenReturn(WorkflowStatus.RUNNING);
        when(mockInstance.startedAt()).thenReturn(Instant.now());
        when(mockInstance.completedAt()).thenReturn(null);
        when(mockInstance.start()).thenReturn(CompletableFuture.completedFuture(mockModel));
        when(mockDefinition.instance(any())).thenReturn(mockInstance);

        WorkflowDefinitionId id = new WorkflowDefinitionId("test-ns", "test-wf", "1.0.0");
        when(mockApplication.workflowDefinitions()).thenReturn(Map.of(id, mockDefinition));

        // When
        Response response = resource.executeWorkflow("test-ns", "test-wf", "1.0.0", false, Map.of("input", "data"))
                .await().indefinitely();

        // Then
        assertThat(response.getStatus()).isEqualTo(202);
        assertThat(response.getEntity()).isInstanceOf(ExecutionResponse.class);

        ExecutionResponse executionResponse = (ExecutionResponse) response.getEntity();
        assertThat(executionResponse.instanceId()).isEqualTo("instance-123");
        assertThat(executionResponse.status()).isEqualTo(WorkflowStatus.RUNNING);
        assertThat(executionResponse.startedAt()).isNotNull();
    }

    @Test
    @DisplayName("test_execute_workflow_sync_returns_200_with_output")
    void test_execute_workflow_sync_returns_200_with_output() {
        // Given
        WorkflowDefinition mockDefinition = mock(WorkflowDefinition.class);
        WorkflowInstance mockInstance = mock(WorkflowInstance.class);
        WorkflowModel mockModel = mock(WorkflowModel.class);

        when(mockInstance.id()).thenReturn("instance-456");
        when(mockInstance.status()).thenReturn(WorkflowStatus.COMPLETED);
        when(mockInstance.startedAt()).thenReturn(Instant.now());
        when(mockInstance.completedAt()).thenReturn(Instant.now());
        when(mockInstance.start()).thenReturn(CompletableFuture.completedFuture(mockModel));
        when(mockDefinition.instance(any())).thenReturn(mockInstance);

        when(mockModel.asMap()).thenReturn(java.util.Optional.of(Map.of("result", "success")));

        WorkflowDefinitionId id = new WorkflowDefinitionId("test-ns", "test-wf", "1.0.0");
        when(mockApplication.workflowDefinitions()).thenReturn(Map.of(id, mockDefinition));

        // When
        Response response = resource.executeWorkflow("test-ns", "test-wf", "1.0.0", true, Map.of("input", "data"))
                .await().indefinitely();

        // Then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getEntity()).isInstanceOf(ExecutionResponse.class);

        ExecutionResponse executionResponse = (ExecutionResponse) response.getEntity();
        assertThat(executionResponse.instanceId()).isEqualTo("instance-456");
        assertThat(executionResponse.status()).isEqualTo(WorkflowStatus.COMPLETED);
        assertThat(executionResponse.workflowOutput()).isNotNull();
        assertThat(executionResponse.workflowOutput()).containsEntry("result", "success");
    }

    @Test
    @DisplayName("test_execute_workflow_with_null_input")
    void test_execute_workflow_with_null_input() {
        // Given
        WorkflowDefinition mockDefinition = mock(WorkflowDefinition.class);
        WorkflowInstance mockInstance = mock(WorkflowInstance.class);
        WorkflowModel mockModel = mock(WorkflowModel.class);

        when(mockInstance.id()).thenReturn("instance-789");
        when(mockInstance.status()).thenReturn(WorkflowStatus.RUNNING);
        when(mockInstance.startedAt()).thenReturn(Instant.now());
        when(mockInstance.start()).thenReturn(CompletableFuture.completedFuture(mockModel));
        when(mockDefinition.instance(any())).thenReturn(mockInstance);

        WorkflowDefinitionId id = new WorkflowDefinitionId("test-ns", "test-wf", "1.0.0");
        when(mockApplication.workflowDefinitions()).thenReturn(Map.of(id, mockDefinition));

        // When - null input should be accepted
        Response response = resource.executeWorkflow("test-ns", "test-wf", "1.0.0", false, null).await().indefinitely();

        // Then
        assertThat(response.getStatus()).isEqualTo(202);
    }

    @Test
    @DisplayName("test_execute_workflow_latest_version_selects_highest_version")
    void test_execute_workflow_latest_version_selects_highest_version() {
        // Given
        WorkflowDefinition mockDef1 = mock(WorkflowDefinition.class);
        WorkflowDefinition mockDef2 = mock(WorkflowDefinition.class);
        WorkflowDefinition mockDef3 = mock(WorkflowDefinition.class);

        WorkflowInstance mockInstance = mock(WorkflowInstance.class);
        WorkflowModel mockModel = mock(WorkflowModel.class);

        when(mockInstance.id()).thenReturn("instance-latest");
        when(mockInstance.status()).thenReturn(WorkflowStatus.RUNNING);
        when(mockInstance.startedAt()).thenReturn(Instant.now());
        when(mockInstance.start()).thenReturn(CompletableFuture.completedFuture(mockModel));

        // Only the latest version (2.0.0) should be used
        when(mockDef3.instance(any())).thenReturn(mockInstance);

        WorkflowDefinitionId id1 = new WorkflowDefinitionId("test-ns", "test-wf", "1.0.0");
        WorkflowDefinitionId id2 = new WorkflowDefinitionId("test-ns", "test-wf", "1.5.0");
        WorkflowDefinitionId id3 = new WorkflowDefinitionId("test-ns", "test-wf", "2.0.0");

        when(mockApplication.workflowDefinitions()).thenReturn(Map.of(
                id1, mockDef1,
                id2, mockDef2,
                id3, mockDef3));

        // When - request latest version (no version in path)
        Response response = resource.executeWorkflow("test-ns", "test-wf", false, Map.of()).await().indefinitely();

        // Then
        assertThat(response.getStatus()).isEqualTo(202);
        ExecutionResponse executionResponse = (ExecutionResponse) response.getEntity();
        assertThat(executionResponse.instanceId()).isEqualTo("instance-latest");
    }

    @Test
    @DisplayName("test_execute_workflow_filters_by_namespace_and_name")
    void test_execute_workflow_filters_by_namespace_and_name() {
        // Given
        WorkflowDefinitionId id1 = new WorkflowDefinitionId("test-ns", "other-wf", "1.0.0");
        WorkflowDefinitionId id2 = new WorkflowDefinitionId("other-ns", "test-wf", "1.0.0");

        when(mockApplication.workflowDefinitions()).thenReturn(Map.of(
                id1, mock(WorkflowDefinition.class),
                id2, mock(WorkflowDefinition.class)));

        // When - request workflow that doesn't match namespace+name combo
        Response response = resource.executeWorkflow("test-ns", "test-wf", false, Map.of()).await().indefinitely();

        // Then - should return 404 (no matching workflow)
        assertThat(response.getStatus()).isEqualTo(404);
    }
}
