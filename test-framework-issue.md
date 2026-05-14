# Test Framework for Workflow Event Assertions

## Problem Statement

Currently, Quarkus Flow provides the `WorkflowExecutionListener` interface for listening to workflow lifecycle events in production code. However, there's no dedicated test framework or module that allows developers to easily assert on these events during integration tests or unit tests.

Developers testing workflows today can only assert on the final workflow result, but cannot easily verify:
- The sequence of events that occurred during execution
- Specific task completions and their outputs
- Workflow state transitions
- Event timing and ordering
- Intermediate workflow states

## Current Testing Approach

Current tests follow this pattern:

```java
@QuarkusTest
class HelloWorkflowTest {
    @Inject
    HelloWorkflow workflow;

    @Test
    void should_produce_hello_message() throws Exception {
        WorkflowModel result = workflow.instance(Map.of())
                .start()
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);

        // Can only assert on final result
        assertThat(result.asMap().orElseThrow().get("message"), is("hello world!"));
    }
}
```

## Proposed Solution

Create a `quarkus-flow-testing` module that provides:

### 1. Test Listener/Recorder
A test-scoped listener that records all workflow events during test execution:

```java
@QuarkusTest
class WorkflowEventTest {
    
    @Inject
    WorkflowEventRecorder eventRecorder; // New test utility
    
    @Inject
    MyWorkflow workflow;
    
    @Test
    void should_complete_all_tasks_in_order() {
        eventRecorder.startRecording(); // or auto-start per test
        
        workflow.instance(Map.of()).start().await().indefinitely();
        
        // Assert on recorded events
        eventRecorder.assertThat()
            .workflowStarted()
            .taskStarted("task1")
            .taskCompleted("task1")
            .taskStarted("task2")
            .taskCompleted("task2")
            .workflowCompleted();
    }
}
```

### 2. Fluent Assertion API
Provide a fluent API for asserting on workflow events:

```java
// Assert on event sequence
eventRecorder.assertThat()
    .hasWorkflowStartedEvent()
    .hasTaskCompletedEvent("inc")
    .withOutput(output -> {
        assertThat(output.asMap().get("count")).isEqualTo(1);
    });

// Assert on event count
eventRecorder.assertThat()
    .hasTaskStartedEventCount(3)
    .hasTaskCompletedEventCount(3);

// Assert on event timing
eventRecorder.assertThat()
    .taskCompletedBefore("task1", "task2")
    .workflowCompletedWithin(Duration.ofSeconds(5));

// Assert on specific event properties
eventRecorder.assertThat()
    .hasTaskFailedEvent("risky-task")
    .withError(error -> {
        assertThat(error.getMessage()).contains("Connection timeout");
    });
```

### 3. Event Matchers
Provide Hamcrest-style matchers for more flexible assertions:

```java
assertThat(eventRecorder.getEvents(), 
    hasEvent(workflowStarted(withWorkflowId("my-workflow"))));

assertThat(eventRecorder.getEvents(),
    hasEvent(taskCompleted("inc", withOutput(containsEntry("count", 1)))));
```

### 4. Conditional Waiting
Support waiting for specific events during async workflow execution:

```java
@Test
void should_suspend_and_resume() {
    workflow.instance(Map.of()).start();
    
    // Wait for specific event before proceeding
    eventRecorder.waitFor()
        .workflowSuspended()
        .timeout(Duration.ofSeconds(5));
    
    // Resume workflow
    workflowService.resume(instanceId);
    
    eventRecorder.waitFor()
        .workflowResumed()
        .workflowCompleted();
}
```

### 5. Event Filtering and Querying
Allow filtering and querying recorded events:

```java
// Get all task events
List<TaskEvent> taskEvents = eventRecorder.getTaskEvents();

// Get events for specific workflow instance
List<WorkflowEvent> instanceEvents = 
    eventRecorder.getEventsForInstance(instanceId);

// Filter by event type
List<TaskCompletedEvent> completedTasks = 
    eventRecorder.getEvents(TaskCompletedEvent.class);
```

## Implementation Considerations

### Module Structure
```
quarkus-flow-testing/
├── runtime/
│   ├── WorkflowEventRecorder.java
│   ├── FluentEventAssertions.java
│   ├── EventMatchers.java
│   └── TestWorkflowExecutionListener.java
├── deployment/
│   └── TestingProcessor.java (auto-register test listener)
└── integration-tests/
    └── (comprehensive test examples)
```

### Key Features
1. **Automatic Registration**: Test listener should be automatically registered in test scope
2. **Thread-Safe**: Support concurrent test execution
3. **Test Isolation**: Events should be isolated per test method
4. **JUnit Integration**: Provide JUnit 5 extension for automatic setup/teardown
5. **AssertJ Integration**: Leverage AssertJ for fluent assertions
6. **Quarkus Test Profile**: Support different test profiles with different listener configurations

### Example Test Scenarios
The framework should support testing:
- Sequential task execution
- Parallel task execution
- Workflow suspension/resumption
- Error handling and retries
- Compensation flows
- Event timing and performance
- State transitions
- Task output validation

## Benefits

1. **Better Test Coverage**: Verify not just the final result, but the entire execution path
2. **Easier Debugging**: Recorded events provide insight into what happened during test failures
3. **Behavior Verification**: Assert on workflow behavior, not just outcomes
4. **Integration Testing**: Better support for testing complex workflow scenarios
5. **Documentation**: Tests become living documentation of workflow behavior

## Related Work

- Existing `WorkflowExecutionListener` interface (production use)
- Current test patterns using `@QuarkusTest` and `@Inject`
- Example listener: `examples/suspend-resume-abort/src/main/java/org/acme/flow/FlowCustomListener.java`

## Acceptance Criteria

- [ ] New `quarkus-flow-testing` module created
- [ ] `WorkflowEventRecorder` bean available in test scope
- [ ] Fluent assertion API implemented
- [ ] Event matchers for common scenarios
- [ ] Async event waiting support
- [ ] Comprehensive documentation with examples
- [ ] Integration tests demonstrating all features
- [ ] JUnit 5 extension for automatic setup
- [ ] Thread-safe and test-isolated implementation

## Open Questions

1. Should this be a separate module or part of the core testing utilities?
2. Should we support both JUnit 4 and JUnit 5, or only JUnit 5?
3. Should event recording be opt-in or automatic in test scope?
4. How should we handle event recording in durable/persistent workflows?
5. Should we provide integration with other testing frameworks (TestNG, Spock)?

## References

- [Custom Listeners Documentation](https://docs.quarkiverse.io/quarkus-flow/dev/custom-listeners.html)
- [WorkflowExecutionListener Interface](https://github.com/serverlessworkflow/sdk-java/blob/main/impl/core/src/main/java/io/serverlessworkflow/impl/lifecycle/WorkflowExecutionListener.java)
- [Current Test Examples](core/integration-tests/src/test/java/io/quarkiverse/flow/it/)
