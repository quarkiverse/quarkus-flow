package io.quarkiverse.flow.testing;

import java.util.List;

import io.quarkiverse.flow.testing.events.RecordedWorkflowEvent;

/**
 * Storage contract for recorded workflow events.
 */
public interface WorkflowEventStorage {

    /**
     * Records a workflow event.
     *
     * @param event the event to record; must not be null
     */
    void record(RecordedWorkflowEvent event);

    /**
     * Returns all recorded events visible to the caller.
     *
     * @return immutable snapshot of all events
     */
    List<RecordedWorkflowEvent> getAll();

    /**
     * Removes all recorded events visible to the caller.
     */
    void clear();

    /**
     * Returns the number of recorded events visible to the caller.
     */
    int size();

    /**
     * Returns {@code true} if no events have been recorded.
     */
    boolean isEmpty();
}
