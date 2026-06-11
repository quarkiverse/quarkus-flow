package io.quarkiverse.flow.testing.assertions;

/**
 * Fluent assertions for comparing the completion order of two tasks.
 */
public interface TaskCompletionOrderAssertions {

    /**
     * Asserts that the subject task completed strictly before {@code otherTask}.
     *
     * @param otherTask the task that must complete after the subject
     * @return the parent {@link WorkflowAssertions} for further chaining
     */
    WorkflowAssertions completedBefore(String otherTask);

    /**
     * Asserts that the subject task completed before or at the same instant as {@code otherTask}.
     *
     * @param otherTask the task that must complete after or at the same time as the subject
     * @return the parent {@link WorkflowAssertions} for further chaining
     */
    WorkflowAssertions completedBeforeOrEqualTo(String otherTask);

    /**
     * Asserts that the subject task completed strictly after {@code otherTask}.
     *
     * @param otherTask the task that must complete before the subject
     * @return the parent {@link WorkflowAssertions} for further chaining
     */
    WorkflowAssertions completedAfter(String otherTask);

    /**
     * Asserts that the subject task completed after or at the same instant as {@code otherTask}.
     *
     * @param otherTask the task that must complete before or at the same time as the subject
     * @return the parent {@link WorkflowAssertions} for further chaining
     */
    WorkflowAssertions completedAfterOrEqualTo(String otherTask);
}
