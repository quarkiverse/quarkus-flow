package io.quarkiverse.flow.runner;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.quarkiverse.flow.internal.WorkflowApplicationReadyEvent;
import io.quarkiverse.flow.internal.WorkflowRegistrarService;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowDefinition;

@DisplayName("WorkflowDefinitionRuntimeLoader Tests")
class WorkflowDefinitionRuntimeLoaderTest {

    @TempDir
    Path tempDir;

    private WorkflowDefinitionRuntimeLoader loader;
    private WorkflowRegistrarService mockRegistrar;
    private FlowRunnerConfig mockConfig;
    private FlowRunnerConfig.Source mockSource;

    @BeforeEach
    void setUp() {
        loader = new WorkflowDefinitionRuntimeLoader();
        mockRegistrar = mock(WorkflowRegistrarService.class);
        mockConfig = mock(FlowRunnerConfig.class);
        mockSource = mock(FlowRunnerConfig.Source.class);

        loader.registrarService = mockRegistrar;
        loader.config = mockConfig;

        when(mockConfig.source()).thenReturn(mockSource);
    }

    @Test
    @DisplayName("test_loader_disabled_skips_loading")
    void test_loader_disabled_skips_loading() {
        // Given
        when(mockConfig.enabled()).thenReturn(false);

        // When
        loader.onStart(new WorkflowApplicationReadyEvent("ABC123"));

        // Then
        verify(mockRegistrar, never()).register(any(Workflow.class));
    }

    @Test
    @DisplayName("test_loader_without_path_skips_loading")
    void test_loader_without_path_skips_loading() {
        // Given
        when(mockConfig.enabled()).thenReturn(true);
        when(mockSource.path()).thenReturn(Optional.empty());

        // When
        loader.onStart(new WorkflowApplicationReadyEvent("ABC123"));

        // Then
        verify(mockRegistrar, never()).register(any(Workflow.class));
    }

    @Test
    @DisplayName("test_loader_with_nonexistent_path_throws_exception")
    void test_loader_with_nonexistent_path_throws_exception() {
        // Given
        when(mockConfig.enabled()).thenReturn(true);
        when(mockSource.path()).thenReturn(Optional.of("/nonexistent/path"));

        // When/Then
        assertThatThrownBy(() -> loader.onStart(new WorkflowApplicationReadyEvent("ABC123")))
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
        assertThatThrownBy(() -> loader.onStart(new WorkflowApplicationReadyEvent("ABC123")))
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
        loader.onStart(new WorkflowApplicationReadyEvent("ABC123"));

        // Then
        verify(mockRegistrar, never()).register(any(Workflow.class));
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
        when(mockRegistrar.register(any(Workflow.class))).thenReturn(mockDefinition);

        when(mockConfig.enabled()).thenReturn(true);
        when(mockSource.path()).thenReturn(Optional.of(tempDir.toString()));

        // When
        loader.onStart(new WorkflowApplicationReadyEvent("ABC123"));

        // Then
        verify(mockRegistrar, times(1)).register(any(Workflow.class));
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
        when(mockRegistrar.register(any(Workflow.class))).thenReturn(mockDefinition);

        when(mockConfig.enabled()).thenReturn(true);
        when(mockSource.path()).thenReturn(Optional.of(tempDir.toString()));

        // When
        loader.onStart(new WorkflowApplicationReadyEvent("ABC123"));

        // Then
        verify(mockRegistrar, times(1)).register(any(Workflow.class));
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
        when(mockRegistrar.register(any(Workflow.class))).thenReturn(mockDefinition);

        when(mockConfig.enabled()).thenReturn(true);
        when(mockSource.path()).thenReturn(Optional.of(tempDir.toString()));

        // When
        loader.onStart(new WorkflowApplicationReadyEvent("ABC123"));

        // Then
        verify(mockRegistrar, times(1)).register(any(Workflow.class));
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
        when(mockRegistrar.register(any(Workflow.class))).thenReturn(mockDefinition);

        when(mockConfig.enabled()).thenReturn(true);
        when(mockSource.path()).thenReturn(Optional.of(tempDir.toString()));

        // When
        loader.onStart(new WorkflowApplicationReadyEvent("ABC123"));

        // Then
        verify(mockRegistrar, times(2)).register(any(Workflow.class));
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
        when(mockRegistrar.register(any(Workflow.class))).thenReturn(mockDefinition);

        when(mockConfig.enabled()).thenReturn(true);
        when(mockSource.path()).thenReturn(Optional.of(tempDir.toString()));

        // When
        loader.onStart(new WorkflowApplicationReadyEvent("ABC123"));

        // Then
        verify(mockRegistrar, times(2)).register(any(Workflow.class));
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
        when(mockRegistrar.register(any(Workflow.class))).thenReturn(mockDefinition);

        when(mockConfig.enabled()).thenReturn(true);
        when(mockSource.path()).thenReturn(Optional.of(tempDir.toString()));

        // When
        loader.onStart(new WorkflowApplicationReadyEvent("ABC123"));

        // Then - Only the valid workflow should be loaded
        verify(mockRegistrar, times(1)).register(any(Workflow.class));
    }

    @Test
    @DisplayName("test_loader_skips_invalid_workflow_format_and_continues")
    void test_loader_skips_invalid_workflow_format_and_continues() throws IOException {
        // Given
        String invalidYaml = "this is not a valid workflow yaml";
        Files.writeString(tempDir.resolve("invalid.yaml"), invalidYaml);

        String validWorkflow = """
                document:
                  dsl: '1.0.0'
                  namespace: test-namespace
                  name: valid-after-invalid
                  version: '1.0.0'
                do:
                  - setMessage:
                      set:
                        message: "Still loaded"
                """;
        Files.writeString(tempDir.resolve("valid.yaml"), validWorkflow);

        WorkflowDefinition mockDefinition = mock(WorkflowDefinition.class);
        when(mockRegistrar.register(any(Workflow.class))).thenReturn(mockDefinition);

        when(mockConfig.enabled()).thenReturn(true);
        when(mockSource.path()).thenReturn(Optional.of(tempDir.toString()));

        // When - the malformed file must not abort loading
        loader.onStart(new WorkflowApplicationReadyEvent("ABC123"));

        // Then - the invalid file is skipped, but the valid workflow alongside it is still registered
        verify(mockRegistrar, times(1)).register(any(Workflow.class));
    }

    @Test
    @DisplayName("test_loader_skips_workflow_missing_required_fields_and_continues")
    void test_loader_skips_workflow_missing_required_fields_and_continues() throws IOException {
        // Given - workflow missing version (SDK parser rejects it)
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

        String validWorkflow = """
                document:
                  dsl: '1.0.0'
                  namespace: test-namespace
                  name: valid-after-incomplete
                  version: '1.0.0'
                do:
                  - setMessage:
                      set:
                        message: "Still loaded"
                """;
        Files.writeString(tempDir.resolve("valid.yaml"), validWorkflow);

        WorkflowDefinition mockDefinition = mock(WorkflowDefinition.class);
        when(mockRegistrar.register(any(Workflow.class))).thenReturn(mockDefinition);

        when(mockConfig.enabled()).thenReturn(true);
        when(mockSource.path()).thenReturn(Optional.of(tempDir.toString()));

        // When - SDK parser fails for missing required fields, must not abort loading
        loader.onStart(new WorkflowApplicationReadyEvent("ABC123"));

        // Then - the invalid file is skipped, but the valid workflow alongside it is still registered
        verify(mockRegistrar, times(1)).register(any(Workflow.class));
    }

    @Test
    @DisplayName("test_loader_skips_duplicate_workflow_ids_and_continues")
    void test_loader_skips_duplicate_workflow_ids_and_continues() throws IOException {
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

        WorkflowDefinition mockDefinition = mock(WorkflowDefinition.class);
        when(mockRegistrar.register(any(Workflow.class))).thenReturn(mockDefinition);

        when(mockConfig.enabled()).thenReturn(true);
        when(mockSource.path()).thenReturn(Optional.of(tempDir.toString()));

        // When - the duplicate must be skipped, not abort the whole load
        loader.onStart(new WorkflowApplicationReadyEvent("ABC123"));

        // Then - only the first occurrence is registered
        verify(mockRegistrar, times(1)).register(any(Workflow.class));
    }

    // -- Symlink tests (Issue #835) --

    private static final String SYMLINK_WORKFLOW_YAML = """
            document:
              dsl: '1.0.0'
              namespace: test-namespace
              name: symlinked-workflow
              version: '1.0.0'
            do:
              - setMessage:
                  set:
                    message: "Symlinked"
            """;

    private boolean supportsSymlinks() {
        try {
            Path probe = tempDir.resolve(".symlink-probe");
            Path target = tempDir.resolve(".symlink-target");
            Files.writeString(target, "probe");
            Files.createSymbolicLink(probe, target);
            Files.delete(probe);
            Files.delete(target);
            return true;
        } catch (IOException | UnsupportedOperationException e) {
            return false;
        }
    }

    @Test
    @DisplayName("test_loader_skips_symlinked_files_by_default")
    void test_loader_skips_symlinked_files_by_default() throws IOException {
        assumeThat(supportsSymlinks()).as("Symbolic links not supported on this platform").isTrue();

        // Given - a real file in a subdirectory and a symlink to it at the top level (mimics ConfigMap structure)
        Path realDir = tempDir.resolve("..real");
        Files.createDirectories(realDir);
        Path realFile = realDir.resolve("workflow.yaml");
        Files.writeString(realFile, SYMLINK_WORKFLOW_YAML);

        Path symlinkFile = tempDir.resolve("workflow.yaml");
        Files.createSymbolicLink(symlinkFile, realFile);

        WorkflowDefinition mockDefinition = mock(WorkflowDefinition.class);
        when(mockRegistrar.register(any(Workflow.class))).thenReturn(mockDefinition);

        when(mockConfig.enabled()).thenReturn(true);
        when(mockSource.path()).thenReturn(Optional.of(tempDir.toString()));
        when(mockSource.followSymlinks()).thenReturn(false);

        // When
        loader.onStart(new WorkflowApplicationReadyEvent("ABC123"));

        // Then - only the real file should be loaded, symlink is skipped
        verify(mockRegistrar, times(1)).register(any(Workflow.class));
    }

    @Test
    @DisplayName("test_loader_follows_symlinked_files_when_enabled")
    void test_loader_follows_symlinked_files_when_enabled() throws IOException {
        assumeThat(supportsSymlinks()).as("Symbolic links not supported on this platform").isTrue();

        // Given - a real file in a subdirectory and a symlink at the top level
        // Both resolve to the same workflow identity, so followSymlinks=true causes a duplicate
        Path realDir = tempDir.resolve("..real");
        Files.createDirectories(realDir);
        Path realFile = realDir.resolve("workflow.yaml");
        Files.writeString(realFile, SYMLINK_WORKFLOW_YAML);

        Path symlinkFile = tempDir.resolve("workflow.yaml");
        Files.createSymbolicLink(symlinkFile, realFile);

        WorkflowDefinition mockDefinition = mock(WorkflowDefinition.class);
        when(mockRegistrar.register(any(Workflow.class))).thenReturn(mockDefinition);

        when(mockConfig.enabled()).thenReturn(true);
        when(mockSource.path()).thenReturn(Optional.of(tempDir.toString()));
        when(mockSource.followSymlinks()).thenReturn(true);

        // When - both the real file and the symlink are followed, causing a duplicate that must be skipped, not fatal
        loader.onStart(new WorkflowApplicationReadyEvent("ABC123"));

        // Then - only the first occurrence (the real file) is registered
        verify(mockRegistrar, times(1)).register(any(Workflow.class));
    }

    @Test
    @DisplayName("test_loader_skips_symlinked_base_directory")
    void test_loader_skips_symlinked_base_directory() throws IOException {
        assumeThat(supportsSymlinks()).as("Symbolic links not supported on this platform").isTrue();

        // Given - base path is itself a symlink to a real directory
        Path realDir = tempDir.resolve("real-workflows");
        Files.createDirectories(realDir);
        Files.writeString(realDir.resolve("workflow.yaml"), SYMLINK_WORKFLOW_YAML);

        Path symlinkDir = tempDir.resolve("workflows-link");
        Files.createSymbolicLink(symlinkDir, realDir);

        when(mockConfig.enabled()).thenReturn(true);
        when(mockSource.path()).thenReturn(Optional.of(symlinkDir.toString()));
        when(mockSource.followSymlinks()).thenReturn(false);

        // When
        loader.onStart(new WorkflowApplicationReadyEvent("ABC123"));

        // Then - the symlinked base directory is skipped entirely
        verify(mockRegistrar, never()).register(any(Workflow.class));
    }

    @Test
    @DisplayName("test_loader_follows_symlinked_base_directory_when_enabled")
    void test_loader_follows_symlinked_base_directory_when_enabled() throws IOException {
        assumeThat(supportsSymlinks()).as("Symbolic links not supported on this platform").isTrue();

        // Given - base path is itself a symlink to a real directory
        Path realDir = tempDir.resolve("real-workflows");
        Files.createDirectories(realDir);
        Files.writeString(realDir.resolve("workflow.yaml"), SYMLINK_WORKFLOW_YAML);

        Path symlinkDir = tempDir.resolve("workflows-link");
        Files.createSymbolicLink(symlinkDir, realDir);

        WorkflowDefinition mockDefinition = mock(WorkflowDefinition.class);
        when(mockRegistrar.register(any(Workflow.class))).thenReturn(mockDefinition);

        when(mockConfig.enabled()).thenReturn(true);
        when(mockSource.path()).thenReturn(Optional.of(symlinkDir.toString()));
        when(mockSource.followSymlinks()).thenReturn(true);

        // When
        loader.onStart(new WorkflowApplicationReadyEvent("ABC123"));

        // Then - Files.walk with FOLLOW_LINKS descends into the symlinked base directory
        verify(mockRegistrar, times(1)).register(any(Workflow.class));
    }

    @Test
    @DisplayName("test_loader_handles_configmap_like_symlink_structure")
    void test_loader_handles_configmap_like_symlink_structure() throws IOException {
        assumeThat(supportsSymlinks()).as("Symbolic links not supported on this platform").isTrue();

        // Given - Kubernetes ConfigMap-like structure:
        //   tempDir/
        //   ├── ..2026_08_10/         (real timestamped directory)
        //   │   └── my-workflow.yaml  (real file)
        //   ├── ..data -> ..2026_08_10/  (symlink)
        //   └── my-workflow.yaml -> ..data/my-workflow.yaml (symlink)
        Path timestampedDir = tempDir.resolve("..2026_08_10");
        Files.createDirectories(timestampedDir);
        Files.writeString(timestampedDir.resolve("my-workflow.yaml"), SYMLINK_WORKFLOW_YAML);

        Path dataLink = tempDir.resolve("..data");
        Files.createSymbolicLink(dataLink, timestampedDir);

        Path topLevelLink = tempDir.resolve("my-workflow.yaml");
        Files.createSymbolicLink(topLevelLink, dataLink.resolve("my-workflow.yaml"));

        WorkflowDefinition mockDefinition = mock(WorkflowDefinition.class);
        when(mockRegistrar.register(any(Workflow.class))).thenReturn(mockDefinition);

        when(mockConfig.enabled()).thenReturn(true);
        when(mockSource.path()).thenReturn(Optional.of(tempDir.toString()));
        when(mockSource.followSymlinks()).thenReturn(false);

        // When
        loader.onStart(new WorkflowApplicationReadyEvent("ABC123"));

        // Then - only the real file is loaded, both symlinks are skipped
        verify(mockRegistrar, times(1)).register(any(Workflow.class));
    }
}
