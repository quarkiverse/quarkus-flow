# Quarkus Flow Testing Framework

A comprehensive testing framework for Quarkus Flow workflows that provides event recording, fluent assertions, and synchronization utilities.

## Features

- **Event Recording**: Automatically capture all workflow and task events during test execution
- **Fluent Assertions**: Chainable API for verifying workflow execution
- **Ordered & Unordered Assertions**: Choose between strict sequential verification or flexible existence checks
- **Event Waiting**: Synchronize tests with asynchronous workflow execution
- **Thread-Safe**: Isolated event storage per test thread for parallel execution

## Quick Start

### 1. Setup Event Recording

```java
WorkflowEventStore workflowEventStore = new WorkflowEventStore();

try (WorkflowApplication app = WorkflowApplication.builder()
        .withListener(new TestWorkflowExecutionListener(workflowEventStore))
        .build()) {
    
    // Your workflow execution
    WorkflowDefinition def = app.workflowDefinition(workflow);
    WorkflowInstance instance = def.instance(inputData);
    instance.start().join();
}
```

**Important:** Use `FluentEventAssertions.assertThat(workflowEventStore)` instead of `assertThat(workflowEventStore.getAll())` to enable integrated wait functionality.

### 2. Unordered Assertions (Default)

By default, assertions check if events exist anywhere in the event list, without caring about order:

```java
FluentEventAssertions.assertThat(workflowEventStore.getAll())
    .workflowStarted()           // Just check it started
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
FluentEventAssertions.assertThat(workflowEventStore.getAll())
    .inOrder()                   // Enable strict ordering
    .workflowStarted()           // Must be first
    .taskStarted("task1")        // Must be next
    .taskCompleted("task1")      // Must follow task1 start
    .taskStarted("task2")        // Must be next
    .taskCompleted("task2")      // Must follow task2 start
    .workflowCompleted()         // Must be last
    .assertAll();
```

### 4. Integrated Wait and Assert (Recommended)

Use integrated wait methods for the cleanest API when working with async workflows:

```java
// Start workflow asynchronously
CompletableFuture.runAsync(() -> instance.start().join());

// Wait and assert in a single fluent chain
FluentEventAssertions.assertThat(workflowEventStore)
    .waitForWorkflowStarted()
    .waitForTaskStarted("task1")
    .waitForTaskCompleted("task1")
    .waitForWorkflowCompleted()
    .inOrder()
    .workflowStarted()
    .taskStarted("task1")
    .taskCompleted("task1")
    .workflowCompleted()
    .assertAll();
```

### 5. Standalone Event Waiting (Alternative)

You can also use `EventWaiter` separately if needed:

```java
EventWaiter eventWaiter = new EventWaiter(workflowEventStore);

// Start workflow asynchronously
CompletableFuture.runAsync(() -> instance.start().join());

// Wait for specific events
eventWaiter.taskStarted("task1");
eventWaiter.taskCompleted("task1");
eventWaiter.workflowCompleted();

// Then verify the complete sequence
FluentEventAssertions.assertThat(workflowEventStore.getAll())
    .inOrder()
    .workflowStarted()
    .taskStarted("task1")
    .taskCompleted("task1")
    .workflowCompleted()
    .assertAll();
```

## API Reference

### FluentEventAssertions

#### Creation Methods
- `assertThat(WorkflowEventStore store)` - Create with store (enables wait methods)
- `assertThat(List<RecordedWorkflowEvent> events)` - Create with events list (no wait support)

#### Integrated Wait Methods (requires WorkflowEventStore)
- `waitForWorkflowStarted()` - Wait for workflow to start
- `waitForWorkflowCompleted()` - Wait for workflow to complete
- `waitForWorkflowFailed()` - Wait for workflow to fail
- `waitForTaskStarted(String taskName)` - Wait for task to start
- `waitForTaskCompleted(String taskName)` - Wait for task to complete
- `waitForTaskFailed(String taskName)` - Wait for task to fail
- `waitTimeout(Duration timeout)` - Configure wait timeout (default: 5s)
- `waitPollInterval(Duration interval)` - Configure poll interval (default: 50ms)

#### Workflow Events
- `workflowStarted()` - Assert workflow started event exists
- `workflowCompleted()` - Assert workflow completed event exists
- `workflowCompleted(WorkflowInstance)` - Assert workflow completed for specific instance
- `workflowFailed()` - Assert workflow failed event exists
- `workflowCancelled()` - Assert workflow cancelled event exists
- `workflowSuspended()` - Assert workflow suspended event exists
- `workflowResumed()` - Assert workflow resumed event exists
- `workflowStatusChanged()` - Assert workflow status changed event exists

#### Task Events
- `taskStarted(String taskName)` - Assert task started event exists
- `taskCompleted(String taskName)` - Assert task completed event exists
- `taskFailed(String taskName)` - Assert task failed event exists
- `taskCancelled(String taskName)` - Assert task cancelled event exists
- `taskSuspended(String taskName)` - Assert task suspended event exists
- `taskResumed(String taskName)` - Assert task resumed event exists
- `taskRetried(String taskName)` - Assert task retried event exists

#### Event Counts
- `hasEventCount(int expected)` - Assert total event count
- `hasWorkflowStartedEventCount(int expected)` - Assert workflow started event count
- `hasWorkflowCompletedEventCount(int expected)` - Assert workflow completed event count
- `hasTaskStartedEventCount(int expected)` - Assert task started event count
- `hasTaskCompletedEventCount(int expected)` - Assert task completed event count
- `hasTaskFailedEventCount(int expected)` - Assert task failed event count
- `hasEventTypeCount(EventType type, int expected)` - Assert count for any event type

#### Ordering & Timing
- `inOrder()` - Enable strict sequential verification
- `taskCompletedBefore(String first, String second)` - Assert task completion order
- `workflowCompletedWithin(Duration duration)` - Assert workflow completion time

#### Filtering
- `allEventsForInstance(String instanceId)` - Assert all events belong to instance
- `allEventsForWorkflow(String workflowId)` - Assert all events belong to workflow

#### Output & Error Verification
- `withOutput(Consumer<WorkflowModel> assertion)` - Verify output of last event
- `withError(Consumer<Throwable> assertion)` - Verify error of last event

#### Utilities
- `reset()` - Reset to beginning for re-verification
- `assertAll()` - Execute all accumulated assertions (required at end)

### WorkflowEventStore

#### Retrieving Events
- `getAll()` - Get all recorded events
- `getByType(EventType type)` - Get events by type
- `getByInstanceId(String instanceId)` - Get events for specific instance
- `getEventsForInstance(String instanceId)` - Alias for getByInstanceId
- `getByTaskName(String taskName)` - Get events for specific task
- `getWorkflowEvents()` - Get all workflow-level events
- `getTaskEvents()` - Get all task-level events

#### Management
- `clear()` - Clear all events for current thread
- `size()` - Get event count
- `isEmpty()` - Check if any events recorded
- `remove()` - Remove thread-local storage

### EventWaiter

#### Waiting Methods
- `workflowStarted()` - Wait for workflow to start
- `workflowCompleted()` - Wait for workflow to complete
- `taskStarted(String taskName)` - Wait for task to start
- `taskCompleted(String taskName)` - Wait for task to complete

#### Configuration
- `EventWaiter(WorkflowEventStore store)` - Create with default 30s timeout
- `EventWaiter(WorkflowEventStore store, Duration timeout)` - Create with custom timeout

## Examples

### Example 1: Basic Workflow Test

```java
@Test
void should_complete_simple_workflow() {
    Workflow workflow = FuncWorkflowBuilder.workflow()
            .tasks(FuncDSL.function("increment", n -> n + 1, Long.class))
            .build();

    WorkflowEventStore store = new WorkflowEventStore();

    try (WorkflowApplication app = WorkflowApplication.builder()
            .withListener(new TestWorkflowExecutionListener(store))
            .build()) {

        WorkflowDefinition def = app.workflowDefinition(workflow);
        WorkflowInstance instance = def.instance(10L);
        WorkflowModel result = instance.start().join();

        FluentEventAssertions.assertThat(store.getAll())
                .workflowStarted()
                .taskStarted("increment")
                .taskCompleted("increment")
                .workflowCompleted()
                .assertAll();
    }
}
```

### Example 2: Verify Task Execution Order

```java
@Test
void should_execute_tasks_in_order() {
    Workflow workflow = FuncWorkflowBuilder.workflow()
            .tasks(
                FuncDSL.function("first", n -> n + 1, Long.class),
                FuncDSL.function("second", n -> n * 2, Long.class)
            )
            .build();

    WorkflowEventStore store = new WorkflowEventStore();

    try (WorkflowApplication app = WorkflowApplication.builder()
            .withListener(new TestWorkflowExecutionListener(store))
            .build()) {

        WorkflowDefinition def = app.workflowDefinition(workflow);
        def.instance(5L).start().join();

        FluentEventAssertions.assertThat(store.getAll())
                .inOrder()
                .workflowStarted()
                .taskStarted("first")
                .taskCompleted("first")
                .taskStarted("second")
                .taskCompleted("second")
                .workflowCompleted()
                .assertAll();
    }
}
```

### Example 3: Verify Output

```java
@Test
void should_produce_correct_output() {
    Workflow workflow = FuncWorkflowBuilder.workflow()
            .tasks(FuncDSL.function("double", n -> n * 2, Long.class))
            .build();

    WorkflowEventStore store = new WorkflowEventStore();

    try (WorkflowApplication app = WorkflowApplication.builder()
            .withListener(new TestWorkflowExecutionListener(store))
            .build()) {

        WorkflowDefinition def = app.workflowDefinition(workflow);
        def.instance(5L).start().join();

        FluentEventAssertions.assertThat(store.getAll())
                .inOrder()
                .workflowStarted()
                .taskStarted("double")
                .taskCompleted("double")
                .workflowCompleted()
                .withOutput(output -> {
                    assertThat(output.asLong()).isEqualTo(10L);
                })
                .assertAll();
    }
}
```

### Example 4: Multiple Workflow Instances

```java
@Test
void should_handle_multiple_instances() {
    Workflow workflow = FuncWorkflowBuilder.workflow()
            .tasks(FuncDSL.function("task", n -> n + 1, Long.class))
            .build();

    WorkflowEventStore store = new WorkflowEventStore();

    try (WorkflowApplication app = WorkflowApplication.builder()
            .withListener(new TestWorkflowExecutionListener(store))
            .build()) {

        WorkflowDefinition def = app.workflowDefinition(workflow);
        
        WorkflowInstance instance1 = def.instance(10L);
        instance1.start().join();
        
        WorkflowInstance instance2 = def.instance(20L);
        instance2.start().join();

        // Verify each instance separately
        FluentEventAssertions.assertThat(store.getEventsForInstance(instance1.id()))
                .hasWorkflowStartedEventCount(1)
                .hasWorkflowCompletedEventCount(1)
                .assertAll();

        FluentEventAssertions.assertThat(store.getEventsForInstance(instance2.id()))
                .hasWorkflowStartedEventCount(1)
                .hasWorkflowCompletedEventCount(1)
                .assertAll();
    }
}
```

### Example 5: Integrated Wait and Assert

```java
@Test
void should_wait_and_assert_in_single_chain() {
    Workflow workflow = FuncWorkflowBuilder.workflow()
            .tasks(
                FuncDSL.function("task1", n -> n + 1, Long.class),
                FuncDSL.function("task2", n -> n * 2, Long.class)
            )
            .build();

    WorkflowEventStore store = new WorkflowEventStore();

    try (WorkflowApplication app = WorkflowApplication.builder()
            .withListener(new TestWorkflowExecutionListener(store))
            .build()) {

        WorkflowDefinition def = app.workflowDefinition(workflow);
        WorkflowInstance instance = def.instance(10L);

        // Start workflow asynchronously
        CompletableFuture.runAsync(() -> instance.start().join());

        // Wait for events and assert in one fluent chain
        FluentEventAssertions.assertThat(store)
                .waitForWorkflowStarted()
                .waitForTaskCompleted("task1")
                .waitForTaskCompleted("task2")
                .waitForWorkflowCompleted()
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

## Best Practices

1. **Always call assertAll()**: Assertions are accumulated and only executed when `assertAll()` is called
2. **Use assertThat(store) for async workflows**: Create FluentEventAssertions with WorkflowEventStore to enable integrated wait methods
3. **Use inOrder() when order matters**: Only use strict ordering when the sequence is important
4. **Clear events between tests**: Call `store.clear()` in test cleanup to ensure isolation
5. **Prefer integrated wait methods**: Use `waitForTaskCompleted()` instead of separate EventWaiter for cleaner code
6. **Verify specific instances**: Use `getEventsForInstance()` when testing multiple workflow instances
7. **Check event counts**: Use count assertions to verify expected number of retries, failures, etc.
8. **Configure timeouts appropriately**: Use `waitTimeout()` for slow operations, default is 5 seconds

### Recommended Pattern for Async Workflows

```java
// ✅ GOOD: Integrated wait and assert
FluentEventAssertions.assertThat(store)
    .waitForWorkflowCompleted()
    .workflowStarted()
    .workflowCompleted()
    .assertAll();

// ❌ AVOID: Separate EventWaiter (more verbose)
EventWaiter waiter = new EventWaiter(store);
waiter.workflowCompleted();
FluentEventAssertions.assertThat(store.getAll())
    .workflowCompleted()
    .assertAll();

// ❌ NEVER: Thread.sleep (unreliable)
Thread.sleep(1000);
FluentEventAssertions.assertThat(store.getAll())
    .workflowCompleted()
    .assertAll();
```

## Thread Safety

The framework is designed for parallel test execution:
- `WorkflowEventStore` uses ThreadLocal storage
- Each test thread has isolated event storage
- No shared state between tests
- Safe for JUnit parallel execution

## Migration from hasX() Methods

If you were using the old `hasTaskStarted()`, `hasTaskCompleted()`, etc. methods, you can now use the simpler API:

**Old way (still works):**
```java
.hasTaskStarted("task1")
.hasTaskCompleted("task1")
```

**New way (recommended):**
```java
.taskStarted("task1")      // Unordered by default
.taskCompleted("task1")    // Unordered by default
```

**For ordered assertions:**
```java
.inOrder()
.taskStarted("task1")      // Now enforces order
.taskCompleted("task1")    // Now enforces order
```
