package io.quarkiverse.flow.testing;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.quarkiverse.flow.testing.events.RecordedWorkflowEvent;

public class DefaultWorkflowEventStorage implements WorkflowEventStorage {

    private final List<RecordedWorkflowEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public void record(RecordedWorkflowEvent event) {
        events.add(event);
    }

    @Override
    public List<RecordedWorkflowEvent> getAll() {
        return Collections.unmodifiableList(events);
    }

    @Override
    public void clear() {
        events.clear();
    }

    @Override
    public int size() {
        return events.size();
    }

    @Override
    public boolean isEmpty() {
        return events.isEmpty();
    }
}
