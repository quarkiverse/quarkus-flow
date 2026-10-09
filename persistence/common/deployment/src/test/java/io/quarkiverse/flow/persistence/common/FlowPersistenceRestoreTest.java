package io.quarkiverse.flow.persistence.common;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduler;
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

    @Mock
    Scheduler scheduler;

    FlowPersistenceRestore restore;

    @BeforeEach
    void setUp() {
        restore = new FlowPersistenceRestore();
        restore.application = application;
        restore.handlers = handlers;
        restore.config = config;
        restore.scheduler = scheduler;

        when(application.workflowDefinitions()).thenReturn(Map.of(WORKFLOW_ID, definition));
        when(definition.id()).thenReturn(WORKFLOW_ID);
        when(handlers.reader()).thenReturn(reader);
        when(config.excludeWorkflows()).thenReturn(Optional.empty());
        when(config.scanInterval()).thenReturn(Optional.empty());
        when(instance.id()).thenReturn("instance-1");
    }

    @Test
    @DisplayName("Should skip restoring an instance already active in memory")
    void test_restore_skips_instance_already_active_in_memory() {
        when(config.autoRestore()).thenReturn(true);
        when(reader.scanAll(definition)).thenReturn(Stream.of(instance));
        when(definition.activeInstance("instance-1")).thenReturn(Optional.of(instance));

        restore.restoreInstances(READY_EVENT);

        verify(instance, never()).start();
    }

    @Test
    @DisplayName("Should restore an instance not active in memory")
    void test_restore_starts_instance_not_active_in_memory() {
        when(config.autoRestore()).thenReturn(true);
        when(reader.scanAll(definition)).thenReturn(Stream.of(instance));
        when(definition.activeInstance("instance-1")).thenReturn(Optional.empty());

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
        verifyNoInteractions(scheduler);
    }

    @Test
    @DisplayName("Should schedule a periodic scan job when scan-interval is configured")
    void test_periodic_job_scheduled_when_scan_interval_configured() {
        when(config.autoRestore()).thenReturn(true);
        when(config.scanInterval()).thenReturn(Optional.of(Duration.ofSeconds(30)));
        when(reader.scanAll(definition)).thenReturn(Stream.empty());
        Scheduler.JobDefinition job = mockJobDefinition();
        when(scheduler.newJob(FlowPersistenceRestore.JOB_IDENTITY)).thenReturn(job);

        restore.restoreInstances(READY_EVENT);

        verify(scheduler).newJob(FlowPersistenceRestore.JOB_IDENTITY);
        verify(job).setInterval(Duration.ofSeconds(30).toString());
        verify(job).setConcurrentExecution(Scheduled.ConcurrentExecution.SKIP);
        verify(job).schedule();
    }

    @Test
    @DisplayName("Should not schedule a periodic scan job when scan-interval is absent")
    void test_periodic_job_not_scheduled_when_scan_interval_absent() {
        when(config.autoRestore()).thenReturn(true);
        when(reader.scanAll(definition)).thenReturn(Stream.empty());

        restore.restoreInstances(READY_EVENT);

        verify(scheduler, never()).newJob(anyString());
    }

    @Test
    @DisplayName("Should unschedule the job on stop when it was scheduled")
    void test_stop_unschedules_job_when_scheduled() {
        when(config.autoRestore()).thenReturn(true);
        when(config.scanInterval()).thenReturn(Optional.of(Duration.ofSeconds(30)));
        when(reader.scanAll(definition)).thenReturn(Stream.empty());
        Scheduler.JobDefinition job = mockJobDefinition();
        when(scheduler.newJob(FlowPersistenceRestore.JOB_IDENTITY)).thenReturn(job);
        restore.restoreInstances(READY_EVENT);

        restore.stop();

        verify(scheduler).unscheduleJob(FlowPersistenceRestore.JOB_IDENTITY);
    }

    @Test
    @DisplayName("Should not unschedule anything on stop when no job was scheduled")
    void test_stop_does_nothing_when_not_scheduled() {
        when(config.autoRestore()).thenReturn(true);
        when(reader.scanAll(definition)).thenReturn(Stream.empty());
        restore.restoreInstances(READY_EVENT);

        restore.stop();

        verify(scheduler, never()).unscheduleJob(anyString());
    }

    private Scheduler.JobDefinition mockJobDefinition() {
        Scheduler.JobDefinition job = mock(Scheduler.JobDefinition.class);
        when(job.setInterval(any())).thenReturn(job);
        when(job.setConcurrentExecution(any())).thenReturn(job);
        when(job.setTask(any(java.util.function.Consumer.class))).thenReturn(job);
        return job;
    }
}
