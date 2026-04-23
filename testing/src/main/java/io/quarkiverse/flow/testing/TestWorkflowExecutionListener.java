package io.quarkiverse.flow.testing;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.quarkiverse.flow.testing.events.RecordedWorkflowEvent;
import io.serverlessworkflow.impl.lifecycle.TaskCancelledEvent;
import io.serverlessworkflow.impl.lifecycle.TaskCompletedEvent;
import io.serverlessworkflow.impl.lifecycle.TaskFailedEvent;
import io.serverlessworkflow.impl.lifecycle.TaskResumedEvent;
import io.serverlessworkflow.impl.lifecycle.TaskRetriedEvent;
import io.serverlessworkflow.impl.lifecycle.TaskStartedEvent;
import io.serverlessworkflow.impl.lifecycle.TaskSuspendedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowCancelledEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowCompletedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowExecutionListener;
import io.serverlessworkflow.impl.lifecycle.WorkflowFailedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowResumedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowStartedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowStatusEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowSuspendedEvent;

/**
 * Test-scoped workflow execution listener that records all lifecycle events for assertion in tests.
 * This listener is automatically registered in test scope and captures all workflow and task events
 * into a thread-safe event store for later verification.
 * <p>
 */
@ApplicationScoped
public class TestWorkflowExecutionListener implements WorkflowExecutionListener {

    private static final Logger LOG = Logger.getLogger(TestWorkflowExecutionListener.class);

    WorkflowEventStore eventStore;

    public TestWorkflowExecutionListener(
            WorkflowEventStore eventStore
    ) {
        this.eventStore = eventStore;
    }


    @Override
    public int priority() {
        return 10;
    }

    @Override
    public void onWorkflowStarted(WorkflowStartedEvent event) {
        try {
            RecordedWorkflowEvent recordedEvent = RecordedWorkflowEvent.from(event);
            eventStore.record(recordedEvent);
            LOG.debugf("Recorded workflow started event: %s", recordedEvent);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to record workflow started event");
        }
    }

    @Override
    public void onWorkflowCompleted(WorkflowCompletedEvent event) {
        try {
            RecordedWorkflowEvent recordedEvent = RecordedWorkflowEvent.from(event);
            eventStore.record(recordedEvent);
            LOG.debugf("Recorded workflow completed event: %s", recordedEvent);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to record workflow completed event");
        }
    }

    @Override
    public void onWorkflowFailed(WorkflowFailedEvent event) {
        try {
            RecordedWorkflowEvent recordedEvent = RecordedWorkflowEvent.from(event);
            eventStore.record(recordedEvent);
            LOG.debugf("Recorded workflow failed event: %s", recordedEvent);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to record workflow failed event");
        }
    }

    @Override
    public void onWorkflowCancelled(WorkflowCancelledEvent event) {
        try {
            RecordedWorkflowEvent recordedEvent = RecordedWorkflowEvent.from(event);
            eventStore.record(recordedEvent);
            LOG.debugf("Recorded workflow cancelled event: %s", recordedEvent);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to record workflow cancelled event");
        }
    }

    @Override
    public void onWorkflowSuspended(WorkflowSuspendedEvent event) {
        try {
            RecordedWorkflowEvent recordedEvent = RecordedWorkflowEvent.from(event);
            eventStore.record(recordedEvent);
            LOG.debugf("Recorded workflow suspended event: %s", recordedEvent);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to record workflow suspended event");
        }
    }

    @Override
    public void onWorkflowResumed(WorkflowResumedEvent event) {
        try {
            RecordedWorkflowEvent recordedEvent = RecordedWorkflowEvent.from(event);
            eventStore.record(recordedEvent);
            LOG.debugf("Recorded workflow resumed event: %s", recordedEvent);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to record workflow resumed event");
        }
    }

    @Override
    public void onTaskStarted(TaskStartedEvent event) {
        try {
            RecordedWorkflowEvent recordedEvent = RecordedWorkflowEvent.from(event);
            eventStore.record(recordedEvent);
            LOG.debugf("Recorded task started event: %s", recordedEvent);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to record task started event");
        }
    }

    @Override
    public void onTaskCompleted(TaskCompletedEvent event) {
        try {
            RecordedWorkflowEvent recordedEvent = RecordedWorkflowEvent.from(event);
            eventStore.record(recordedEvent);
            LOG.debugf("Recorded task completed event: %s", recordedEvent);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to record task completed event");
        }
    }

    @Override
    public void onTaskFailed(TaskFailedEvent event) {
        try {
            RecordedWorkflowEvent recordedEvent = RecordedWorkflowEvent.from(event);
            eventStore.record(recordedEvent);
            LOG.debugf("Recorded task failed event: %s", recordedEvent);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to record task failed event");
        }
    }

    @Override
    public void onTaskCancelled(TaskCancelledEvent event) {
        try {
            RecordedWorkflowEvent recordedEvent = RecordedWorkflowEvent.from(event);
            eventStore.record(recordedEvent);
            LOG.debugf("Recorded task cancelled event: %s", recordedEvent);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to record task cancelled event");
        }
    }

    @Override
    public void onTaskSuspended(TaskSuspendedEvent event) {
        try {
            RecordedWorkflowEvent recordedEvent = RecordedWorkflowEvent.from(event);
            eventStore.record(recordedEvent);
            LOG.debugf("Recorded task suspended event: %s", recordedEvent);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to record task suspended event");
        }
    }

    @Override
    public void onTaskResumed(TaskResumedEvent event) {
        try {
            RecordedWorkflowEvent recordedEvent = RecordedWorkflowEvent.from(event);
            eventStore.record(recordedEvent);
            LOG.debugf("Recorded task resumed event: %s", recordedEvent);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to record task resumed event");
        }
    }

    @Override
    public void onTaskRetried(TaskRetriedEvent event) {
        try {
            RecordedWorkflowEvent recordedEvent = RecordedWorkflowEvent.from(event);
            eventStore.record(recordedEvent);
            LOG.debugf("Recorded task retried event: %s", recordedEvent);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to record task retried event");
        }
    }
}
