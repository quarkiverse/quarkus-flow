# Quarkus Flow Testing Framework - Implementation Plan

## Executive Summary

This document outlines the detailed implementation plan for creating a `quarkus-flow-testing` module that provides comprehensive testing utilities for Quarkus Flow workflows. The framework will enable developers to assert on workflow lifecycle events, verify execution sequences, and test complex workflow scenarios.

## Design Decisions

Based on project analysis and requirements:

- **Module Location**: Top-level module (sibling to `core`, `messaging`, etc.)
- **Event Recording**: Automatic in test scope (always enabled when module is present)
- **JUnit Support**: JUnit 5 only (aligns with Quarkus ecosystem)
- **Event Storage**: Memory-only (no persistence, test-scoped)
- **Assertion Style**: AssertJ focus (already used throughout the project)
- **Thread Safety**: Full support for parallel test execution
- **Test Isolation**: Events isolated per test method using JUnit 5 extension

## Module Structure

```
quarkus-flow-testing/
├── pom.xml                           # Module POM
├── runtime/
│   ├── pom.xml
│   └── src/
│       ├── main/java/io/quarkiverse/flow/testing/
│       │   ├── WorkflowEventRecorder.java           # Main recorder bean
│       │   ├── TestWorkflowExecutionListener.java   # Listener implementation
│       │   ├── FluentEventAssertions.java           # Fluent assertion API
│       │   ├── EventWaiter.java                     # Async event waiting
│       │   ├── WorkflowEventStore.java              # Thread-safe event storage
│       │   ├── events/                              # Event wrapper classes
│       │   │   ├── RecordedWorkflowEvent.java
│       │   │   ├── RecordedTaskEvent.java
│       │   │   └── EventType.java
│       │   └── junit/
│       │       └── WorkflowTestExtension.java       # JUnit 5 extension
│       └── test/java/                               # Unit tests
├── deployment/
│   ├── pom.xml
│   └── src/
│       ├── main/java/io/quarkiverse/flow/testing/deployment/
│       │   └── TestingProcessor.java                # Auto-registration processor
│       └── test/java/                               # Deployment tests
└── integration-tests/
    ├── pom.xml
    └── src/
        └── test/java/io/quarkiverse/flow/testing/it/
            ├── BasicEventRecordingTest.java
            ├── SequentialTasksTest.java
            ├── ParallelTasksTest.java
            ├── AsyncEventWaitingTest.java
            ├── EventFilteringTest.java
            ├── SuspendResumeTest.java
            ├── CompensationFlowTest.java
            └── ThreadSafetyTest.java
```

## Pre-Implementation Checklist

Before starting implementation, verify the following:

### Environment Setup
- [ ] Latest Quarkus Flow codebase pulled from main branch
- [ ] All existing tests pass: `./mvnw clean install -DskipITs=false`
- [ ] Development environment configured per CLAUDE.md
- [ ] Git branch created: `feature/testing-framework`

### Knowledge Prerequisites
- [ ] Reviewed `WorkflowExecutionListener` interface in core module
- [ ] Reviewed existing structured logging implementation
- [ ] Reviewed serverlessworkflow-impl-core event types
- [ ] Reviewed Quarkus extension development guide
- [ ] Reviewed project's existing test patterns

### Dependencies Verification
- [ ] Confirmed AssertJ version used in project
- [ ] Confirmed JUnit 5 version used in project
- [ ] Confirmed Quarkus version compatibility
- [ ] Verified no conflicting test frameworks in use

### Design Validation
- [ ] Implementation plan reviewed and approved
- [ ] API design validated with sample usage
- [ ] Thread safety approach validated
- [ ] Test isolation strategy confirmed

## Implementation Phases

### Phase 1: Core Infrastructure (Foundation)

#### 1.1 Create Module Structure

**Tasks:**
- Create `quarkus-flow-testing/` directory at project root
- Create `runtime/`, `deployment/`, and `integration-tests/` subdirectories
- Create POM files for all modules
- Add module to parent `pom.xml`

**POM Configuration:**
```xml
<!-- quarkus-flow-testing/pom.xml -->
<parent>
    <groupId>io.quarkiverse.flow</groupId>
    <artifactId>quarkus-flow-parent</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</parent>
<artifactId>quarkus-flow-testing-parent</artifactId>
<packaging>pom</packaging>
<name>Quarkus Flow :: Testing :: Parent</name>

<modules>
    <module>runtime</module>
    <module>deployment</module>
    <module>integration-tests</module>
</modules>
```

**Runtime Dependencies:**
- `quarkus-flow-core` (runtime)
- `io.serverlessworkflow:serverlessworkflow-impl-core`
- `org.assertj:assertj-core`
- `org.junit.jupiter:junit-jupiter-api`
- `io.quarkus:quarkus-arc` (CDI)

**Deployment Dependencies:**
- `quarkus-flow-testing-runtime`
- `quarkus-flow-core-deployment`
- `io.quarkus:quarkus-arc-deployment`

**Acceptance Criteria:**
- [ ] Module structure created
- [ ] All POM files configured correctly
- [ ] Module builds successfully with `./mvnw clean install`
- [ ] Parent POM updated to include new module

**Quality Gate 1:**
- Module builds without errors
- No dependency conflicts
- Follows project POM structure conventions

#### 1.2 Event Storage Infrastructure

**File:** `runtime/src/main/java/io/quarkiverse/flow/testing/WorkflowEventStore.java`

**Purpose:** Thread-safe storage for recorded events with test isolation

**Key Features:**
- Thread-local storage for test isolation
- Concurrent collections for thread safety
- Event ordering preservation
- Efficient filtering and querying

**Implementation Details:**
```java
@ApplicationScoped
public class WorkflowEventStore {
    // Thread-local storage for test isolation
    private final ThreadLocal<List<RecordedWorkflowEvent>> events = 
        ThreadLocal.withInitial(CopyOnWriteArrayList::new);
    
    // Methods:
    // - void record(RecordedWorkflowEvent event)
    // - List<RecordedWorkflowEvent> getAll()
    // - List<RecordedWorkflowEvent> getByType(EventType type)
    // - List<RecordedWorkflowEvent> getByInstanceId(String instanceId)
    // - void clear()
    // - int size()
}
```

**Acceptance Criteria:**
- [ ] Thread-safe event storage implemented
- [ ] Thread-local isolation working
- [ ] Unit tests for concurrent access
- [ ] Unit tests for filtering operations

**Quality Gate 1.2:**
- All unit tests pass
- Thread safety verified with concurrent tests
- Memory leaks checked (ThreadLocal cleanup)

#### 1.3 Event Wrapper Classes

**Files:**
- `runtime/src/main/java/io/quarkiverse/flow/testing/events/RecordedWorkflowEvent.java`
- `runtime/src/main/java/io/quarkiverse/flow/testing/events/RecordedTaskEvent.java`
- `runtime/src/main/java/io/quarkiverse/flow/testing/events/EventType.java`

**Purpose:** Wrap serverlessworkflow events with additional metadata

**RecordedWorkflowEvent Structure:**
```java
public class RecordedWorkflowEvent {
    private final EventType type;
    private final Instant timestamp;
    private final String workflowId;
    private final String instanceId;
    private final Object originalEvent; // WorkflowStartedEvent, etc.
    private final Map<String, Object> metadata;
    
    // Getters, builder pattern
}
```

**EventType Enum:**
```java
public enum EventType {
    WORKFLOW_STARTED,
    WORKFLOW_COMPLETED,
    WORKFLOW_FAILED,
    WORKFLOW_CANCELLED,
    WORKFLOW_SUSPENDED,
    WORKFLOW_RESUMED,
    WORKFLOW_STATUS_CHANGED,
    TASK_STARTED,
    TASK_COMPLETED,
    TASK_FAILED,
    TASK_CANCELLED,
    TASK_SUSPENDED,
    TASK_RESUMED,
    TASK_RETRIED
}
```

**Acceptance Criteria:**
- [ ] Event wrapper classes implemented
- [ ] All event types covered
- [ ] Timestamp capture working
- [ ] Metadata extraction working

**Quality Gate 1.3:**
- All event types mapped correctly
- Immutability enforced
- Null handling tested
- Serialization works (if needed for debugging)

### Phase 2: Listener Implementation

#### 2.1 TestWorkflowExecutionListener

**File:** `runtime/src/main/java/io/quarkiverse/flow/testing/TestWorkflowExecutionListener.java`

**Purpose:** Implements `WorkflowExecutionListener` to capture all workflow events

**Implementation Pattern:**
```java
@ApplicationScoped
@Alternative
@Priority(1) // Ensure it runs first
public class TestWorkflowExecutionListener implements WorkflowExecutionListener {
    
    @Inject
    WorkflowEventStore eventStore;
    
    @Override
    public void onWorkflowStarted(WorkflowStartedEvent event) {
        eventStore.record(RecordedWorkflowEvent.from(event, EventType.WORKFLOW_STARTED));
    }
    
    // Implement all 14 lifecycle methods
}
```

**Key Considerations:**
- Use `@Alternative` with `@Priority` to ensure test listener is active in test scope
- Record events synchronously to maintain ordering
- Extract relevant metadata from each event type
- Handle null values gracefully

**Acceptance Criteria:**
- [ ] All 14 lifecycle methods implemented
- [ ] Events recorded in correct order
- [ ] Metadata extraction working for all event types
- [ ] Unit tests for each event type

**Quality Gate 2.1:**
- All lifecycle methods covered
- Event ordering preserved
- No exceptions thrown on null values
- Performance impact < 5ms per event

#### 2.2 Deployment Processor

**File:** `deployment/src/main/java/io/quarkiverse/flow/testing/deployment/TestingProcessor.java`

**Purpose:** Auto-register test listener in test scope

**Implementation:**
```java
public class TestingProcessor {
    
    @BuildStep(onlyIf = IsTest.class)
    UnremovableBeanBuildItem makeTestListenerUnremovable() {
        return UnremovableBeanBuildItem.beanTypes(
            TestWorkflowExecutionListener.class,
            WorkflowEventStore.class,
            WorkflowEventRecorder.class
        );
    }
    
    @BuildStep(onlyIf = IsTest.class)
    AdditionalBeanBuildItem registerTestBeans() {
        return AdditionalBeanBuildItem.builder()
            .addBeanClass(TestWorkflowExecutionListener.class)
            .addBeanClass(WorkflowEventStore.class)
            .addBeanClass(WorkflowEventRecorder.class)
            .setUnremovable()
            .build();
    }
}
```

**Acceptance Criteria:**
- [ ] Listener auto-registered in test scope only
- [ ] Not active in production builds
- [ ] Integration test verifies auto-registration

**Quality Gate 2.2:**
- Beans only registered in test scope
- Production build unaffected
- No circular dependencies

### Phase 3: Assertion API

#### 3.1 WorkflowEventRecorder (Main API)

**File:** `runtime/src/main/java/io/quarkiverse/flow/testing/WorkflowEventRecorder.java`

**Purpose:** Main entry point for test assertions

**API Design:**
```java
@ApplicationScoped
public class WorkflowEventRecorder {
    
    @Inject
    WorkflowEventStore eventStore;
    
    // Main assertion entry point
    public FluentEventAssertions assertThat() {
        return new FluentEventAssertions(eventStore.getAll());
    }
    
    // Event access
    public List<RecordedWorkflowEvent> getEvents() {
        return eventStore.getAll();
    }
    
    public List<RecordedWorkflowEvent> getEvents(EventType type) {
        return eventStore.getByType(type);
    }
    
    public List<RecordedWorkflowEvent> getEventsForInstance(String instanceId) {
        return eventStore.getByInstanceId(instanceId);
    }
    
    // Async waiting
    public EventWaiter waitFor() {
        return new EventWaiter(eventStore);
    }
    
    // Cleanup (called by JUnit extension)
    public void clear() {
        eventStore.clear();
    }
}
```

**Acceptance Criteria:**
- [ ] Main API implemented
- [ ] CDI injection working
- [ ] Event access methods working
- [ ] Integration with assertion API

**Quality Gate 3.1:**
- API intuitive and discoverable
- CDI injection works in all test scenarios
- Thread-safe for parallel tests

#### 3.2 FluentEventAssertions

**File:** `runtime/src/main/java/io/quarkiverse/flow/testing/FluentEventAssertions.java`

**Purpose:** Fluent API for asserting on event sequences

**API Design:**
```java
public class FluentEventAssertions {
    private final List<RecordedWorkflowEvent> events;
    private int currentIndex = 0;
    
    // Workflow assertions
    public FluentEventAssertions workflowStarted() {
        assertNextEventType(EventType.WORKFLOW_STARTED);
        return this;
    }
    
    public FluentEventAssertions workflowCompleted() {
        assertNextEventType(EventType.WORKFLOW_COMPLETED);
        return this;
    }
    
    public FluentEventAssertions workflowFailed() {
        assertNextEventType(EventType.WORKFLOW_FAILED);
        return this;
    }
    
    // Task assertions
    public FluentEventAssertions taskStarted(String taskName) {
        RecordedWorkflowEvent event = assertNextEventType(EventType.TASK_STARTED);
        assertThat(event.getTaskName()).isEqualTo(taskName);
        return this;
    }
    
    public FluentEventAssertions taskCompleted(String taskName) {
        RecordedWorkflowEvent event = assertNextEventType(EventType.TASK_COMPLETED);
        assertThat(event.getTaskName()).isEqualTo(taskName);
        return this;
    }
    
    // Output assertions
    public FluentEventAssertions withOutput(Consumer<WorkflowModel> outputAssertion) {
        RecordedWorkflowEvent event = events.get(currentIndex - 1);
        outputAssertion.accept(event.getOutput());
        return this;
    }
    
    // Count assertions
    public FluentEventAssertions hasEventCount(int expected) {
        assertThat(events).hasSize(expected);
        return this;
    }
    
    public FluentEventAssertions hasTaskStartedEventCount(int expected) {
        long count = events.stream()
            .filter(e -> e.getType() == EventType.TASK_STARTED)
            .count();
        assertThat(count).isEqualTo(expected);
        return this;
    }
    
    // Timing assertions
    public FluentEventAssertions taskCompletedBefore(String task1, String task2) {
        // Find events and compare timestamps
        return this;
    }
    
    public FluentEventAssertions workflowCompletedWithin(Duration duration) {
        // Check workflow duration
        return this;
    }
}
```

**Acceptance Criteria:**
- [ ] Fluent API implemented
- [ ] All common assertions covered
- [ ] Method chaining working
- [ ] Clear error messages on assertion failures
- [ ] Unit tests for all assertion methods

**Quality Gate 3.2:**
- Error messages are clear and actionable
- API covers 90% of common use cases
- Method names follow AssertJ conventions
- IDE auto-completion works well

#### 3.3 EventWaiter (Async Support)

**File:** `runtime/src/main/java/io/quarkiverse/flow/testing/EventWaiter.java`

**Purpose:** Wait for specific events during async workflow execution

**API Design:**
```java
public class EventWaiter {
    private final WorkflowEventStore eventStore;
    private Duration timeout = Duration.ofSeconds(5);
    
    public EventWaiter timeout(Duration timeout) {
        this.timeout = timeout;
        return this;
    }
    
    public EventWaiter workflowStarted() {
        return waitForEvent(EventType.WORKFLOW_STARTED);
    }
    
    public EventWaiter workflowCompleted() {
        return waitForEvent(EventType.WORKFLOW_COMPLETED);
    }
    
    public EventWaiter taskCompleted(String taskName) {
        return waitForEvent(EventType.TASK_COMPLETED, 
            e -> taskName.equals(e.getTaskName()));
    }
    
    private EventWaiter waitForEvent(EventType type) {
        return waitForEvent(type, e -> true);
    }
    
    private EventWaiter waitForEvent(EventType type, Predicate<RecordedWorkflowEvent> condition) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            Optional<RecordedWorkflowEvent> event = eventStore.getAll().stream()
                .filter(e -> e.getType() == type)
                .filter(condition)
                .findFirst();
            
            if (event.isPresent()) {
                return this;
            }
            
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for event", e);
            }
        }
        
        throw new AssertionError(
            String.format("Timeout waiting for event %s after %s", type, timeout));
    }
}
```

**Acceptance Criteria:**
- [ ] Async waiting implemented
- [ ] Timeout handling working
- [ ] Polling mechanism efficient
- [ ] Clear timeout error messages
- [ ] Integration tests with async workflows

**Quality Gate 3.3:**
- Polling interval optimized (50ms default)
- CPU usage minimal during waiting
- Timeout messages include helpful context
- Interrupt handling correct

### Phase 4: JUnit 5 Integration

#### 4.1 WorkflowTestExtension

**File:** `runtime/src/main/java/io/quarkiverse/flow/testing/junit/WorkflowTestExtension.java`

**Purpose:** JUnit 5 extension for automatic setup/teardown

**Implementation:**
```java
public class WorkflowTestExtension implements BeforeEachCallback, AfterEachCallback {
    
    @Override
    public void beforeEach(ExtensionContext context) {
        // Extension is passive - recorder is auto-injected via CDI
        // Events are automatically recorded via TestWorkflowExecutionListener
    }
    
    @Override
    public void afterEach(ExtensionContext context) {
        // Clear events after each test for isolation
        getRecorder(context).ifPresent(WorkflowEventRecorder::clear);
    }
    
    private Optional<WorkflowEventRecorder> getRecorder(ExtensionContext context) {
        return context.getTestInstance()
            .flatMap(this::findRecorderField);
    }
    
    private Optional<WorkflowEventRecorder> findRecorderField(Object testInstance) {
        // Use reflection to find @Inject WorkflowEventRecorder field
        // Return the recorder instance
    }
}
```

**Usage:**
```java
@QuarkusTest
@ExtendWith(WorkflowTestExtension.class) // Optional - for explicit cleanup
class MyWorkflowTest {
    @Inject
    WorkflowEventRecorder eventRecorder;
    
    @Test
    void test_workflow_execution() {
        // Events automatically cleared after test
    }
}
```

**Acceptance Criteria:**
- [ ] Extension implemented
- [ ] Automatic cleanup working
- [ ] Test isolation verified
- [ ] Works with parallel test execution
- [ ] Integration tests demonstrate usage

**Quality Gate 4.1:**
- Extension works with @QuarkusTest
- Cleanup happens even on test failure
- No memory leaks between tests
- Parallel execution safe

### Phase 5: Integration Tests

#### 5.1 Basic Event Recording Test

**File:** `integration-tests/src/test/java/io/quarkiverse/flow/testing/it/BasicEventRecordingTest.java`

**Purpose:** Verify basic event recording functionality

**Test Scenarios:**
- Simple workflow with single task
- Verify workflow started/completed events
- Verify task started/completed events
- Verify event ordering
- Verify event metadata

#### 5.2 Sequential Tasks Test

**File:** `integration-tests/src/test/java/io/quarkiverse/flow/testing/it/SequentialTasksTest.java`

**Purpose:** Test sequential task execution tracking

**Test Scenarios:**
- Multiple tasks in sequence
- Verify execution order
- Verify task outputs
- Verify timing relationships

#### 5.3 Parallel Tasks Test

**File:** `integration-tests/src/test/java/io/quarkiverse/flow/testing/it/ParallelTasksTest.java`

**Purpose:** Test parallel task execution tracking

**Test Scenarios:**
- Tasks executing in parallel
- Verify all tasks complete
- Verify no ordering assumptions
- Verify concurrent event recording

#### 5.4 Async Event Waiting Test

**File:** `integration-tests/src/test/java/io/quarkiverse/flow/testing/it/AsyncEventWaitingTest.java`

**Purpose:** Test async event waiting functionality

**Test Scenarios:**
- Wait for workflow completion
- Wait for specific task completion
- Timeout handling
- Multiple waits in sequence

#### 5.5 Suspend/Resume Test

**File:** `integration-tests/src/test/java/io/quarkiverse/flow/testing/it/SuspendResumeTest.java`

**Purpose:** Test suspend/resume event tracking

**Test Scenarios:**
- Workflow suspension
- Workflow resumption
- Event sequence verification
- State transition tracking

#### 5.6 Thread Safety Test

**File:** `integration-tests/src/test/java/io/quarkiverse/flow/testing/it/ThreadSafetyTest.java`

**Purpose:** Verify thread-safe event recording

**Test Scenarios:**
- Multiple tests running in parallel
- Event isolation between tests
- No event leakage
- Concurrent workflow execution

**Acceptance Criteria:**
- [ ] All integration tests implemented
- [ ] All tests pass
- [ ] Code coverage > 80%
- [ ] Tests demonstrate all features

**Quality Gate 5:**
- All integration tests pass consistently
- No flaky tests
- Tests run in < 30 seconds total
- Coverage report generated

### Phase 6: Example Integration

#### 6.1 Update Booking Compensation Example

**File:** `examples/booking-compensation/src/test/java/org/acme/BookingCompensationTest.java`

**Purpose:** Demonstrate testing framework in real example

**Test Implementation:**
```java
@QuarkusTest
class BookingCompensationTest {
    
    @Inject
    WorkflowEventRecorder eventRecorder;
    
    @Inject
    BookingWorkflow workflow;
    
    @Test
    void should_complete_booking_successfully() {
        workflow.instance(Map.of("userId", "123"))
            .start()
            .await().indefinitely();
        
        eventRecorder.assertThat()
            .workflowStarted()
            .taskStarted("bookFlight")
            .taskCompleted("bookFlight")
            .taskStarted("bookHotel")
            .taskCompleted("bookHotel")
            .taskStarted("bookCar")
            .taskCompleted("bookCar")
            .workflowCompleted();
    }
    
    @Test
    void should_compensate_on_failure() {
        // Inject failure
        workflow.instance(Map.of("userId", "123", "failHotel", true))
            .start()
            .await().indefinitely();
        
        eventRecorder.assertThat()
            .workflowStarted()
            .taskCompleted("bookFlight")
            .taskFailed("bookHotel")
            .taskStarted("cancelFlight") // Compensation
            .taskCompleted("cancelFlight")
            .workflowFailed();
    }
    
    @Test
    void should_track_compensation_sequence() {
        List<RecordedWorkflowEvent> events = eventRecorder.getEvents();
        
        // Verify compensation tasks run in reverse order
        assertThat(events)
            .extracting(RecordedWorkflowEvent::getTaskName)
            .containsSequence("bookFlight", "bookHotel", "cancelHotel", "cancelFlight");
    }
}
```

**Acceptance Criteria:**
- [ ] Example tests implemented
- [ ] Tests demonstrate key features
- [ ] Tests pass successfully
- [ ] README updated with testing examples

**Quality Gate 6:**
- Example tests are clear and educational
- README includes quick start guide
- Tests cover common patterns

### Phase 7: Documentation

#### 7.1 Testing Guide

**File:** `docs/modules/ROOT/pages/testing.adoc`

**Content Structure:**
1. Introduction to workflow testing
2. Getting started with quarkus-flow-testing
3. Basic event assertions
4. Advanced assertion patterns
5. Async event waiting
6. Testing compensation flows
7. Testing parallel execution
8. Best practices
9. Troubleshooting

**Code Examples:**
- Include examples from integration tests
- Show common testing patterns
- Demonstrate error scenarios

#### 7.2 API Reference

**File:** `docs/modules/ROOT/pages/testing-api.adoc`

**Content:**
- WorkflowEventRecorder API
- FluentEventAssertions API
- EventWaiter API
- Event types reference
- Configuration options

#### 7.3 Update Navigation

**File:** `docs/modules/ROOT/nav.adoc`

Add testing documentation to navigation:
```
* xref:testing.adoc[Testing Workflows]
** xref:testing-api.adoc[Testing API Reference]
```

**Acceptance Criteria:**
- [ ] Testing guide written
- [ ] API reference complete
- [ ] Code examples included
- [ ] Navigation updated
- [ ] Docs build successfully

**Quality Gate 7:**
- Documentation builds without warnings
- All code examples compile
- Links work correctly
- Searchable and well-indexed

### Phase 8: Final Integration

#### 8.1 Update Parent POM

**File:** `pom.xml`

Add module to parent:
```xml
<modules>
    <module>core</module>
    <module>messaging</module>
    <module>langchain4j</module>
    <module>persistence</module>
    <module>durable-kubernetes</module>
    <module>scheduler</module>
    <module>testing</module>  <!-- NEW -->
    <module>bom</module>
    <module>quarkus-platform-checks</module>
</modules>
```

#### 8.2 Update BOM

**File:** `bom/pom.xml`

Add testing module to BOM:
```xml
<dependency>
    <groupId>io.quarkiverse.flow</groupId>
    <artifactId>quarkus-flow-testing</artifactId>
    <version>${project.version}</version>
    <scope>test</scope>
</dependency>
```

#### 8.3 Full Build Validation

Run complete build with integration tests:
```bash
./mvnw clean install -DskipITs=false
```

**Validation Checklist:**
- [ ] All modules build successfully
- [ ] All unit tests pass
- [ ] All integration tests pass
- [ ] No dependency conflicts
- [ ] Documentation builds
- [ ] Examples build and run

**Acceptance Criteria:**
- [ ] Parent POM updated
- [ ] BOM updated
- [ ] Full build passes
- [ ] No regressions in existing tests

**Quality Gate 8:**
- Clean build with no warnings
- All tests pass on CI
- No performance regressions
- Ready for PR

## Testing Strategy

### Unit Tests
- Test each component in isolation
- Mock dependencies where appropriate
- Focus on edge cases and error handling
- Target > 80% code coverage

### Integration Tests
- Test end-to-end workflows
- Use real Quarkus test framework
- Test thread safety and isolation
- Test all assertion patterns
- Test async scenarios

### Example Tests
- Demonstrate real-world usage
- Serve as living documentation
- Cover common testing patterns
- Show best practices

## Success Criteria

### Functional Requirements
- [ ] Event recording works for all 14 lifecycle events
- [ ] Fluent assertion API is intuitive and complete
- [ ] Async event waiting works reliably
- [ ] Thread-safe and test-isolated
- [ ] JUnit 5 integration seamless
- [ ] Auto-registration in test scope works

### Quality Requirements
- [ ] Code coverage > 80%
- [ ] All integration tests pass
- [ ] Documentation complete and accurate
- [ ] Examples demonstrate key features
- [ ] No performance impact on production code

### Project Requirements
- [ ] Follows Quarkus extension patterns
- [ ] Follows project code conventions
- [ ] Full build passes (`./mvnw clean install -DskipITs=false`)
- [ ] No dependency conflicts
- [ ] Compatible with existing modules

## Implementation Timeline

### Phase 1-2: Foundation (Days 1-2)
- Module structure
- Event storage
- Listener implementation

### Phase 3-4: API & Integration (Days 3-4)
- Assertion API
- JUnit 5 extension
- Event waiting

### Phase 5-6: Testing & Examples (Days 5-6)
- Integration tests
- Example updates
- Bug fixes

### Phase 7-8: Documentation & Release (Days 7-8)
- Documentation
- Final validation
- PR preparation

## Quality Gates

Quality gates must be passed before proceeding to the next phase:

### Gate 1: Core Infrastructure Complete
**Criteria:**
- Module builds successfully
- Event storage thread-safe
- Event wrappers complete
- Unit tests pass
- No memory leaks

**Validation:**
```bash
./mvnw clean test -pl testing/integration-tests
```

### Gate 5: Integration Tests Complete
**Criteria:**
- All integration tests pass
- Code coverage > 80%
- No flaky tests
- Tests run efficiently

**Validation:**
```bash
./mvnw clean verify -pl testing/integration-tests
./mvnw jacoco:report -pl testing
```

### Gate 6: Examples Updated
**Criteria:**
- Example tests demonstrate framework
- Tests pass consistently
- README updated
- Clear usage patterns shown

**Validation:**
```bash
./mvnw clean test -pl examples/booking-compensation
```

### Gate 7: Documentation Complete
**Criteria:**
- All documentation written
- Code examples compile
- Links work
- Docs build successfully

**Validation:**
```bash
./mvnw clean install -pl docs
```

### Gate 8: Full Integration
**Criteria:**
- Full build passes
- No regressions
- BOM updated
- Ready for PR

**Validation:**
```bash
./mvnw clean install -DskipITs=false
```

## Rollback Strategy

If critical issues are discovered during implementation:

### Phase 1-2 Issues (Foundation)
**Rollback:** Delete testing module, revert parent POM changes
**Impact:** Minimal - no code dependencies yet
**Recovery Time:** < 1 hour

### Phase 3-4 Issues (API)
**Rollback:** Keep infrastructure, redesign API
**Impact:** Medium - may need API redesign
**Recovery Time:** 1-2 days

### Phase 5-6 Issues (Testing/Examples)
**Rollback:** Fix tests, keep framework
**Impact:** Low - framework code stable
**Recovery Time:** < 1 day

### Phase 7-8 Issues (Documentation/Integration)
**Rollback:** Fix specific issues, keep implementation
**Impact:** Minimal - documentation only
**Recovery Time:** < 4 hours

### Critical Failure Scenarios

**Scenario 1: Thread Safety Issues**
- **Detection:** Integration tests fail intermittently
- **Action:** Review ThreadLocal usage, add synchronization
- **Fallback:** Use single-threaded event store temporarily

**Scenario 2: Performance Impact**
- **Detection:** Workflow execution slows > 10%
- **Action:** Profile listener overhead, optimize recording
- **Fallback:** Make event recording opt-in via annotation

**Scenario 3: CDI Conflicts**
- **Detection:** Bean resolution failures in tests
- **Action:** Review bean priorities and alternatives
- **Fallback:** Use programmatic bean lookup

**Scenario 4: Quarkus Extension Issues**
- **Detection:** Build step failures, deployment errors
- **Action:** Review Quarkus extension guide, check build steps
- **Fallback:** Manual bean registration in tests

## Performance Benchmarks

Expected performance characteristics:

### Event Recording Overhead
- **Target:** < 1ms per event
- **Maximum:** < 5ms per event
- **Measurement:** Microbenchmark with JMH

### Memory Usage
- **Target:** < 1MB per 1000 events
- **Maximum:** < 10MB per 1000 events
- **Measurement:** Memory profiler during integration tests

### Test Execution Time
- **Target:** No impact on test execution time
- **Maximum:** < 5% increase in test execution time
- **Measurement:** Compare test suite before/after

### Startup Time
- **Target:** No impact on Quarkus startup
- **Maximum:** < 100ms increase in test startup
- **Measurement:** Quarkus dev mode startup time

### Benchmark Tests

Create performance benchmark tests:

```java
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class EventRecordingBenchmark {
    
    @Benchmark
    public void recordSingleEvent(Blackhole bh) {
        // Measure event recording overhead
    }
    
    @Benchmark
    public void recordAndQuery1000Events(Blackhole bh) {
        // Measure bulk operations
    }
}
```

## Troubleshooting Guide

### Common Issues and Solutions

#### Issue 1: Events Not Being Recorded

**Symptoms:**
- `eventRecorder.getEvents()` returns empty list
- Assertions fail with "expected event not found"

**Possible Causes:**
1. TestWorkflowExecutionListener not registered
2. Wrong test scope (not using @QuarkusTest)
3. Events cleared prematurely

**Solutions:**
1. Verify deployment processor is active
2. Add @QuarkusTest annotation
3. Check JUnit extension configuration
4. Enable debug logging: `quarkus.log.category."io.quarkiverse.flow.testing".level=DEBUG`

#### Issue 2: Test Isolation Failures

**Symptoms:**
- Events from previous test appear in current test
- Flaky test failures
- Event count mismatches

**Possible Causes:**
1. ThreadLocal not cleared properly
2. Parallel test execution issues
3. JUnit extension not active

**Solutions:**
1. Verify @ExtendWith(WorkflowTestExtension.class) is present
2. Check ThreadLocal cleanup in afterEach
3. Disable parallel execution temporarily: `junit.jupiter.execution.parallel.enabled=false`
4. Add explicit cleanup: `eventRecorder.clear()` in @AfterEach

#### Issue 3: Async Event Waiting Timeouts

**Symptoms:**
- `waitFor().workflowCompleted()` times out
- Events recorded after timeout

**Possible Causes:**
1. Workflow execution slower than expected
2. Timeout too short
3. Event not being recorded

**Solutions:**
1. Increase timeout: `waitFor().timeout(Duration.ofSeconds(30))`
2. Check workflow execution logs
3. Verify listener is recording events
4. Use `eventRecorder.getEvents()` to debug

#### Issue 4: CDI Injection Failures

**Symptoms:**
- `@Inject WorkflowEventRecorder` is null
- NullPointerException in tests

**Possible Causes:**
1. Missing @QuarkusTest annotation
2. Deployment processor not running
3. Bean scope issues

**Solutions:**
1. Add @QuarkusTest to test class
2. Verify testing module in dependencies
3. Check Quarkus version compatibility
4. Review build logs for processor errors

#### Issue 5: Assertion Failures with Unclear Messages

**Symptoms:**
- Assertion fails but message unclear
- Hard to debug event sequence

**Possible Causes:**
1. Complex event sequence
2. Missing event metadata
3. Timing issues

**Solutions:**
1. Print events before assertion: `eventRecorder.getEvents().forEach(System.out::println)`
2. Use debugger to inspect events
3. Add custom assertions with better messages
4. Break down complex assertions

### Debug Logging

Enable detailed logging for troubleshooting:

```properties
# application.properties (test scope)
quarkus.log.category."io.quarkiverse.flow.testing".level=DEBUG
quarkus.log.category."io.quarkiverse.flow".level=DEBUG
quarkus.log.category."io.serverlessworkflow".level=DEBUG
```

### Diagnostic Tools

**Event Dump Utility:**
```java
public class EventDumper {
    public static void dump(WorkflowEventRecorder recorder) {
        List<RecordedWorkflowEvent> events = recorder.getEvents();
        System.out.println("=== Recorded Events ===");
        for (int i = 0; i < events.size(); i++) {
            RecordedWorkflowEvent event = events.get(i);
            System.out.printf("[%d] %s - %s (instance: %s) at %s%n",
                i, event.getType(), event.getTaskName(),
                event.getInstanceId(), event.getTimestamp());
        }
        System.out.println("======================");
    }
}
```

## Migration Guide

### For Projects Without Existing Tests

Simply add the testing module dependency and start writing tests:

```xml
<dependency>
    <groupId>io.quarkiverse.flow</groupId>
    <artifactId>quarkus-flow-testing</artifactId>
    <scope>test</scope>
</dependency>
```

### For Projects With Existing Tests

If you have existing workflow tests using manual verification:

**Before:**
```java
@Test
void testWorkflow() {
    WorkflowInstance instance = workflow.start();
    instance.await().indefinitely();
    
    // Manual verification
    assertTrue(instance.isCompleted());
    assertEquals("expected", instance.getOutput().get("result"));
}
```

**After:**
```java
@Test
void testWorkflow() {
    @Inject
    WorkflowEventRecorder eventRecorder;
    
    WorkflowInstance instance = workflow.start();
    instance.await().indefinitely();
    
    // Event-based verification
    eventRecorder.assertThat()
        .workflowStarted()
        .taskStarted("processData")
        .taskCompleted("processData")
        .workflowCompleted()
        .withOutput(output -> {
            assertThat(output.get("result")).isEqualTo("expected");
        });
}
```

**Migration Steps:**
1. Add testing module dependency
2. Inject WorkflowEventRecorder in test classes
3. Replace manual assertions with event assertions
4. Add event sequence verification
5. Remove redundant manual checks
6. Run tests to verify behavior unchanged

## Maintenance Plan

### Short-term (First 3 Months)

**Goals:**
- Gather user feedback
- Fix bugs and issues
- Improve documentation based on usage

**Activities:**
- Monitor GitHub issues
- Respond to questions
- Create FAQ based on common issues
- Add more examples

### Medium-term (3-12 Months)

**Goals:**
- Enhance API based on feedback
- Add advanced features
- Improve performance

**Activities:**
- Add requested assertion methods
- Optimize event recording
- Add more event types if needed
- Create video tutorials

### Long-term (12+ Months)

**Goals:**
- Keep up with Quarkus updates
- Maintain compatibility
- Evolve with workflow spec

**Activities:**
- Update for new Quarkus versions
- Support new workflow features
- Refactor based on lessons learned
- Consider additional testing utilities

### Version Compatibility

**Quarkus Version Support:**
- Support current Quarkus LTS version
- Support latest Quarkus version
- Deprecate support for versions > 2 years old

**Serverless Workflow Spec:**
- Support current spec version
- Update when spec evolves
- Maintain backward compatibility where possible

### Breaking Changes Policy

**Major Version (X.0.0):**
- API breaking changes allowed
- Require migration guide
- Provide deprecation period

**Minor Version (x.Y.0):**
- New features only
- No breaking changes
- Deprecate old APIs with warnings

**Patch Version (x.y.Z):**
- Bug fixes only
- No API changes
- No new features

## Appendix A: Code Conventions

Follow project conventions from CLAUDE.md:

### Package Structure
```
io.quarkiverse.flow.testing/
├── WorkflowEventRecorder.java      # Main API
├── WorkflowEventStore.java         # Storage
├── TestWorkflowExecutionListener.java  # Listener
├── events/                         # Event types
│   ├── RecordedWorkflowEvent.java
│   ├── RecordedTaskEvent.java
│   └── EventType.java
├── assertions/                     # Assertion API
│   ├── FluentEventAssertions.java
│   └── EventWaiter.java
└── junit/                          # JUnit integration
    └── WorkflowTestExtension.java
```

### Naming Conventions
- Classes: PascalCase
- Methods: camelCase
- Constants: UPPER_SNAKE_CASE
- Packages: lowercase

### Documentation
- All public APIs must have Javadoc
- Include @param and @return tags
- Add usage examples in class-level Javadoc
- Document thread safety guarantees

### Testing
- Test class name: `<ClassName>Test`
- Test method name: `should_<expected_behavior>_when_<condition>`
- Use AssertJ for assertions
- Use @DisplayName for readable test names

## Appendix B: API Design Principles

### Fluent API Design
- Return `this` for method chaining
- Use descriptive method names
- Provide both simple and advanced options
- Follow AssertJ conventions

### Error Messages
- Be specific about what failed
- Include actual vs expected values
- Suggest possible solutions
- Include relevant context

### Thread Safety
- Document thread safety guarantees
- Use immutable objects where possible
- Synchronize only when necessary
- Prefer concurrent collections

### Performance
- Minimize overhead in hot paths
- Use lazy initialization
- Avoid unnecessary object creation
- Profile before optimizing

## Appendix C: Testing Checklist

Before submitting PR, verify:

### Code Quality
- [ ] All code follows project conventions
- [ ] No compiler warnings
- [ ] No SonarQube issues
- [ ] Code coverage > 80%

### Functionality
- [ ] All acceptance criteria met
- [ ] All quality gates passed
- [ ] No regressions in existing tests
- [ ] Examples work as documented

### Documentation
- [ ] All public APIs documented
- [ ] User guide complete
- [ ] API reference complete
- [ ] Examples included

### Testing
- [ ] Unit tests pass
- [ ] Integration tests pass
- [ ] Examples build and run
- [ ] Performance benchmarks acceptable

### Integration
- [ ] Full build passes
- [ ] No dependency conflicts
- [ ] BOM updated
- [ ] Parent POM updated

### Review
- [ ] Self-review completed
- [ ] Code reviewed by peer
- [ ] Documentation reviewed
- [ ] Ready for maintainer review

## Appendix D: Resources

### Internal Resources
- CLAUDE.md - Project conventions
- CONTRIBUTING.md - Contribution guidelines
- examples/booking-compensation/ - Example workflow
- core/runtime/.../structuredlogging/ - Existing listener example

### External Resources
- [Quarkus Extension Guide](https://quarkus.io/guides/writing-extensions)
- [AssertJ Documentation](https://assertj.github.io/doc/)
- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)
- [Serverless Workflow Spec](https://github.com/serverlessworkflow/specification)
- [LangChain4j Agents](https://docs.langchain4j.dev/tutorials/agents)

### Community
- [Quarkus Flow GitHub](https://github.com/quarkiverse/quarkus-flow)
- [Quarkus Flow Discussions](https://github.com/quarkiverse/quarkus-flow/discussions)
- [Quarkiverse Discord](https://discord.gg/quarkus)

## Conclusion

This implementation plan provides a comprehensive roadmap for creating the `quarkus-flow-testing` module. By following this plan phase-by-phase and respecting the quality gates, we will deliver a robust, well-tested, and well-documented testing framework that enhances the developer experience for Quarkus Flow users.

The plan emphasizes:
- **Incremental development** with clear phases
- **Quality gates** to ensure each phase is complete
- **Comprehensive testing** at all levels
- **Clear documentation** for users
- **Risk mitigation** strategies
- **Long-term maintenance** considerations

Success will be measured by:
- Framework adoption in examples and user projects
- Positive user feedback
- Reduced time to write workflow tests
- Improved test reliability and clarity

**Next Action:** Review and approve this plan, then begin Phase 1 implementation.
```bash
cd testing
./mvnw clean test
```

### Gate 2: Listener Implementation Complete
**Criteria:**
- All 14 lifecycle methods implemented
- Events recorded correctly
- Deployment processor working
- Test scope isolation verified

**Validation:**
```bash
./mvnw clean install -pl testing/deployment
```

### Gate 3: Assertion API Complete
**Criteria:**
- Fluent API intuitive
- All common assertions covered
- Async waiting works
- Error messages clear

**Validation:**
- Manual API review
- Unit tests for all assertions
- Sample usage code compiles

### Gate 4: JUnit Integration Complete
**Criteria:**
- Extension works with @QuarkusTest
- Automatic cleanup working
- Parallel execution safe
- No test interference

**Validation:*