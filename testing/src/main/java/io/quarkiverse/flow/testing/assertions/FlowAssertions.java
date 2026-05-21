package io.quarkiverse.flow.testing.assertions;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.assertj.core.api.Assertions;

import io.quarkiverse.flow.testing.WorkflowEventStore;
import io.quarkiverse.flow.testing.events.EventType;
import io.quarkiverse.flow.testing.events.RecordedWorkflowEvent;
import io.serverlessworkflow.impl.WorkflowInstance;
import io.serverlessworkflow.impl.WorkflowModel;

public class FlowAssertions implements ConfigurableAssertions {

    private final List<RecordedWorkflowEvent> events;
    private int current = 0;
    private boolean strictly = false;
    private RecordedWorkflowEvent currentVerifiedEvent = null;

    FlowAssertions(List<RecordedWorkflowEvent> events) {
        this.events = Collections.unmodifiableList(events);
    }

    public static ConfigurableAssertions assertWith(WorkflowEventStore eventStore) {
        return new FlowAssertions(eventStore.getAll());
    }

    // ── ConfigurableAssertions ────────────────────────────────────────────────

    @Override
    public ConfigurableAssertions strictly() {
        this.strictly = true;
        return this;
    }

    @Override
    public ConfigurableAssertions filteringBy(String id) {
        Objects.requireNonNull(id, "instanceId must not be null");
        List<RecordedWorkflowEvent> filtered = events.stream()
                .filter(e -> id.equals(e.getInstanceId()))
                .collect(Collectors.toList());
        return new FlowAssertions(filtered);
    }

    @Override
    public ConfigurableAssertions reset() {
        this.current = 0;
        this.strictly = false;
        this.currentVerifiedEvent = null;
        return this;
    }

    public FlowAssertions workflowStarted() {
        if (strictly) {
            assertNextEventTypeIs(EventType.WORKFLOW_STARTED);
        } else {
            Assertions.assertThat(events)
                    .as("At least one WORKFLOW_STARTED event")
                    .anySatisfy(e -> Assertions.assertThat(e.getType()).isEqualTo(EventType.WORKFLOW_STARTED));
        }
        return this;
    }

    public FlowAssertions workflowCompleted() {
        if (strictly) {
            assertNextEventTypeIs(EventType.WORKFLOW_COMPLETED);
        } else {
            currentVerifiedEvent = events.stream()
                    .filter(e -> e.getType() == EventType.WORKFLOW_COMPLETED)
                    .findFirst().orElse(null);
            Assertions.assertThat(currentVerifiedEvent).as("At least one WORKFLOW_COMPLETED event").isNotNull();
        }
        return this;
    }

    public FlowAssertions workflowCompleted(WorkflowInstance instance) {
        if (strictly) {
            RecordedWorkflowEvent event = assertNextEventTypeIs(EventType.WORKFLOW_COMPLETED);
            Assertions.assertThat(event.getInstanceId())
                    .as("Instance ID for WORKFLOW_COMPLETED").isEqualTo(instance.id());
        } else {
            currentVerifiedEvent = events.stream()
                    .filter(e -> e.getType() == EventType.WORKFLOW_COMPLETED && e.getInstanceId().equals(instance.id()))
                    .findFirst().orElse(null);
            Assertions.assertThat(currentVerifiedEvent)
                    .as("At least one WORKFLOW_COMPLETED for instance '%s'", instance.id()).isNotNull();
        }
        return this;
    }

    public FlowAssertions workflowFailed() {
        if (strictly) {
            assertNextEventTypeIs(EventType.WORKFLOW_FAILED);
        } else {
            currentVerifiedEvent = events.stream()
                    .filter(e -> e.getType() == EventType.WORKFLOW_FAILED)
                    .findFirst().orElse(null);
            Assertions.assertThat(currentVerifiedEvent).as("At least one WORKFLOW_FAILED event").isNotNull();
        }
        return this;
    }

    public FlowAssertions workflowCancelled() {
        if (strictly) {
            assertNextEventTypeIs(EventType.WORKFLOW_CANCELED);
        } else {
            currentVerifiedEvent = events.stream()
                    .filter(e -> e.getType() == EventType.WORKFLOW_CANCELED)
                    .findFirst().orElse(null);
            Assertions.assertThat(currentVerifiedEvent).as("At least one WORKFLOW_CANCELLED event").isNotNull();
        }
        return this;
    }

    public FlowAssertions workflowSuspended() {
        if (strictly) {
            assertNextEventTypeIs(EventType.WORKFLOW_SUSPENDED);
        } else {
            currentVerifiedEvent = events.stream()
                    .filter(e -> e.getType() == EventType.WORKFLOW_SUSPENDED)
                    .findFirst().orElse(null);
            Assertions.assertThat(currentVerifiedEvent).as("At least one WORKFLOW_SUSPENDED event").isNotNull();
        }
        return this;
    }

    public FlowAssertions workflowResumed() {
        if (strictly) {
            assertNextEventTypeIs(EventType.WORKFLOW_RESUMED);
        } else {
            currentVerifiedEvent = events.stream()
                    .filter(e -> e.getType() == EventType.WORKFLOW_RESUMED)
                    .findFirst().orElse(null);
            Assertions.assertThat(currentVerifiedEvent).as("At least one WORKFLOW_RESUMED event").isNotNull();
        }
        return this;
    }

    // ── Task event assertions ─────────────────────────────────────────────────

    public FlowAssertions taskStarted(String taskName) {
        return assertTaskEvent(EventType.TASK_STARTED, taskName);
    }

    public FlowAssertions taskCompleted(String taskName) {
        return assertTaskEvent(EventType.TASK_COMPLETED, taskName);
    }

    public FlowAssertions taskFailed(String taskName) {
        return assertTaskEvent(EventType.TASK_FAILED, taskName);
    }

    public FlowAssertions taskCancelled(String taskName) {
        return assertTaskEvent(EventType.TASK_CANCELLED, taskName);
    }

    public FlowAssertions taskSuspended(String taskName) {
        return assertTaskEvent(EventType.TASK_SUSPENDED, taskName);
    }

    public FlowAssertions taskResumed(String taskName) {
        return assertTaskEvent(EventType.TASK_RESUMED, taskName);
    }

    public FlowAssertions taskRetried(String taskName) {
        return assertTaskEvent(EventType.TASK_RETRIED, taskName);
    }

    private FlowAssertions assertTaskEvent(EventType type, String taskName) {
        if (strictly) {
            RecordedWorkflowEvent event = assertNextEventTypeIs(type);
            Assertions.assertThat(event.getTaskName())
                    .as("Task name for %s event", type).hasValue(taskName);
        } else {
            currentVerifiedEvent = events.stream()
                    .filter(e -> e.getType() == type)
                    .filter(e -> e.getTaskName().map(taskName::equals).orElse(false))
                    .findFirst().orElse(null);
            Assertions.assertThat(currentVerifiedEvent)
                    .as("At least one %s event for task '%s'", type, taskName).isNotNull();
        }
        return this;
    }

    // ── Output / Error ────────────────────────────────────────────────────────

    public void withOutput(Consumer<WorkflowModel> outputAssertion) {
        if (currentVerifiedEvent == null) {
            throw new AssertionError("No event has been verified yet. Call an event assertion method first.");
        }
        WorkflowModel output = currentVerifiedEvent.getOutput()
                .orElseThrow(() -> new AssertionError("Event " + currentVerifiedEvent.getType() + " has no output"));
        outputAssertion.accept(output);
    }

    @Override
    public ConfigurableAssertions configure() {
        return this;
    }

    public FlowAssertions withError(Consumer<Throwable> errorAssertion) {
        if (currentVerifiedEvent == null) {
            throw new AssertionError("No event has been verified yet. Call an event assertion method first.");
        }
        Throwable error = currentVerifiedEvent.getError()
                .orElseThrow(() -> new AssertionError("Event " + currentVerifiedEvent.getType() + " has no error"));
        errorAssertion.accept(error);
        return this;
    }

    // ── Count assertions ──────────────────────────────────────────────────────

    @Override
    public WorkflowAssertions hasWorkflowStartedEventCount(int n) {
        return hasEventTypeCount(EventType.WORKFLOW_STARTED, n);
    }

    @Override
    public WorkflowAssertions hasWorkflowCompletedEventCount(int n) {
        return hasEventTypeCount(EventType.WORKFLOW_COMPLETED, n);
    }

    @Override
    public WorkflowAssertions hasWorkflowFailedEventCount(int n) {
        return hasEventTypeCount(EventType.WORKFLOW_FAILED, n);
    }

    @Override
    public WorkflowAssertions hasWorkflowCanceledEventCount(int n) {
        return hasEventTypeCount(EventType.WORKFLOW_CANCELED, n);
    }

    @Override
    public WorkflowAssertions hasWorkflowSuspendedEventCount(int n) {
        return hasEventTypeCount(EventType.WORKFLOW_SUSPENDED, n);
    }

    @Override
    public WorkflowAssertions hasWorkflowResumedEventCount(int n) {
        return hasEventTypeCount(EventType.WORKFLOW_RESUMED, n);
    }

    @Override
    public WorkflowAssertions hasTaskStartedEventCount(int n) {
        return hasEventTypeCount(EventType.TASK_STARTED, n);
    }

    @Override
    public WorkflowAssertions hasTaskCompletedEventCount(int n) {
        return hasEventTypeCount(EventType.TASK_COMPLETED, n);
    }

    public FlowAssertions hasEventTypeCount(EventType type, int expected) {
        long count = events.stream().filter(e -> e.getType() == type).count();
        Assertions.assertThat(count).as("Count of %s events", type).isEqualTo(expected);
        return this;
    }

    // ── Structural assertions ─────────────────────────────────────────────────

    public FlowAssertions workflowCompletedWithin(Duration duration) {
        RecordedWorkflowEvent start = events.stream()
                .filter(e -> e.getType() == EventType.WORKFLOW_STARTED).findFirst()
                .orElseThrow(() -> new AssertionError("No WORKFLOW_STARTED event found"));
        RecordedWorkflowEvent end = events.stream()
                .filter(e -> e.getType() == EventType.WORKFLOW_COMPLETED).findFirst()
                .orElseThrow(() -> new AssertionError("No WORKFLOW_COMPLETED event found"));
        Assertions.assertThat(Duration.between(start.getTimestamp(), end.getTimestamp()))
                .as("Workflow execution duration").isLessThanOrEqualTo(duration);
        return this;
    }

    public FlowAssertions allEventsForInstance(String instanceId) {
        Assertions.assertThat(events)
                .as("All events should belong to instance %s", instanceId)
                .allMatch(e -> instanceId.equals(e.getInstanceId()));
        return this;
    }

    public FlowAssertions allEventsForWorkflow(String workflowId) {
        Assertions.assertThat(events)
                .as("All events should belong to workflow %s", workflowId)
                .allMatch(e -> workflowId.equals(e.getWorkflowId()));
        return this;
    }

    // ── Task completion order ─────────────────────────────────────────────────

    public TaskCompletionOrderAssertions assertTask(String taskName) {
        return new TaskCompletedAssertions(findTaskCompletedEvent(taskName), taskName, this);
    }

    public final class TaskCompletedAssertions implements TaskCompletionOrderAssertions {
        private final RecordedWorkflowEvent subject;
        private final String subjectName;
        private final FlowAssertions parent;

        private TaskCompletedAssertions(RecordedWorkflowEvent subject, String subjectName, FlowAssertions parent) {
            this.subject = subject;
            this.subjectName = subjectName;
            this.parent = parent;
        }

        public FlowAssertions completedBefore(String other) {
            Assertions.assertThat(subject.getTimestamp())
                    .as("'%s' should complete before '%s'", subjectName, other)
                    .isBefore(findTaskCompletedEvent(other).getTimestamp());
            return parent;
        }

        public FlowAssertions completedBeforeOrEqualTo(String other) {
            Assertions.assertThat(subject.getTimestamp())
                    .as("'%s' should complete before or at the same time as '%s'", subjectName, other)
                    .isBeforeOrEqualTo(findTaskCompletedEvent(other).getTimestamp());
            return parent;
        }

        public FlowAssertions completedAfter(String other) {
            Assertions.assertThat(subject.getTimestamp())
                    .as("'%s' should complete after '%s'", subjectName, other)
                    .isAfter(findTaskCompletedEvent(other).getTimestamp());
            return parent;
        }

        public FlowAssertions completedAfterOrEqualTo(String other) {
            Assertions.assertThat(subject.getTimestamp())
                    .as("'%s' should complete after or at the same time as '%s'", subjectName, other)
                    .isAfterOrEqualTo(findTaskCompletedEvent(other).getTimestamp());
            return parent;
        }
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private RecordedWorkflowEvent findTaskCompletedEvent(String taskName) {
        return events.stream()
                .filter(e -> e.getType() == EventType.TASK_COMPLETED)
                .filter(e -> e.getTaskName().map(taskName::equals).orElse(false))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No TASK_COMPLETED event found for task: " + taskName));
    }

    private RecordedWorkflowEvent assertNextEventTypeIs(EventType expectedType) {
        if (current >= events.size()) {
            throw new AssertionError(String.format(
                    "Expected event %s at index %d, but only %d events were recorded",
                    expectedType, current, events.size()));
        }
        RecordedWorkflowEvent event = events.get(current);
        Assertions.assertThat(event.getType()).as("Event type at index %d", current).isEqualTo(expectedType);
        currentVerifiedEvent = event;
        current++;
        return event;
    }
}
