package io.quarkiverse.flow.testing;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import io.quarkiverse.flow.testing.events.RecordedWorkflowEvent;

class DefaultConfigurableAssertions implements ConfigurableAssertions {

    private final List<RecordedWorkflowEvent> events;
    private boolean strictlyInOrder;
    private String instanceID;
    private FluentEventAssertions delegate;

    public DefaultConfigurableAssertions(List<RecordedWorkflowEvent> events) {
        this.events = Collections.unmodifiableList(events);
        this.strictlyInOrder = false;
        this.instanceID = null;
    }

    @Override
    public ConfigurableAssertions inOrder() {
        this.strictlyInOrder = true;
        return this;
    }

    @Override
    public ConfigurableAssertions forInstance(String instanceID) {
        this.instanceID = Objects.requireNonNull(instanceID, "instanceID must not be null");
        return this;
    }

    private FluentEventAssertions getDelegate() {
        if (delegate == null) {
            List<RecordedWorkflowEvent> filteredEvents = events;

            // Apply instance filter if specified
            if (instanceID != null) {
                filteredEvents = events.stream()
                        .filter(e -> instanceID.equals(e.getInstanceId()))
                        .collect(java.util.stream.Collectors.toList());
            }

            delegate = new FluentEventAssertions(filteredEvents);

            // Apply ordering mode if specified
            if (strictlyInOrder) {
                delegate.inOrder();
            }
        }
        return delegate;
    }

    @Override
    public FluentEventAssertions workflowStarted() {
        return getDelegate().workflowStarted();
    }

    @Override
    public FluentEventAssertions workflowCompleted() {
        return getDelegate().workflowCompleted();
    }

    @Override
    public FluentEventAssertions workflowFailed() {
        return getDelegate().workflowFailed();
    }

    @Override
    public FluentEventAssertions workflowCancelled() {
        return getDelegate().workflowCancelled();
    }

    @Override
    public FluentEventAssertions workflowSuspended() {
        return getDelegate().workflowSuspended();
    }

    @Override
    public FluentEventAssertions workflowResumed() {
        return getDelegate().workflowResumed();
    }

    @Override
    public FluentEventAssertions taskStarted(String taskName) {
        return getDelegate().taskStarted(taskName);
    }

    @Override
    public FluentEventAssertions taskCompleted(String taskName) {
        return getDelegate().taskCompleted(taskName);
    }

    @Override
    public FluentEventAssertions taskFailed(String taskName) {
        return getDelegate().taskFailed(taskName);
    }

    @Override
    public FluentEventAssertions taskCancelled(String taskName) {
        return getDelegate().taskCancelled(taskName);
    }

    @Override
    public FluentEventAssertions taskSuspended(String taskName) {
        return getDelegate().taskSuspended(taskName);
    }

    @Override
    public FluentEventAssertions taskResumed(String taskName) {
        return getDelegate().taskResumed(taskName);
    }

    @Override
    public FluentEventAssertions taskRetried(String taskName) {
        return getDelegate().taskRetried(taskName);
    }

    @Override
    public void assertAll() {
        if (delegate != null) {
            delegate.assertAll();
        }
    }

    public ConfigurableAssertions reset() {
        if (delegate != null) {
            delegate.reset();
        }
        return this;
    }

    public int getCurrentIndex() {
        return delegate != null ? delegate.getCurrentIndex() : 0;
    }

    public int getEventCount() {
        return events.size();
    }
}
