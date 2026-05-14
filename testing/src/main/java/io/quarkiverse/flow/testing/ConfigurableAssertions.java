package io.quarkiverse.flow.testing;

/**
 * Configurable assertions interface that allows setting up assertion behavior
 * before executing assertions. After configuration, use assertion methods to
 * start making assertions which return FluentEventAssertions.
 */
public interface ConfigurableAssertions {

    /**
     * Enables strict ordering mode for assertions.
     *
     * @return this for method chaining
     */
    ConfigurableAssertions inOrder();

    /**
     * Filters events to only include those for the specified workflow instance.
     *
     * @param instanceID the workflow instance ID to filter by
     * @return this for method chaining
     */
    ConfigurableAssertions forInstance(String instanceID);

    // Workflow Event Assertions - return FluentEventAssertions to start assertions

    /**
     * Asserts that a workflow started event exists.
     *
     * @return FluentEventAssertions for further assertions
     */
    FluentEventAssertions workflowStarted();

    /**
     * Asserts that a workflow completed event exists.
     *
     * @return FluentEventAssertions for further assertions
     */
    FluentEventAssertions workflowCompleted();

    /**
     * Asserts that a workflow failed event exists.
     *
     * @return FluentEventAssertions for further assertions
     */
    FluentEventAssertions workflowFailed();

    /**
     * Asserts that a workflow cancelled event exists.
     *
     * @return FluentEventAssertions for further assertions
     */
    FluentEventAssertions workflowCancelled();

    /**
     * Asserts that a workflow suspended event exists.
     *
     * @return FluentEventAssertions for further assertions
     */
    FluentEventAssertions workflowSuspended();

    /**
     * Asserts that a workflow resumed event exists.
     *
     * @return FluentEventAssertions for further assertions
     */
    FluentEventAssertions workflowResumed();

    // Task Event Assertions - return FluentEventAssertions to start assertions

    /**
     * Asserts that a task started event exists for the specified task name.
     *
     * @param taskName the expected task name
     * @return FluentEventAssertions for further assertions
     */
    FluentEventAssertions taskStarted(String taskName);

    /**
     * Asserts that a task completed event exists for the specified task name.
     *
     * @param taskName the expected task name
     * @return FluentEventAssertions for further assertions
     */
    FluentEventAssertions taskCompleted(String taskName);

    /**
     * Asserts that a task failed event exists for the specified task name.
     *
     * @param taskName the expected task name
     * @return FluentEventAssertions for further assertions
     */
    FluentEventAssertions taskFailed(String taskName);

    /**
     * Asserts that a task cancelled event exists for the specified task name.
     *
     * @param taskName the expected task name
     * @return FluentEventAssertions for further assertions
     */
    FluentEventAssertions taskCancelled(String taskName);

    /**
     * Asserts that a task suspended event exists for the specified task name.
     *
     * @param taskName the expected task name
     * @return FluentEventAssertions for further assertions
     */
    FluentEventAssertions taskSuspended(String taskName);

    /**
     * Asserts that a task resumed event exists for the specified task name.
     *
     * @param taskName the expected task name
     * @return FluentEventAssertions for further assertions
     */
    FluentEventAssertions taskResumed(String taskName);

    /**
     * Asserts that a task retried event exists for the specified task name.
     *
     * @param taskName the expected task name
     * @return FluentEventAssertions for further assertions
     */
    FluentEventAssertions taskRetried(String taskName);

    /**
     * Executes all collected assertions.
     */
    void assertAll();

    /**
     * Resets the current index to allow re-verification of events from the beginning.
     *
     * @return this for method chaining
     */
    ConfigurableAssertions reset();
}