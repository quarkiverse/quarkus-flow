# Quarkus Flow Testing Framework

A comprehensive testing framework for Quarkus Flow workflows that provides event recording, fluent assertions, and asynchronous event waiting.

## Features

- **Event Recording**: Automatically capture all workflow and task events during test execution
- **Fluent Assertions**: Chainable API for verifying workflow execution
- **Ordered & Unordered Assertions**: Choose between strict sequential verification or flexible existence checks
- **Async Event Waiting**: Separate API for waiting on asynchronous workflow events
- **Polling & Streaming**: Support for both polling (default) and streaming modes (future)
- **Thread-Safe**: Supports both thread-local and shared storage modes

## Architecture

The framework uses **composition** to separate concerns:

- **`FluentEventAssertions`**: Focuses purely on assertions (ordered and unordered)
- **`AsyncFluentEventAssertions`**: Handles waiting for async events before asserting
- **`WorkflowEventStore`**: Stores events with thread-local or shared storage modes

## Quick Start

### 1. Setup Event Recording

For **synchronous workflows** (events recorded in same thread):
```java
WorkflowEventStore store = new WorkflowEventStore(); // Thread-local storage

try (WorkflowApplication app = WorkflowApplication.builder()
        .withListener(new TestWorkflowExecutionListener(store))
        .build()) {
    
    WorkflowDefinition def = app.workflowDefinition(workflow);
    WorkflowInstance instance = def.instance(inputData);
    instance.start().join(); // Synchronous execution
}
```

For **asynchronous workflows** (events recorded in different threads):
```java
WorkflowEventStore store = new WorkflowEventStore(true); // Shared storage for cross-thread access

try (WorkflowApplication app = WorkflowApplication.builder()
        .withListener(new TestWorkflowExecutionListener(store))
        .build()) {
    
    WorkflowDefinition def = app.workflowDefinition(workflow);
    WorkflowInstance instance = def.instance(inputData);
    
    // Start asynchronously
    CompletableFuture.runAsync(() -> instance.start().join());
}
```

### 2. Unordered Assertions (Default)

By default, assertions check if events exist anywhere in the event list:

```java
FluentEventAssertions.assertThat(store)
    .workflowStarted()           // Check it started (anywhere)
    .taskStarted("task1")        // Check task1 started (anywhere)
    .taskStarted("task2")        // Check task2 started (anywhere)
    .taskCompleted("task1")      // Check task1 completed (anywhere)
    .taskCompleted("task2")      // Check task2 completed (anywhere)
    .workflowCompleted()         // Check workflow completed
    .assertAll();                // Execute all assertions
```

### 3. Ordered Assertions with inOrder()

Use `inOrder()` to enforce strict sequential verification:

```java
FluentEventAssertions.assertThat(store)
    .inOrder()                   // Enable strict ordering
    .workflowStarted()           // Must be first
    .taskStarted("task1")        // Must be next
    .taskCompleted("task1")      // Must follow task1 start
    .taskStarted("task2")        // Must be next
    .taskCompleted("task2")      // Must follow task2 start
    .workflowCompleted()         // Must be last
    .assertAll();
```

### 4. Async Workflows: Wait Then Assert (Recommended)

For asynchronous workflows, use `AsyncFluentEventAssertions` to wait for events, then transition to assertions:

```java
// Start workflow asynchronously
CompletableFuture.runAsync(() -> instance.start().join());

// Wait for events, then assert
store.waitFor()
    .workflowStarted()
    .taskStarted("task1")
    .taskCompleted("task1")
    .workflowCompleted()
    .thenAssert()                // Transition to assertions
    .inOrder()
    .workflowStarted()
    .taskStarted("task1")
    .taskCompleted("task1")
    .workflowCompleted()
    .assertAll();
```

### 5. Configure Timeouts and Polling

```java
store.waitFor()
    .timeout(Duration.ofSeconds(10))      // Max wait time
    .pollInterval(Duration.ofMillis(50))  // Check interval
    .workflowCompleted()
    .thenAssert()
    .workflowCompleted()
    .assertAll();
```

### 6. Polling vs Streaming Modes

```java
// Polling mode (default) - periodically checks for events
store.waitFor()
    .polling()
    .pollInterval(Duration.ofMillis(10))
    .workflowCompleted()
    .thenAssert()
    .workflowCompleted()
    .assertAll();

// Streaming mode (future enhancement) - listens to events as they arrive
store.waitFor()
    .streaming()  // Currently falls back to polling
    .workflowCompleted()
    .thenAssert()
    .workflowCompleted()
    .assertAll();
```

## API Reference

### WorkflowEventStore

**Creation:**
```java
new WorkflowEventStore()           // Thread-local storage (default)
new WorkflowEventStore(true)       // Shared storage for async workflows
```

**Methods:**
- `record(RecordedWorkflowEvent)` - Record an event
- `getAll()` - Get all recorded events
- `getByType(EventType)` - Filter by event type
- `getByInstanceId(String)` - Filter by instance ID
- `getByTaskName(String)` - Filter by task name
- `clear()` - Clear all events
- `size()` - Get event count
- `waitFor()` - Create AsyncFluentEventAssertions for waiting

### AsyncFluentEventAssertions

**Entry Point:**
```java
store.waitFor()  // Returns AsyncFluentEventAssertions
```

**Configuration:**
- `timeout(Duration)` - Set max wait time (default: 5s)
- `pollInterval(Duration)` - Set poll interval (default: 50ms)
- `polling()` - Use polling mode (default)
- `streaming()` - Use streaming mode (future)

**Wait Methods:**
- `workflowStarted()` - Wait for workflow start
- `workflowCompleted()` - Wait for workflow completion
- `workflowFailed()` - Wait for workflow failure
- `taskStarted(String)` - Wait for task start
- `taskCompleted(String)` - Wait for task completion
- `taskFailed(String)` - Wait for task failure
- `eventOfType(EventType)` - Wait for specific event type
- `eventMatching(Predicate)` - Wait for custom condition

**Transition:**
- `thenAssert()` - Transition to FluentEventAssertions
- `assertThat()` - Alias for thenAssert()

### FluentEventAssertions

**Creation:**
```java
FluentEventAssertions.assertThat(store)           // From store
FluentEventAssertions.assertThat(events)          // From event list
```

**Mode:**
- `inOrder()` - Enable strict sequential verification

**Workflow Assertions:**
- `workflowStarted()` - Assert workflow started
- `workflowCompleted()` - Assert workflow completed
- `workflowFailed()` - Assert workflow failed
- `workflowCancelled()` - Assert workflow cancelled
- `workflowSuspended()` - Assert workflow suspended
- `workflowResumed()` - Assert workflow resumed

**Task Assertions:**
- `taskStarted(String)` - Assert task started
- `taskCompleted(String)` - Assert task completed
- `taskFailed(String)` - Assert task failed
- `taskCancelled(String)` - Assert task cancelled
- `taskSuspended(String)` - Assert task suspended
- `taskResumed(String)` - Assert task resumed

**Output/Error Assertions:**
- `withOutput(Consumer<WorkflowModel>)` - Assert on output
- `withError(Consumer<Throwable>)` - Assert on error

**Count Assertions:**
- `hasEventCount(int)` - Assert total event count
- `hasWorkflowStartedEventCount(int)` - Assert workflow started count
- `hasTaskCompletedEventCount(int)` - Assert task completed count

**Timing Assertions:**
- `taskCompletedBefore(String, String)` - Assert task order
- `workflowCompletedWithin(Duration)` - Assert execution time

**Execution:**
- `assertAll()` - Execute all assertions (required!)

## Complete Examples

### Example 1: Simple Synchronous Workflow

```java
@Test
void should_execute_simple_workflow() {
    Workflow workflow = FuncWorkflowBuilder.workflow()
        .tasks(FuncDSL.function("task1", (n) -> n + 1, Long.class))
        .build();

    WorkflowEventStore store = new WorkflowEventStore();

    try (WorkflowApplication app = WorkflowApplication.builder()
            .withListener(new TestWorkflowExecutionListener(store))
            .build()) {

        WorkflowDefinition def = app.workflowDefinition(workflow);
        WorkflowInstance instance = def.instance(10L);
        instance.start().join();

        FluentEventAssertions.assertThat(store)
            .inOrder()
            .workflowStarted()
            .taskStarted("task1")
            .taskCompleted("task1")
            .workflowCompleted()
            .assertAll();
    }
}
```

### Example 2: Asynchronous Workflow with Waiting

```java
@Test
void should_wait_for_async_workflow() {
    Workflow workflow = FuncWorkflowBuilder.workflow()
        .tasks(
            FuncDSL.function("task1", (n) -> n + 1, Long.class),
            FuncDSL.function("task2", (n) -> n * 2, Long.class)
        )
        .build();

    WorkflowEventStore store = new WorkflowEventStore(true); // Shared storage

    try (WorkflowApplication app = WorkflowApplication.builder()
            .withListener(new TestWorkflowExecutionListener(store))
            .build()) {

        WorkflowDefinition def = app.workflowDefinition(workflow);
        WorkflowInstance instance = def.instance(10L);

        // Start asynchronously
        CompletableFuture.runAsync(() -> instance.start().join());

        // Wait and assert
        store.waitFor()
            .workflowStarted()
            .taskCompleted("task1")
            .taskCompleted("task2")
            .workflowCompleted()
            .thenAssert()
            .inOrder()
            .workflowStarted()
            .taskStarted("task1")
            .taskCompleted("task1")
            .taskStarted("task2")
            .taskCompleted("task2")
            .workflowCompleted()
            .assertAll();
    }
}
```

### Example 3: Unordered Assertions

```java
@Test
void should_verify_all_tasks_ran() {
    // ... workflow setup ...

    FluentEventAssertions.assertThat(store)
        .taskStarted("taskA")
        .taskStarted("taskB")
        .taskStarted("taskC")
        .taskCompleted("taskA")
        .taskCompleted("taskB")
        .taskCompleted("taskC")
        .assertAll();
}
```

### Example 4: Output Verification

```java
@Test
void should_verify_output() {
    // ... workflow setup and execution ...

    store.waitFor()
        .workflowCompleted()
        .thenAssert()
        .workflowCompleted()
        .withOutput(output -> {
            assertThat(output.asNumber()).hasValue(42L);
        })
        .assertAll();
}
```

### Example 5: Custom Timeout

```java
@Test
void should_handle_slow_workflow() {
    // ... workflow setup ...

    store.waitFor()
        .timeout(Duration.ofSeconds(30))
        .pollInterval(Duration.ofMillis(100))
        .workflowCompleted()
        .thenAssert()
        .workflowCompleted()
        .assertAll();
}
```

## Best Practices

1. **Use Shared Storage for Async**: Always use `new WorkflowEventStore(true)` when testing asynchronous workflows
2. **Wait Before Assert**: Use `store.waitFor()...thenAssert()` pattern for async workflows
3. **Choose the Right Mode**: Use `inOrder()` when sequence matters, default mode when it doesn't
4. **Always Call assertAll()**: Assertions are collected and executed only when `assertAll()` is called
5. **Configure Timeouts**: Adjust timeouts based on your workflow's expected execution time
6. **Clean Up**: Call `store.clear()` between tests if reusing the same store

## Migration from Old API

If you were using the old integrated wait methods in FluentEventAssertions:

**Old API:**
```java
FluentEventAssertions.assertThat(store)
    .waitForWorkflowStarted()
    .waitForTaskCompleted("task1")
    .workflowStarted()
    .taskCompleted("task1")
    .assertAll();
```

**New API:**
```java
store.waitFor()
    .workflowStarted()
    .taskCompleted("task1")
    .thenAssert()
    .workflowStarted()
    .taskCompleted("task1")
    .assertAll();
```

## Troubleshooting

### Timeout Errors

If you see "Timeout waiting for event" errors:
1. Ensure you're using shared storage: `new WorkflowEventStore(true)`
2. Increase timeout: `.timeout(Duration.ofSeconds(30))`
3. Check that the workflow is actually running asynchronously
4. Verify the listener is properly registered

### Events Not Found

If assertions fail with "event not found":
1. Check that `TestWorkflowExecutionListener` is registered
2. Verify the workflow actually executed
3. Use `store.getAll()` to inspect recorded events
4. Ensure you're using the correct event store instance

### Thread Safety Issues

If you see inconsistent results in parallel tests:
1. Use thread-local storage (default) for isolated tests
2. Use shared storage only when needed for async workflows
3. Call `store.clear()` between tests
4. Avoid sharing WorkflowEventStore instances between tests