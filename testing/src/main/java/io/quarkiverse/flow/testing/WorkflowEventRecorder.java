package io.quarkiverse.flow.testing;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.flow.testing.events.EventType;
import io.quarkiverse.flow.testing.events.RecordedWorkflowEvent;

/**
 * Main entry point for workflow event recording and assertions in tests.
 * This bean is automatically available in test scope and provides access to recorded
 * workflow events, fluent assertions, and async event waiting.
 * <p>
 * Example usage:
 *
 * <pre>
 * &#64;QuarkusTest
 * class MyWorkflowTest {
 *     &#64;Inject
 *     WorkflowEventRecorder eventRecorder;
 *
 *     &#64;Test
 *     void should_complete_workflow() {
 *         // Execute workflow
 *         workflow.instance(Map.of()).start().await().indefinitely();
 *
 *         // Assert on events
 *         eventRecorder.assertThat()
 *                 .workflowStarted()
 *                 .taskStarted("task1")
 *                 .taskCompleted("task1")
 *                 .workflowCompleted();
 *     }
 * }
 * </pre>
 */
@ApplicationScoped
public class WorkflowEventRecorder {

    @Inject
    WorkflowEventStore eventStore;

    /**
     * Returns a fluent assertion API for verifying workflow events.
     * This is the primary method for asserting on event sequences in tests.
     *
     * @return fluent assertion API
     */
    public FluentEventAssertions assertThat() {
        return new FluentEventAssertions(eventStore.getAll());
    }

    /**
     * Returns all recorded events for the current thread.
     *
     * @return immutable list of all recorded events
     */
    public List<RecordedWorkflowEvent> getEvents() {
        return eventStore.getAll();
    }

    /**
     * Returns all events of a specific type.
     *
     * @param type the event type to filter by
     * @return immutable list of events matching the type
     */
    public List<RecordedWorkflowEvent> getEvents(EventType type) {
        return eventStore.getByType(type);
    }

    /**
     * Returns all events for a specific workflow instance.
     *
     * @param instanceId the workflow instance ID
     * @return immutable list of events for the instance
     */
    public List<RecordedWorkflowEvent> getEventsForInstance(String instanceId) {
        return eventStore.getByInstanceId(instanceId);
    }

    /**
     * Returns all workflow-level events (not task events).
     *
     * @return immutable list of workflow events
     */
    public List<RecordedWorkflowEvent> getWorkflowEvents() {
        return eventStore.getWorkflowEvents();
    }

    /**
     * Returns all task-level events.
     *
     * @return immutable list of task events
     */
    public List<RecordedWorkflowEvent> getTaskEvents() {
        return eventStore.getTaskEvents();
    }

    /**
     * Returns all events for a specific task name.
     *
     * @param taskName the task name to filter by
     * @return immutable list of events for the task
     */
    public List<RecordedWorkflowEvent> getEventsByTaskName(String taskName) {
        return eventStore.getByTaskName(taskName);
    }

    /**
     * Returns an event waiter for asynchronous event waiting.
     * Use this to wait for specific events during async workflow execution.
     *
     * @return event waiter
     */
    public EventWaiter waitFor() {
        return new EventWaiter(eventStore);
    }

    /**
     * Clears all recorded events for the current thread.
     * This is automatically called by the JUnit extension after each test,
     * but can be called manually if needed.
     */
    public void clear() {
        eventStore.clear();
    }

    /**
     * Returns the number of recorded events.
     *
     * @return the event count
     */
    public int getEventCount() {
        return eventStore.size();
    }

    /**
     * Checks if any events have been recorded.
     *
     * @return true if no events recorded, false otherwise
     */
    public boolean isEmpty() {
        return eventStore.isEmpty();
    }

    /**
     * Prints all recorded events to standard output for debugging.
     * Each event is printed with its type, timestamp, and relevant metadata.
     */
    public void printEvents() {
        List<RecordedWorkflowEvent> events = eventStore.getAll();
        if (events.isEmpty()) {
            System.out.println("No events recorded");
            return;
        }

        System.out.println("Recorded Events (" + events.size() + "):");
        System.out.println("=".repeat(80));
        for (int i = 0; i < events.size(); i++) {
            RecordedWorkflowEvent event = events.get(i);
            System.out.printf("[%d] %s - %s%n", i, event.getType(), event.getTimestamp());
            System.out.printf("    Workflow: %s, Instance: %s%n",
                    event.getWorkflowId(), event.getInstanceId());
            event.getTaskName().ifPresent(name -> System.out.printf("    Task: %s%n", name));
            event.getErrorMessage().ifPresent(msg -> System.out.printf("    Error: %s%n", msg));
            System.out.println();
        }
        System.out.println("=".repeat(80));
    }
}
