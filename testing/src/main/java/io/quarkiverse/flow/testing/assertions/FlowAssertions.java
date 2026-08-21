package io.quarkiverse.flow.testing.assertions;

import java.util.List;

import io.quarkiverse.flow.testing.WorkflowEventStore;
import io.quarkiverse.flow.testing.events.RecordedEvent;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InjectableInstance;
import io.serverlessworkflow.impl.WorkflowInstance;

/**
 * Entry point for AssertJ-style assertions on Quarkus Flow test recordings.
 *
 * <p>
 * The {@link #assertThat()} and {@link #assertThat(WorkflowInstance)} overloads resolve the
 * recording {@link WorkflowEventStore} from CDI, so they only work inside a running container
 * (e.g. a {@code @QuarkusTest}, typically paired with {@code @QuarkusFlowTest} so each test
 * starts with a clean recording session). The {@link #assertThat(List)} and
 * {@link #assertThat(RecordedEvent)} overloads work anywhere, since they assert directly on
 * values you already have in hand.
 *
 * <pre>{@code
 * WorkflowInstance instance = workflow.instance();
 * instance.start().join();
 *
 * FlowAssertions.assertThat(instance)
 *         .hasEventType(EventType.WORKFLOW_COMPLETED);
 *
 * // equivalent, filtering the whole store manually:
 * FlowAssertions.assertThat()
 *         .filteredByInstanceId(instance.id())
 *         .hasEventType(EventType.WORKFLOW_COMPLETED);
 * }</pre>
 */
public final class FlowAssertions {

    private FlowAssertions() {
    }

    /**
     * Starts an assertion on the CDI-managed {@link WorkflowEventStore}, i.e. the store that
     * {@code @QuarkusFlowTest} clears and that the running application records into.
     *
     * @return a {@link WorkflowEventStoreAssert} wrapping the injected event store
     * @throws IllegalStateException if called outside a running CDI container, or if no
     *         {@link WorkflowEventStore} bean is available
     */
    public static WorkflowEventStoreAssert assertThat() {
        return new WorkflowEventStoreAssert(getWorkflowEventStore());
    }

    /**
     * Starts an assertion on an already-filtered list of events, e.g. one obtained from
     * {@link WorkflowEventStore#getByInstanceId(String)} or one of {@link WorkflowEventStoreAssert}'s
     * {@code filteredByX} methods.
     */
    public static RecordedEventListAssert assertThat(List<RecordedEvent> recordedEvents) {
        return new RecordedEventListAssert(recordedEvents);
    }

    /**
     * Starts an assertion on a single recorded event.
     */
    public static RecordedEventAssert assertThat(RecordedEvent recordedEvent) {
        return new RecordedEventAssert(recordedEvent);
    }

    /**
     * Starts an assertion on the events recorded for the given workflow instance, shorthand for
     * {@code assertThat().filteredByInstanceId(workflowInstance.id())}.
     *
     * @return a {@link RecordedEventListAssert} scoped to {@code workflowInstance}'s events
     * @throws IllegalStateException if called outside a running CDI container, or if no
     *         {@link WorkflowEventStore} bean is available
     */
    public static RecordedEventListAssert assertThat(WorkflowInstance workflowInstance) {
        return new RecordedEventListAssert(getWorkflowEventStore().getByInstanceId(workflowInstance.id()));
    }

    private static WorkflowEventStore getWorkflowEventStore() {
        InjectableInstance<WorkflowEventStore> bean = Arc.container().select(WorkflowEventStore.class);
        if (bean.isResolvable()) {
            return bean.get();
        }
        throw new IllegalStateException("No WorkflowEventStore bean available.");
    }
}