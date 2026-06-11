package io.quarkiverse.flow.runner;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.quarkiverse.flow.internal.WorkflowApplicationReady;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinition;

@DisplayName("WorkflowDefinitionRuntimeLoader Tests")
class WorkflowDefinitionRuntimeLoaderTest {

    @TempDir
    Path tempDir;

    private WorkflowDefinitionRuntimeLoader loader;
    private WorkflowApplication mockApplication;
    private FlowRunnerConfig mockConfig;
    private FlowRunnerConfig.Source mockSource;

    @BeforeEach
    void setUp() {
        loader = new WorkflowDefinitionRuntimeLoader();
        mockApplication = mock(WorkflowApplication.class);
        mockConfig = mock(FlowRunnerConfig.class);
        mockSource = mock(FlowRunnerConfig.Source.class);

        loader.application = mockApplication;
        loader.config = mockConfig;

        when(mockConfig.source()).thenReturn(mockSource);
    }

    @Test
    @DisplayName("test_loader_disabled_skips_loading")
    void test_loader_disabled_skips_loading() {
        // Given
        when(mockConfig.enabled()).thenReturn(false);

        // When
        loader.onStart(new WorkflowApplicationReady("ABC123"));

        // Then
        verify(mockApplication, never()).workflowDefinition(any(Workflow.class));
    }

    @Test
    @DisplayName("test_loader_without_path_skips_loading")
    void test_loader_without_path_skips_loading() {
        // Given
        when(mockConfig.enabled()).thenReturn(true);
        when(mockSource.path()).thenReturn(Optional.empty());

        // When
        loader.onStart(new WorkflowApplicationReady("ABC123"));

        // Then
        verify(mockApplication, never()).workflowDefinition(any(Workflow.class));
    }

    @Test
    @DisplayName("test_loader_with_nonexistent_path_throws_exception")
    void test_loader_with_nonexistent_path_throws_exception() {
        // Given
        when(mockConfig.enabled()).thenReturn(true);
        when(mockSource.path()).thenReturn(Optional.of("/nonexistent/path"));

        // When/Then
        assertThatThrownBy(() -> loader.onStart(new WorkflowApplicationReady("ABC123")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Workflow directory does not exist");
    }

    @Test
    @DisplayName("test_loader_with_file_instead_of_directory_throws_exception")
    void test_loader_with_file_instead_of_directory_throws_exception() throws IOException {
        // Given
        Path file = tempDir.resolve("not-a-directory.txt");
        Files.writeString(file, "test");

        when(mockConfig.enabled()).thenReturn(true);
        when(mockSource.path()).thenReturn(Optional.of(file.toString()));

        // When/Then
        assertThatThrownBy(() -> loader.onStart(new WorkflowApplicationReady("ABC123")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Workflow path is not a directory");
    }

    @Test
    @DisplayName("test_loader_with_empty_directory_loads_no_workflows")
    void test_loader_with_empty_directory_loads_no_workflows() {
        // Given
        when(mockConfig.enabled()).thenReturn(true);
        when(mockSource.path()).thenReturn(Optional.of(tempDir.toString()));

        // When
        loader.onStart(new WorkflowApplicationReady("ABC123"));

        // Then
        verify(mockApplication, never()).workflowDefinition(any(Workflow.class));
    }

    @Test
    @DisplayName("test_loader_loads_valid_yaml_workflow")
    void test_loader_loads_valid_yaml_workflow() throws IOException {
        // Given
        String workflowYaml = """
                document:
                  dsl: '1.0.0'
                  namespace: test-namespace
                  name: test-workflow
                  version: '1.0.0'
                do:
                  - setMessage:
                      set:
                        message: "Hello World"
                """;

        Path workflowFile = tempDir.resolve("test-workflow.yaml");
        Files.writeString(workflowFile, workflowYaml);

        WorkflowDefinition mockDefinition = mock(WorkflowDefinition.class);
        when(mockApplication.workflowDefinition(any(Workflow.class))).thenReturn(mockDefinition);

        when(mockConfig.enabled()).thenReturn(true);
        when(mockSource.path()).thenReturn(Optional.of(tempDir.toString()));

        // When
        loader.onStart(new WorkflowApplicationReady("ABC123"));

        // Then
        verify(mockApplication, times(1)).workflowDefinition(any(Workflow.class));
    }

    @Test
    @DisplayName("test_loader_loads_valid_yml_workflow")
    void test_loader_loads_valid_yml_workflow() throws IOException {
        // Given
        String workflowYml = """
                document:
                  dsl: '1.0.0'
                  namespace: test-namespace
                  name: test-workflow-yml
                  version: '1.0.0'
                do:
                  - setMessage:
                      set:
                        message: "Hello from YML"
                """;

        Path workflowFile = tempDir.resolve("test-workflow.yml");
        Files.writeString(workflowFile, workflowYml);

        WorkflowDefinition mockDefinition = mock(WorkflowDefinition.class);
        when(mockApplication.workflowDefinition(any(Workflow.class))).thenReturn(mockDefinition);

        when(mockConfig.enabled()).thenReturn(true);
        when(mockSource.path()).thenReturn(Optional.of(tempDir.toString()));

        // When
        loader.onStart(new WorkflowApplicationReady("ABC123"));

        // Then
        verify(mockApplication, times(1)).workflowDefinition(any(Workflow.class));
    }

    @Test
    @DisplayName("test_loader_loads_valid_json_workflow")
    void test_loader_loads_valid_json_workflow() throws IOException {
        // Given
        String workflowJson = """
                {
                  "document": {
                    "dsl": "1.0.0",
                    "namespace": "test-namespace",
                    "name": "test-workflow-json",
                    "version": "1.0.0"
                  },
                  "do": [
                    {
                      "setMessage": {
                        "set": {
                          "message": "Hello from JSON"
                        }
                      }
                    }
                  ]
                }
                """;

        Path workflowFile = tempDir.resolve("test-workflow.json");
        Files.writeString(workflowFile, workflowJson);

        WorkflowDefinition mockDefinition = mock(WorkflowDefinition.class);
        when(mockApplication.workflowDefinition(any(Workflow.class))).thenReturn(mockDefinition);

        when(mockConfig.enabled()).thenReturn(true);
        when(mockSource.path()).thenReturn(Optional.of(tempDir.toString()));

        // When
        loader.onStart(new WorkflowApplicationReady("ABC123"));

        // Then
        verify(mockApplication, times(1)).workflowDefinition(any(Workflow.class));
    }

    @Test
    @DisplayName("test_loader_loads_multiple_workflows_from_directory")
    void test_loader_loads_multiple_workflows_from_directory() throws IOException {
        // Given
        String workflow1 = """
                document:
                  dsl: '1.0.0'
                  namespace: test-namespace
                  name: workflow-1
                  version: '1.0.0'
                do:
                  - setMessage:
                      set:
                        message: "Workflow 1"
                """;

        String workflow2 = """
                document:
                  dsl: '1.0.0'
                  namespace: test-namespace
                  name: workflow-2
                  version: '1.0.0'
                do:
                  - setMessage:
                      set:
                        message: "Workflow 2"
                """;

        Files.writeString(tempDir.resolve("workflow-1.yaml"), workflow1);
        Files.writeString(tempDir.resolve("workflow-2.yaml"), workflow2);

        WorkflowDefinition mockDefinition = mock(WorkflowDefinition.class);
        when(mockApplication.workflowDefinition(any(Workflow.class))).thenReturn(mockDefinition);

        when(mockConfig.enabled()).thenReturn(true);
        when(mockSource.path()).thenReturn(Optional.of(tempDir.toString()));

        // When
        loader.onStart(new WorkflowApplicationReady("ABC123"));

        // Then
        verify(mockApplication, times(2)).workflowDefinition(any(Workflow.class));
    }

    @Test
    @DisplayName("test_loader_loads_workflows_recursively")
    void test_loader_loads_workflows_recursively() throws IOException {
        // Given
        Path subDir = tempDir.resolve("subdirectory");
        Files.createDirectories(subDir);

        String workflow1 = """
                document:
                  dsl: '1.0.0'
                  namespace: test-namespace
                  name: workflow-root
                  version: '1.0.0'
                do:
                  - setMessage:
                      set:
                        message: "Root workflow"
                """;

        String workflow2 = """
                document:
                  dsl: '1.0.0'
                  namespace: test-namespace
                  name: workflow-sub
                  version: '1.0.0'
                do:
                  - setMessage:
                      set:
                        message: "Sub workflow"
                """;

        Files.writeString(tempDir.resolve("root-workflow.yaml"), workflow1);
        Files.writeString(subDir.resolve("sub-workflow.yaml"), workflow2);

        WorkflowDefinition mockDefinition = mock(WorkflowDefinition.class);
        when(mockApplication.workflowDefinition(any(Workflow.class))).thenReturn(mockDefinition);

        when(mockConfig.enabled()).thenReturn(true);
        when(mockSource.path()).thenReturn(Optional.of(tempDir.toString()));

        // When
        loader.onStart(new WorkflowApplicationReady("ABC123"));

        // Then
        verify(mockApplication, times(2)).workflowDefinition(any(Workflow.class));
    }

    @Test
    @DisplayName("test_loader_ignores_unsupported_file_types")
    void test_loader_ignores_unsupported_file_types() throws IOException {
        // Given
        Files.writeString(tempDir.resolve("readme.txt"), "Not a workflow");
        Files.writeString(tempDir.resolve("config.xml"), "<config/>");

        String validWorkflow = """
                document:
                  dsl: '1.0.0'
                  namespace: test-namespace
                  name: valid-workflow
                  version: '1.0.0'
                do:
                  - setMessage:
                      set:
                        message: "Valid"
                """;
        Files.writeString(tempDir.resolve("valid.yaml"), validWorkflow);

        WorkflowDefinition mockDefinition = mock(WorkflowDefinition.class);
        when(mockApplication.workflowDefinition(any(Workflow.class))).thenReturn(mockDefinition);

        when(mockConfig.enabled()).thenReturn(true);
        when(mockSource.path()).thenReturn(Optional.of(tempDir.toString()));

        // When
        loader.onStart(new WorkflowApplicationReady("ABC123"));

        // Then - Only the valid workflow should be loaded
        verify(mockApplication, times(1)).workflowDefinition(any(Workflow.class));
    }

    @Test
    @DisplayName("test_loader_throws_exception_for_invalid_workflow_format")
    void test_loader_throws_exception_for_invalid_workflow_format() throws IOException {
        // Given
        String invalidYaml = "this is not a valid workflow yaml";
        Files.writeString(tempDir.resolve("invalid.yaml"), invalidYaml);

        when(mockConfig.enabled()).thenReturn(true);
        when(mockSource.path()).thenReturn(Optional.of(tempDir.toString()));

        // When/Then
        assertThatThrownBy(() -> loader.onStart(new WorkflowApplicationReady("ABC123")))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("Failed to load workflow");
    }

    @Test
    @DisplayName("test_loader_throws_exception_for_workflow_missing_required_fields")
    void test_loader_throws_exception_for_workflow_missing_required_fields() throws IOException {
        // Given - workflow missing version (SDK parser will throw IOException)
        String invalidWorkflow = """
                document:
                  dsl: '1.0.0'
                  namespace: test-namespace
                  name: incomplete-workflow
                do:
                  - setMessage:
                      set:
                        message: "Missing version"
                """;
        Files.writeString(tempDir.resolve("incomplete.yaml"), invalidWorkflow);

        when(mockConfig.enabled()).thenReturn(true);
        when(mockSource.path()).thenReturn(Optional.of(tempDir.toString()));

        // When/Then - SDK parser throws IOException for missing required fields
        assertThatThrownBy(() -> loader.onStart(new WorkflowApplicationReady("ABC123")))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("Failed to load workflow");
    }

    @Test
    @DisplayName("test_loader_rejects_duplicate_workflow_ids")
    void test_loader_rejects_duplicate_workflow_ids() throws IOException {
        String workflow1 = """
                document:
                  dsl: '1.0.0'
                  namespace: test-namespace
                  name: duplicate-workflow
                  version: '1.0.0'
                do:
                  - setMessage:
                      set:
                        message: "First"
                """;
        String workflow2 = """
                document:
                  dsl: '1.0.0'
                  namespace: test-namespace
                  name: duplicate-workflow
                  version: '1.0.0'
                do:
                  - setMessage:
                      set:
                        message: "Second"
                """;
        Files.writeString(tempDir.resolve("workflow-1.yaml"), workflow1);
        Files.writeString(tempDir.resolve("workflow-2.yaml"), workflow2);
        when(mockConfig.enabled()).thenReturn(true);
        when(mockSource.path()).thenReturn(Optional.of(tempDir.toString()));
        assertThatThrownBy(() -> loader.onStart(new WorkflowApplicationReady("ABC123")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicated workflow definition")
                .hasMessageContaining("test-namespace")
                .hasMessageContaining("duplicate-workflow")
                .hasMessageContaining("1.0.0")
                .hasMessageContaining("workflow-2.yaml");
    }
}
