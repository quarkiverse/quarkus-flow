package io.quarkiverse.flow.persistence.common;

import static io.quarkiverse.flow.persistence.common.FlowPersistenceUtils.excludedIds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import io.serverlessworkflow.api.types.Document;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowContextData;
import io.serverlessworkflow.impl.WorkflowDefinitionData;
import io.serverlessworkflow.impl.WorkflowDefinitionId;
import io.serverlessworkflow.impl.persistence.PersistenceInstanceWriter;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FilteredPersistenceWriterTest {

    @Mock
    PersistenceInstanceWriter delegate;

    @Mock
    WorkflowContextData excludedContext;

    @Mock
    WorkflowContextData includedContext;

    @Mock
    WorkflowDefinitionData excludedDefinition;

    @Mock
    WorkflowDefinitionData includedDefinition;

    private static final String EXCLUDED_WORKFLOW_NAME = "com.example:excluded-workflow:1.0.0";

    @BeforeEach
    void setUp() {
        Workflow excludedWorkflow = new Workflow()
                .withDocument(new Document().withNamespace("com.example").withName("excluded-workflow").withVersion("1.0.0"));
        Workflow includedWorkflow = new Workflow()
                .withDocument(new Document().withNamespace("com.example").withName("included-workflow").withVersion("1.0.0"));

        when(excludedContext.definition()).thenReturn(excludedDefinition);
        when(excludedDefinition.workflow()).thenReturn(excludedWorkflow);
        when(excludedDefinition.id()).thenReturn(new WorkflowDefinitionId("com.example", "excluded-workflow", "1.0.0"));

        when(includedContext.definition()).thenReturn(includedDefinition);
        when(includedDefinition.workflow()).thenReturn(includedWorkflow);
        when(includedDefinition.id()).thenReturn(new WorkflowDefinitionId("com.example", "included-workflow", "1.0.0"));
    }

    @Test
    @DisplayName("Should not delegate excluded workflow (started)")
    void test_started_excluded_workflow_should_not_delegate() {
        FilteredPersistenceWriter writer = new FilteredPersistenceWriter(delegate,
                excludedIds(Optional.of(List.of(EXCLUDED_WORKFLOW_NAME))));
        CompletableFuture<Void> result = writer.started(excludedContext);
        assertThat(result).isCompleted();
        verifyNoInteractions(delegate);
    }

    @Test
    @DisplayName("Should delegate non excluded workflow (started)")
    void test_started_non_excluded_workflow_should_delegate() {
        when(delegate.started(any())).thenReturn(CompletableFuture.completedFuture(null));
        FilteredPersistenceWriter writer = new FilteredPersistenceWriter(delegate,
                excludedIds(Optional.of(List.of(EXCLUDED_WORKFLOW_NAME))));
        writer.started(includedContext);
        verify(delegate).started(includedContext);
    }

    @Test
    @DisplayName("Should not delegate excluded workflow (completed)")
    void test_completed_excluded_workflow_should_not_delegate() {
        FilteredPersistenceWriter writer = new FilteredPersistenceWriter(delegate,
                excludedIds(Optional.of(List.of(EXCLUDED_WORKFLOW_NAME))));
        writer.completed(excludedContext);
        verifyNoInteractions(delegate);
    }

    @Test
    @DisplayName("Should delegate non excluded workflow (completed)")
    void test_completed_non_excluded_workflow_should_delegate() {
        when(delegate.completed(any())).thenReturn(CompletableFuture.completedFuture(null));
        FilteredPersistenceWriter writer = new FilteredPersistenceWriter(delegate,
                excludedIds(Optional.of(List.of(EXCLUDED_WORKFLOW_NAME))));
        writer.completed(includedContext);
        verify(delegate).completed(includedContext);
    }

    @Test
    @DisplayName("Should not delegate excluded workflow (failed)")
    void test_failed_excluded_workflow_should_not_delegate() {
        FilteredPersistenceWriter writer = new FilteredPersistenceWriter(delegate,
                excludedIds(Optional.of(List.of(EXCLUDED_WORKFLOW_NAME))));
        writer.failed(excludedContext, new RuntimeException("test"));
        verifyNoInteractions(delegate);
    }

    @Test
    @DisplayName("Should delegate non excluded workflow (failed)")
    void test_failed_non_excluded_workflow_should_delegate() {
        when(delegate.failed(any(), any())).thenReturn(CompletableFuture.completedFuture(null));
        FilteredPersistenceWriter writer = new FilteredPersistenceWriter(delegate,
                excludedIds(Optional.of(List.of(EXCLUDED_WORKFLOW_NAME))));
        RuntimeException ex = new RuntimeException("test");
        writer.failed(includedContext, ex);
        verify(delegate).failed(includedContext, ex);
    }

    @Test
    @DisplayName("Should delegate excluded workflow (aborted)")
    void test_aborted_excluded_workflow_should_not_delegate() {
        FilteredPersistenceWriter writer = new FilteredPersistenceWriter(delegate,
                excludedIds(Optional.of(List.of(EXCLUDED_WORKFLOW_NAME))));
        writer.aborted(excludedContext);
        verifyNoInteractions(delegate);
    }

    @Test
    @DisplayName("Should delegate excluded workflow (suspended)")
    void test_suspended_excluded_workflow_should_not_delegate() {
        FilteredPersistenceWriter writer = new FilteredPersistenceWriter(delegate,
                excludedIds(Optional.of(List.of(EXCLUDED_WORKFLOW_NAME))));
        writer.suspended(excludedContext);
        verifyNoInteractions(delegate);
    }

    @Test
    @DisplayName("Should delegate excluded workflow (resumed)")
    void test_resumed_excluded_workflow_should_not_delegate() {
        FilteredPersistenceWriter writer = new FilteredPersistenceWriter(delegate,
                excludedIds(Optional.of(List.of(EXCLUDED_WORKFLOW_NAME))));
        writer.resumed(excludedContext);
        verifyNoInteractions(delegate);
    }

    @Test
    @DisplayName("Should delegate excluded workflow (all)")
    void test_empty_exclusion_list_delegates_all() {
        when(delegate.started(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(delegate.completed(any())).thenReturn(CompletableFuture.completedFuture(null));
        FilteredPersistenceWriter writer = new FilteredPersistenceWriter(delegate, Collections.emptySet());
        writer.started(includedContext);
        writer.completed(includedContext);
        verify(delegate).started(includedContext);
        verify(delegate).completed(includedContext);
    }
}