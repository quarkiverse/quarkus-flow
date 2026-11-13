package io.quarkiverse.flow.testing;

import org.jboss.logging.Logger;

import io.quarkiverse.flow.testing.events.RecordedEvent;
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
import io.serverlessworkflow.impl.lifecycle.WorkflowSuspendedEvent;

public class TestWorkflowExecutionListener implements WorkflowExecutionListener {

    private static final Logger log = Logger.getLogger(TestWorkflowExecutionListener.class);

    WorkflowEventStore eventStore;

    public TestWorkflowExecutionListener(
            WorkflowEventStore eventStore) {
        this.eventStore = eventStore;
    }

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public void onWorkflowStarted(WorkflowStartedEvent event) {
        try {
            RecordedEvent recordedEvent = RecordedEvent.from(event);
            eventStore.record(recordedEvent);
            log.debugv("Recorded workflow started event: {0}", recordedEvent);
        } catch (Exception e) {
            log.errorf(e, "Failed to record workflow started event");
        }
    }

    @Override
    public void onWorkflowCompleted(WorkflowCompletedEvent event) {
        try {
            RecordedEvent recordedEvent = RecordedEvent.from(event);
            eventStore.record(recordedEvent);
            log.debugv("Recorded workflow completed event: {0}", recordedEvent);
        } catch (Exception e) {
            log.errorf(e, "Failed to record workflow completed event");
        }
    }

    @Override
    public void onWorkflowFailed(WorkflowFailedEvent event) {
        try {
            RecordedEvent recordedEvent = RecordedEvent.from(event);
            eventStore.record(recordedEvent);
            log.debugv("Recorded workflow failed event: {0}", recordedEvent);
        } catch (Exception e) {
            log.errorf(e, "Failed to record workflow failed event");
        }
    }

    @Override
    public void onWorkflowCancelled(WorkflowCancelledEvent event) {
        try {
            RecordedEvent recordedEvent = RecordedEvent.from(event);
            eventStore.record(recordedEvent);
            log.debugv("Recorded workflow cancelled event: {0}", recordedEvent);
        } catch (Exception e) {
            log.errorf(e, "Failed to record workflow cancelled event");
        }
    }

    @Override
    public void onWorkflowSuspended(WorkflowSuspendedEvent event) {
        try {
            RecordedEvent recordedEvent = RecordedEvent.from(event);
            eventStore.record(recordedEvent);
            log.debugv("Recorded workflow suspended event: {0}", recordedEvent);
        } catch (Exception e) {
            log.errorf(e, "Failed to record workflow suspended event");
        }
    }

    @Override
    public void onWorkflowResumed(WorkflowResumedEvent event) {
        try {
            RecordedEvent recordedEvent = RecordedEvent.from(event);
            eventStore.record(recordedEvent);
            log.debugv("Recorded workflow resumed event: {0}", recordedEvent);
        } catch (Exception e) {
            log.errorf(e, "Failed to record workflow resumed event");
        }
    }

    @Override
    public void onTaskStarted(TaskStartedEvent event) {
        try {
            RecordedEvent recordedEvent = RecordedEvent.from(event);
            eventStore.record(recordedEvent);
            log.debugv("Recorded task started event: {0}", recordedEvent);
        } catch (Exception e) {
            log.errorf(e, "Failed to record task started event");
        }
    }

    @Override
    public void onTaskCompleted(TaskCompletedEvent event) {
        try {
            RecordedEvent recordedEvent = RecordedEvent.from(event);
            eventStore.record(recordedEvent);
            log.debugv("Recorded task completed event: {0}", recordedEvent);
        } catch (Exception e) {
            log.errorf(e, "Failed to record task completed event");
        }
    }

    @Override
    public void onTaskFailed(TaskFailedEvent event) {
        try {
            RecordedEvent recordedEvent = RecordedEvent.from(event);
            eventStore.record(recordedEvent);
            log.debugv("Recorded task failed event: {0}", recordedEvent);
        } catch (Exception e) {
            log.errorf(e, "Failed to record task failed event");
        }
    }

    @Override
    public void onTaskCancelled(TaskCancelledEvent event) {
        try {
            RecordedEvent recordedEvent = RecordedEvent.from(event);
            eventStore.record(recordedEvent);
            log.debugv("Recorded task cancelled event: {0}", recordedEvent);
        } catch (Exception e) {
            log.errorf(e, "Failed to record task cancelled event");
        }
    }

    @Override
    public void onTaskSuspended(TaskSuspendedEvent event) {
        try {
            RecordedEvent recordedEvent = RecordedEvent.from(event);
            eventStore.record(recordedEvent);
            log.debugv("Recorded task suspended event: {0}", recordedEvent);
        } catch (Exception e) {
            log.errorf(e, "Failed to record task suspended event");
        }
    }

    @Override
    public void onTaskResumed(TaskResumedEvent event) {
        try {
            RecordedEvent recordedEvent = RecordedEvent.from(event);
            eventStore.record(recordedEvent);
            log.debugv("Recorded task resumed event: {0}", recordedEvent);
        } catch (Exception e) {
            log.errorf(e, "Failed to record task resumed event");
        }
    }

    @Override
    public void onTaskRetried(TaskRetriedEvent event) {
        try {
            RecordedEvent recordedEvent = RecordedEvent.from(event);
            eventStore.record(recordedEvent);
            log.debugv("Recorded task retried event: {0}", recordedEvent);
        } catch (Exception e) {
            log.errorf(e, "Failed to record task retried event");
        }
    }
}
