package io.quarkiverse.flow.testing.assertions;

import java.time.Duration;
import java.util.function.Consumer;

import io.serverlessworkflow.impl.WorkflowModel;

public interface WorkflowAssertions {

    /**
     * Asserts that a task cancelled event exists for the specified task name.
     *
     * @param taskName the expected task name
     * @return {@link WorkflowAssertions} for further assertions
     */
    WorkflowAssertions taskCancelled(String taskName);

    /**
     * Asserts that a task suspended event exists for the specified task name.
     *
     * @param taskName the expected task name
     * @return {@link WorkflowAssertions} for further assertions
     */
    WorkflowAssertions taskSuspended(String taskName);

    /**
     * Asserts that a task resumed event exists for the specified task name.
     *
     * @param taskName the expected task name
     * @return {@link WorkflowAssertions} for further assertions
     */
    WorkflowAssertions taskResumed(String taskName);

    /**
     * Asserts that a task retried event exists for the specified task name.
     *
     * @param taskName the expected task name
     * @return {@link WorkflowAssertions} for further assertions
     */
    WorkflowAssertions taskRetried(String taskName);

    /**
     * Asserts that a task with the name <code>taskName</code> was started.
     *
     * @param taskName the task's name
     * @return {@link WorkflowAssertions} for further assertions
     */
    WorkflowAssertions taskStarted(String taskName);

    /**
     * Asserts that a task with the name <code>taskName</code> was completed.
     *
     * @param taskName the task's name
     * @return {@link WorkflowAssertions} for further assertions
     */
    WorkflowAssertions taskCompleted(String taskName);

    /**
     * Asserts that a task with the name <code>taskName</code> was failed.
     *
     * @param taskName the task's name
     * @return {@link WorkflowAssertions} for further assertions
     */
    WorkflowAssertions taskFailed(String taskName);

    /**
     * Asserts that a workflow started event exists.
     *
     * @return {@link WorkflowAssertions} for further assertions
     */
    WorkflowAssertions workflowStarted();

    /**
     * Asserts that a workflow cancelled event exists.
     *
     * @return {@link WorkflowAssertions} for further assertions
     */
    WorkflowAssertions workflowCancelled();

    /**
     * Asserts that a workflow suspended event exists.
     *
     * @return {@link WorkflowAssertions} for further assertions
     */
    WorkflowAssertions workflowSuspended();

    /**
     * Asserts that a workflow resumed event exists.
     *
     * @return {@link WorkflowAssertions} for further assertions
     */
    WorkflowAssertions workflowResumed();

    /**
     * Asserts that a workflow was completed.
     *
     * @return an instance of {@link WorkflowAssertions}
     */
    WorkflowAssertions workflowCompleted();

    /**
     * Asserts that a workflow was failed.
     *
     * @return an instance of {@link WorkflowAssertions}
     */
    WorkflowAssertions workflowFailed();

    /**
     * Asserts that exactly <code>expected</code> workflow started events were recorded.
     *
     * @param expected the expected number of workflow started events
     * @return an instance of {@link WorkflowAssertions}
     */
    WorkflowAssertions hasWorkflowStartedEventCount(int expected);

    /**
     * Asserts that exactly <code>expected</code> workflow completed events were recorded.
     *
     * @param expected the expected number of workflow completed events
     * @return an instance of {@link WorkflowAssertions}
     */
    WorkflowAssertions hasWorkflowCompletedEventCount(int expected);

    /**
     * Asserts that exactly <code>expected</code> workflow failed events were recorded.
     *
     * @param expected the expected number of workflow failed events
     * @return an instance of {@link WorkflowAssertions}
     */
    WorkflowAssertions hasWorkflowFailedEventCount(int expected);

    /**
     * Asserts that exactly <code>expected</code> workflow canceled events were recorded.
     *
     * @param expected the expected number of workflow canceled events
     * @return an instance of {@link WorkflowAssertions}
     */
    WorkflowAssertions hasWorkflowCanceledEventCount(int expected);

    /**
     * Asserts that exactly <code>expected</code> workflow canceled events were recorded.
     *
     * @param expected the expected number of workflow suspended events
     * @return an instance of {@link WorkflowAssertions}
     */
    WorkflowAssertions hasWorkflowSuspendedEventCount(int expected);

    /**
     * Asserts that exactly <code>expected</code> workflow resumed events were recorded.
     *
     * @param expected the expected number of workflow resumed events
     * @return an instance of {@link WorkflowAssertions}
     */
    WorkflowAssertions hasWorkflowResumedEventCount(int expected);

    /**
     * Entry point for fluent task-completion ordering assertions.
     * Resolves the {@code TASK_COMPLETED} event for the given task and returns a
     * {@link TaskCompletionOrderAssertions} that lets you compare its completion
     * timestamp against another task.
     *
     * <pre>
     * assertions.assertTask("taskA").completedBefore("taskB");
     * assertions.assertTask("taskB").completedAfter("taskA");
     * </pre>
     *
     * @param taskName the task whose completion event is the subject of the assertion
     * @return a {@link TaskCompletionOrderAssertions} scoped to the resolved event
     * @throws AssertionError if no {@code TASK_COMPLETED} event is found for {@code taskName}
     */
    TaskCompletionOrderAssertions assertTask(String taskName);

    /**
     * Asserts that exactly <code>expected</code> task started events were recorded.
     *
     * @param expected the expected number of task started events
     * @return an instance of {@link WorkflowAssertions}
     */
    WorkflowAssertions hasTaskStartedEventCount(int expected);

    /**
     * Asserts that exactly <code>expected</code> task completed events were recorded.
     *
     * @param expected the expected number of task completed events
     * @return an instance of {@link WorkflowAssertions}
     */
    WorkflowAssertions hasTaskCompletedEventCount(int expected);

    WorkflowAssertions allEventsForInstance(String id);

    /**
     * Asserts that the workflow completed within the given <code>duration</code> from
     * the moment the first workflow started event was recorded.
     *
     * @param duration the maximum allowed elapsed time between workflow start and completion
     * @return an instance of {@link WorkflowAssertions}
     */
    WorkflowAssertions workflowCompletedWithin(Duration duration);

    void withOutput(Consumer<WorkflowModel> outputAssertion);

    ConfigurableAssertions configure();

}
