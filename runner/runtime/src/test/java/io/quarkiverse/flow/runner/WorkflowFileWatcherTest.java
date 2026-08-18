package io.quarkiverse.flow.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.quarkiverse.flow.internal.WorkflowApplicationReadyEvent;
import io.quarkiverse.flow.internal.WorkflowRegistrarService;
import io.quarkus.scheduler.Scheduler;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowDefinitionId;

@DisplayName("WorkflowFileWatcher Tests")
class WorkflowFileWatcherTest {

    @TempDir
    Path tempDir;

    private WorkflowFileWatcher watcher;
    private WorkflowDefinitionRuntimeLoader loader;
    private WorkflowRegistrarService mockRegistrar;
    private WorkflowApplication mockApplication;
    private FlowRunnerConfig mockConfig;
    private FlowRunnerConfig.Source mockSource;

    private static final String WORKFLOW_YAML_TEMPLATE = """
            document:
              dsl: '1.0.0'
              namespace: %s
              name: %s
              version: '%s'
            do:
              - setMessage:
                  set:
                    message: "Hello"
            """;

    @BeforeEach
    void setUp() {
        watcher = new WorkflowFileWatcher();
        loader = new WorkflowDefinitionRuntimeLoader();
        mockRegistrar = mock(WorkflowRegistrarService.class);
        mockApplication = mock(WorkflowApplication.class);
        mockConfig = mock(FlowRunnerConfig.class);
        mockSource = mock(FlowRunnerConfig.Source.class);

        loader.registrarService = mockRegistrar;
        loader.config = mockConfig;

        watcher.registrarService = mockRegistrar;
        watcher.application = mockApplication;
        watcher.loader = loader;
        watcher.config = mockConfig;

        when(mockConfig.source()).thenReturn(mockSource);
        when(mockConfig.enabled()).thenReturn(true);
        when(mockSource.path()).thenReturn(Optional.of(tempDir.toString()));
        when(mockSource.followSymlinks()).thenReturn(false);
        when(mockApplication.workflowDefinitions()).thenReturn(Collections.emptyMap());

        WorkflowDefinition mockDefinition = mock(WorkflowDefinition.class);
        when(mockRegistrar.register(any(Workflow.class))).thenReturn(mockDefinition);
    }

    @Test
    @DisplayName("test_watcher_detects_new_yaml_file")
    void test_watcher_detects_new_yaml_file() throws IOException {
        Files.writeString(tempDir.resolve("new-workflow.yaml"),
                WORKFLOW_YAML_TEMPLATE.formatted("test-ns", "new-workflow", "1.0.0"));

        watcher.poll();

        verify(mockRegistrar, times(1)).register(any(Workflow.class));
        assertThat(watcher.registeredFiles).hasSize(1);
    }

    @Test
    @DisplayName("test_watcher_detects_new_yml_file")
    void test_watcher_detects_new_yml_file() throws IOException {
        Files.writeString(tempDir.resolve("new-workflow.yml"),
                WORKFLOW_YAML_TEMPLATE.formatted("test-ns", "yml-workflow", "1.0.0"));

        watcher.poll();

        verify(mockRegistrar, times(1)).register(any(Workflow.class));
    }

    @Test
    @DisplayName("test_watcher_detects_new_json_file")
    void test_watcher_detects_new_json_file() throws IOException {
        String workflowJson = """
                {
                  "document": {
                    "dsl": "1.0.0",
                    "namespace": "test-ns",
                    "name": "json-workflow",
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
        Files.writeString(tempDir.resolve("new-workflow.json"), workflowJson);

        watcher.poll();

        verify(mockRegistrar, times(1)).register(any(Workflow.class));
    }

    @Test
    @DisplayName("test_watcher_detects_new_files_in_subdirectory")
    void test_watcher_detects_new_files_in_subdirectory() throws IOException {
        Path subDir = tempDir.resolve("subdir");
        Files.createDirectories(subDir);
        Files.writeString(subDir.resolve("sub-workflow.yaml"),
                WORKFLOW_YAML_TEMPLATE.formatted("test-ns", "sub-workflow", "1.0.0"));

        watcher.poll();

        verify(mockRegistrar, times(1)).register(any(Workflow.class));
    }

    @Test
    @DisplayName("test_watcher_does_not_reparse_already_registered_files")
    void test_watcher_does_not_reparse_already_registered_files() throws IOException {
        Files.writeString(tempDir.resolve("existing.yaml"),
                WORKFLOW_YAML_TEMPLATE.formatted("test-ns", "existing", "1.0.0"));

        watcher.poll();
        verify(mockRegistrar, times(1)).register(any(Workflow.class));

        watcher.poll();
        // Still only 1 call — file is in the cache, never re-parsed
        verify(mockRegistrar, times(1)).register(any(Workflow.class));
    }

    @Test
    @DisplayName("test_baseline_prevents_parsing_startup_files")
    void test_baseline_prevents_parsing_startup_files() throws IOException {
        Files.writeString(tempDir.resolve("startup.yaml"),
                WORKFLOW_YAML_TEMPLATE.formatted("test-ns", "startup", "1.0.0"));

        watcher.buildBaseline();
        assertThat(watcher.registeredFiles).hasSize(1);

        watcher.poll();
        // File was in baseline — never parsed or registered by the watcher
        verify(mockRegistrar, never()).register(any(Workflow.class));
    }

    @Test
    @DisplayName("test_watcher_ignores_unsupported_extensions")
    void test_watcher_ignores_unsupported_extensions() throws IOException {
        Files.writeString(tempDir.resolve("readme.txt"), "Not a workflow");
        Files.writeString(tempDir.resolve("config.xml"), "<config/>");
        Files.writeString(tempDir.resolve("data.csv"), "a,b,c");

        watcher.poll();

        verify(mockRegistrar, never()).register(any(Workflow.class));
        assertThat(watcher.registeredFiles).isEmpty();
    }

    @Test
    @DisplayName("test_watcher_skips_malformed_files_without_crashing")
    void test_watcher_skips_malformed_files_without_crashing() throws IOException {
        Files.writeString(tempDir.resolve("bad.yaml"), "this is not valid workflow yaml");

        watcher.poll();

        verify(mockRegistrar, never()).register(any(Workflow.class));
        assertThat(watcher.registeredFiles).doesNotContainKey(tempDir.resolve("bad.yaml"));
    }

    @Test
    @DisplayName("test_watcher_detects_multiple_new_files_in_single_poll")
    void test_watcher_detects_multiple_new_files_in_single_poll() throws IOException {
        Files.writeString(tempDir.resolve("workflow-1.yaml"),
                WORKFLOW_YAML_TEMPLATE.formatted("test-ns", "workflow-1", "1.0.0"));
        Files.writeString(tempDir.resolve("workflow-2.yaml"),
                WORKFLOW_YAML_TEMPLATE.formatted("test-ns", "workflow-2", "1.0.0"));
        Files.writeString(tempDir.resolve("workflow-3.yml"),
                WORKFLOW_YAML_TEMPLATE.formatted("test-ns", "workflow-3", "1.0.0"));

        watcher.poll();

        verify(mockRegistrar, times(3)).register(any(Workflow.class));
        assertThat(watcher.registeredFiles).hasSize(3);
    }

    @Test
    @DisplayName("test_watcher_retries_malformed_file_on_next_poll")
    void test_watcher_retries_malformed_file_on_next_poll() throws IOException {
        Path workflowFile = tempDir.resolve("fixing.yaml");
        Files.writeString(workflowFile, "invalid yaml content");

        watcher.poll();
        verify(mockRegistrar, never()).register(any(Workflow.class));

        Files.writeString(workflowFile,
                WORKFLOW_YAML_TEMPLATE.formatted("test-ns", "fixed-workflow", "1.0.0"));

        watcher.poll();
        verify(mockRegistrar, times(1)).register(any(Workflow.class));
        assertThat(watcher.registeredFiles).containsKey(workflowFile);
    }

    @Test
    @DisplayName("test_watcher_handles_empty_source_path_gracefully")
    void test_watcher_handles_empty_source_path_gracefully() {
        when(mockSource.path()).thenReturn(Optional.empty());

        watcher.poll();

        verify(mockRegistrar, never()).register(any(Workflow.class));
    }

    @Test
    @DisplayName("test_watcher_skips_already_registered_workflow_id")
    void test_watcher_skips_already_registered_workflow_id() throws IOException {
        Files.writeString(tempDir.resolve("duplicate.yaml"),
                WORKFLOW_YAML_TEMPLATE.formatted("test-ns", "already-registered", "1.0.0"));

        WorkflowDefinitionId existingId = new WorkflowDefinitionId("test-ns", "already-registered", "1.0.0");
        when(mockApplication.workflowDefinitions()).thenReturn(Map.of(existingId, mock(WorkflowDefinition.class)));

        watcher.poll();

        verify(mockRegistrar, never()).register(any(Workflow.class));
        assertThat(watcher.registeredFiles).containsKey(tempDir.resolve("duplicate.yaml"));
    }

    @Test
    @DisplayName("test_watcher_does_not_start_when_runner_disabled")
    void test_watcher_does_not_start_when_runner_disabled() throws IOException {
        when(mockConfig.enabled()).thenReturn(false);

        Scheduler mockScheduler = mock(Scheduler.class);
        watcher.scheduler = mockScheduler;

        Files.writeString(tempDir.resolve("new-workflow.yaml"),
                WORKFLOW_YAML_TEMPLATE.formatted("test-ns", "new-workflow", "1.0.0"));

        watcher.onStart(new WorkflowApplicationReadyEvent("test"));

        verify(mockScheduler, never()).newJob(any());
        assertThat(watcher.registeredFiles).isEmpty();
    }

    @Test
    @DisplayName("test_scanner_returns_supported_files_only")
    void test_scanner_returns_supported_files_only() throws IOException {
        Files.writeString(tempDir.resolve("valid.yaml"),
                WORKFLOW_YAML_TEMPLATE.formatted("test-ns", "valid", "1.0.0"));
        Files.writeString(tempDir.resolve("also-valid.json"), "{}");
        Files.writeString(tempDir.resolve("not-valid.txt"), "text");

        List<Path> files = loader.scanWorkflowFiles();

        assertThat(files).hasSize(2);
        assertThat(files).allMatch(p -> p.toString().endsWith(".yaml") || p.toString().endsWith(".json"));
    }

    @Test
    @DisplayName("test_scanner_returns_empty_for_missing_path")
    void test_scanner_returns_empty_for_missing_path() {
        when(mockSource.path()).thenReturn(Optional.of("/nonexistent/path"));

        List<Path> files = loader.scanWorkflowFiles();

        assertThat(files).isEmpty();
    }

    @Test
    @DisplayName("test_scanner_returns_empty_when_path_not_configured")
    void test_scanner_returns_empty_when_path_not_configured() {
        when(mockSource.path()).thenReturn(Optional.empty());

        List<Path> files = loader.scanWorkflowFiles();

        assertThat(files).isEmpty();
    }

    @Nested
    @DisplayName("Build-time registration guard")
    class BuildTimeRegistrationGuard {

        @Test
        @DisplayName("test_watcher_has_no_bean_defining_annotations")
        void test_watcher_has_no_bean_defining_annotations() {
            for (Annotation annotation : WorkflowFileWatcher.class.getAnnotations()) {
                String name = annotation.annotationType().getName();
                assertThat(name)
                        .as("WorkflowFileWatcher must not have bean-defining annotations — "
                                + "registration is controlled by the deployment processor "
                                + "so the bean only exists when watch is enabled")
                        .doesNotContain("ApplicationScoped")
                        .doesNotContain("Singleton")
                        .doesNotContain("RequestScoped")
                        .doesNotContain("Dependent")
                        .doesNotContain("Unremovable");
            }
        }
    }
}
