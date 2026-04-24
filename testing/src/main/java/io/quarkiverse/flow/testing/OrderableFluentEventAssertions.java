package io.quarkiverse.flow.testing;

/**
 * Builder interface returned by {@code assertThat()} that allows configuring assertion mode
 * and starting assertions.
 * <p>
 * This interface provides ONLY:
 * <ul>
 * <li>{@code inOrder()} - Enable strict ordering mode before assertions</li>
 * <li>Assertion methods - Start making assertions (returns {@link FluentEventAssertions})</li>
 * <li>{@code forInstance()} - Filter by instance ID before assertions</li>
 * </ul>
 * <p>
 * Once an assertion method is called, the return type changes to {@link FluentEventAssertions},
 * which provides additional utility methods like {@code hasEventCount()}, {@code taskCompletedBefore()}, etc.
 * <p>
 * Valid usage patterns:
 *
 * <pre>
 * // ✅ With ordering
 * assertThat(store).inOrder().workflowStarted().hasEventCount(5).assertAll();
 *
 * // ✅ Without ordering
 * assertThat(store).workflowStarted().taskCompletedBefore("a", "b").assertAll();
 *
 * // ✅ With filtering then ordering
 * assertThat(store).forInstance(id).inOrder().workflowStarted().assertAll();
 * </pre>
 *
 * @see FluentEventAssertions
 * @see AsyncFluentEventAssertions
 */
public interface OrderableFluentEventAssertions {

    /**
     * Enables strict ordering mode. When enabled, all subsequent assertions will verify
     * that events occur in the exact order specified. When disabled (default), assertions
     * only verify that events exist somewhere in the event list.
     * <p>
     * This method must be called immediately after {@code assertThat()} or {@code forInstance()}
     * - before any assertion methods.
     *
     * @return FluentEventAssertions for making ordered assertions
     */
    FluentEventAssertions inOrder();

    /**
     * Filters events to only include those for the specified workflow instance.
     * This is useful when testing multiple workflow instances, and you want to assert
     * on events from a specific instance.
     *
     * @param instanceId the workflow instance ID to filter by
     * @return OrderableFluentEventAssertions to allow inOrder() after filtering
     */
    OrderableFluentEventAssertions forInstance(String instanceId);

    // Workflow Event Assertions - return FluentEventAssertions (with utility methods)

    /**
     * Asserts that a workflow started event exists.
     *
     * @return FluentEventAssertions for further assertions and utility methods
     */
    FluentEventAssertions workflowStarted();

    /**
     * Asserts that a workflow completed event exists.
     *
     * @return FluentEventAssertions for further assertions and utility methods
     */
    FluentEventAssertions workflowCompleted();

    /**
     * Asserts that a workflow failed event exists.
     *
     * @return FluentEventAssertions for further assertions and utility methods
     */
    FluentEventAssertions workflowFailed();

    /**
     * Asserts that a workflow cancelled event exists.
     *
     * @return FluentEventAssertions for further assertions and utility methods
     */
    FluentEventAssertions workflowCancelled();

    /**
     * Asserts that a workflow suspended event exists.
     *
     * @return FluentEventAssertions for further assertions and utility methods
     */
    FluentEventAssertions workflowSuspended();

    /**
     * Asserts that a workflow resumed event exists.
     *
     * @return FluentEventAssertions for further assertions and utility methods
     */
    FluentEventAssertions workflowResumed();

    // Task Event Assertions - return FluentEventAssertions (with utility methods)

    /**
     * Asserts that a task started event exists for the specified task name.
     *
     * @param taskName the expected task name
     * @return FluentEventAssertions for further assertions and utility methods
     */
    FluentEventAssertions taskStarted(String taskName);

    /**
     * Asserts that a task completed event exists for the specified task name.
     *
     * @param taskName the expected task name
     * @return FluentEventAssertions for further assertions and utility methods
     */
    FluentEventAssertions taskCompleted(String taskName);

    /**
     * Asserts that a task failed event exists for the specified task name.
     *
     * @param taskName the expected task name
     * @return FluentEventAssertions for further assertions and utility methods
     */
    FluentEventAssertions taskFailed(String taskName);

    /**
     * Asserts that a task cancelled event exists for the specified task name.
     *
     * @param taskName the expected task name
     * @return FluentEventAssertions for further assertions and utility methods
     */
    FluentEventAssertions taskCancelled(String taskName);

    /**
     * Asserts that a task suspended event exists for the specified task name.
     *
     * @param taskName the expected task name
     * @return FluentEventAssertions for further assertions and utility methods
     */
    FluentEventAssertions taskSuspended(String taskName);

    /**
     * Asserts that a task resumed event exists for the specified task name.
     *
     * @param taskName the expected task name
     * @return FluentEventAssertions for further assertions and utility methods
     */
    FluentEventAssertions taskResumed(String taskName);

    /**
     * Asserts that a task retried event exists for the specified task name.
     *
     * @param taskName the expected task name
     * @return FluentEventAssertions for further assertions and utility methods
     */
    FluentEventAssertions taskRetried(String taskName);

    /**
     * Executes all collected assertions.
     * This must be called to actually perform the assertions.
     * Can be called directly on OrderableFluentEventAssertions if no assertions were made yet.
     */
    void assertAll();

    /**
     * Resets the current index to allow re-verification of events from the beginning.
     *
     * @return OrderableFluentEventAssertions for method chaining
     */
    OrderableFluentEventAssertions reset();

    /**
     * Returns the current verification index.
     *
     * @return the current index
     */
    int getCurrentIndex();

    /**
     * Returns the total number of events being verified.
     *
     * @return the event count
     */
    int getEventCount();
}