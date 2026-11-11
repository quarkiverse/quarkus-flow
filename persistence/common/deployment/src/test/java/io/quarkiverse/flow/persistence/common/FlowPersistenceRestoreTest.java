package io.quarkiverse.flow.persistence.common;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import io.quarkiverse.flow.internal.WorkflowApplicationReadyEvent;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowDefinitionId;
import io.serverlessworkflow.impl.WorkflowInstance;
import io.serverlessworkflow.impl.persistence.PersistenceInstanceHandlers;
import io.serverlessworkflow.impl.persistence.PersistenceInstanceReader;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings({ "unchecked", "rawtypes" })
class FlowPersistenceRestoreTest {

    private static final WorkflowDefinitionId WORKFLOW_ID = new WorkflowDefinitionId("com-example", "workflow", "1.0.0");
    private static final WorkflowApplicationReadyEvent READY_EVENT = new WorkflowApplicationReadyEvent("test-app");

    @Mock
    WorkflowApplication application;

    @Mock
    WorkflowDefinition definition;

    @Mock
    WorkflowInstance instance;

    @Mock
    PersistenceInstanceHandlers handlers;

    @Mock
    PersistenceInstanceReader reader;

    @Mock
    FlowPersistenceConfig config;

    FlowPersistenceRestore restore;

    @BeforeEach
    void setUp() {
        restore = new FlowPersistenceRestore();
        restore.application = application;
        restore.handlers = handlers;
        restore.config = config;

        when(application.workflowDefinitions()).thenReturn(Map.of(WORKFLOW_ID, definition));
        when(definition.id()).thenReturn(WORKFLOW_ID);
        when(handlers.reader()).thenReturn(reader);
        when(config.excludeWorkflows()).thenReturn(Optional.empty());
        when(config.scanInterval()).thenReturn(Optional.empty());
        when(instance.id()).thenReturn("instance-1");
    }

    @Test
    @DisplayName("Should call start on every instance returned by scanAll, relying on WorkflowInstance.start() "
            + "being idempotent for instances already active in memory")
    void test_restore_calls_start_on_every_scanned_instance() {
        when(config.autoRestore()).thenReturn(true);
        when(reader.scanAll(definition)).thenReturn(Stream.of(instance));

        restore.restoreInstances(READY_EVENT);

        verify(instance).start();
    }

    @Test
    @DisplayName("Should skip excluded workflows when restoring")
    void test_restore_respects_exclude_workflows() {
        when(config.autoRestore()).thenReturn(true);
        when(config.excludeWorkflows()).thenReturn(Optional.of(List.of("com-example:workflow:1.0.0")));

        restore.restoreInstances(READY_EVENT);

        verifyNoInteractions(reader);
    }

    @Test
    @DisplayName("Should do nothing when auto-restore is disabled")
    void test_restore_noop_when_auto_restore_disabled() {
        when(config.autoRestore()).thenReturn(false);

        restore.restoreInstances(READY_EVENT);

        verifyNoInteractions(application);
        verifyNoInteractions(handlers);
    }

    @Test
    @DisplayName("Should start a local periodic scan when scan-interval is configured")
    void test_periodic_scan_started_when_scan_interval_configured() {
        when(config.autoRestore()).thenReturn(true);
        when(config.scanInterval()).thenReturn(Optional.of(Duration.ofSeconds(30)));
        when(reader.scanAll(definition)).thenReturn(Stream.empty());
        restore.restoreInstances(READY_EVENT);

        org.assertj.core.api.Assertions.assertThat(restore.isScanScheduled()).isTrue();
        restore.stop();
    }

    @Test
    @DisplayName("Should not start a periodic scan when scan-interval is absent")
    void test_periodic_scan_not_started_when_scan_interval_absent() {
        when(config.autoRestore()).thenReturn(true);
        when(reader.scanAll(definition)).thenReturn(Stream.empty());

        restore.restoreInstances(READY_EVENT);

        org.assertj.core.api.Assertions.assertThat(restore.isScanScheduled()).isFalse();
    }

    @Test
    @DisplayName("Should stop the periodic scan executor")
    void test_stop_shuts_down_scan_executor() {
        when(config.autoRestore()).thenReturn(true);
        when(config.scanInterval()).thenReturn(Optional.of(Duration.ofSeconds(30)));
        when(reader.scanAll(definition)).thenReturn(Stream.empty());
        restore.restoreInstances(READY_EVENT);
        restore.stop();

        org.assertj.core.api.Assertions.assertThat(restore.isScanScheduled()).isFalse();
    }

    @Test
    @DisplayName("Should stop cleanly when no periodic scan was configured")
    void test_stop_does_nothing_when_not_scheduled() {
        when(config.autoRestore()).thenReturn(true);
        when(reader.scanAll(definition)).thenReturn(Stream.empty());
        restore.restoreInstances(READY_EVENT);

        restore.stop();

        org.assertj.core.api.Assertions.assertThat(restore.isScanScheduled()).isFalse();
    }
}
