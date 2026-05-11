# Resilient Task Orchestrator (Quarkus Flow + Event-Driven Choreography)

An educational example demonstrating **event-driven task choreography** patterns using Quarkus Flow. This example shows how to build workflows where independent tasks communicate via events, execute idempotently, and retry on failure.

**Use case**: Build pipeline orchestration with multiple independent tasks (lint, test, build, deploy) that can fail and retry automatically.

> **Note**: This is a learning example that demonstrates foundational patterns. For production use, you would need to add: durable state persistence, exponential backoff, completion tracking, and proper resume logic (see [Limitations](#-limitations) section).

## 🎯 Key Patterns Demonstrated

### 1. **Event-Driven Choreography**
Instead of a single monolithic workflow, we use **coordinating workflows** that communicate via events:
- **CoordinatorWorkflow**: Thin orchestrator that decomposes work and emits task events
- **TaskWorkflow**: Independent workflows that execute tasks and publish completion events

**Why**: Fault isolation - if one task fails, others continue independently.

### 2. **Idempotent Task Execution**
Tasks are designed to be safely re-executed:
```java
// Checks if phase already completed before executing
if (state.isPhaseCompleted("compile")) {
    LOG.info("Phase already completed, skipping");
    return existingResult;
}
```

**Why**: Safe resume after failures without duplicate work.

### 3. **State Reconciliation (Simulated)**
Before resuming a task, we check the persisted task state:
```java
reconciliationService.reconcile(taskId);
// Checks: Can this task be safely resumed? Was it previously failed?
```

**Why**: Demonstrates the reconciliation pattern - in production, this would validate external state like git commits, filesystem artifacts, etc.

**Note**: This example uses simulated reconciliation (checking status fields). Real reconciliation would verify actual external systems (see [Limitations](#-limitations)).

### 4. **Automatic Retry**
Failed tasks automatically retry up to a configured limit:
```java
if (result.status() == FAILED && attempts < MAX_RETRIES) {
    return RETRY;
}
```

**Why**: Handles transient failures without manual intervention.

**Note**: Currently retries happen immediately. Production systems should add exponential backoff (see [Limitations](#-limitations)).

## 🏗 Architecture

```
                   +-------------------------+
                   |  CoordinatorWorkflow    |
                   |  (Thin Orchestrator)    |
                   +-------------------------+
                              |
                              | emit: org.acme.build.task.started
                              v
              +---------------+---------------+
              |               |               |
    +---------v----+ +--------v-----+ +-------v------+
    | TaskWorkflow | | TaskWorkflow | | TaskWorkflow |
    |   (lint)     | |   (test)     | |   (build)    |
    +--------------+ +--------------+ +--------------+
              |               |               |
              | emit: org.acme.build.task.completed
              |               |               |
              +---------------+---------------+
                              |
                              v
                        (consumed by
                     other workflows that
                     need task results)
```

**Note**: Current implementation doesn't track completion in the coordinator. Tasks run independently and emit completion events. See [Limitations](#-limitations) for how to add completion tracking.

### Components

**Workflows** (in `workflow/`):
- `CoordinatorWorkflow`: Orchestrates the pipeline, emits task events
- `TaskWorkflow`: Executes individual tasks with retry and resume logic

**Services** (in `service/`):
- `TaskExecutor`: Simulates task execution with configurable failures
- `TaskStateStore`: Persists task state (in-memory, simulates database)
- `StateReconciliationService`: Validates state consistency before resume

**Models** (in `model/`):
- `BuildSpec`: Input specification for the pipeline
- `BuildTask`: Individual task definition
- `TaskState`: Persisted state supporting resume
- `TaskResult`: Execution result

## 🚀 Quick Start

### Prerequisites

- Java 17+
- Maven 3.8+
- Docker or Podman (for Kafka Dev Services)

### Run the Example

```bash
mvn quarkus:dev
```

Quarkus Dev Services automatically starts Kafka in a container.

### Trigger a Build

```bash
# Option 1: Start a build with default tasks (lint, test, build, deploy)
curl -X POST http://localhost:8080/api/builds/start/my-project

# Option 2: Customize the tasks
curl -X POST http://localhost:8080/api/builds/start \
  -H "Content-Type: application/json" \
  -d '{
    "projectName": "my-app",
    "gitRef": "feature/new-feature",
    "tasks": ["lint", "test", "build"]
  }'

# Response:
# {
#   "buildId": "01ABC123...",
#   "status": "STARTED",
#   "project": "my-app",
#   "tasks": ["lint", "test", "build"]
# }
```

### Check Task Status

```bash
# Get all task statuses
curl http://localhost:8080/api/builds/status | jq
```

## 🧪 Testing

### Run Integration Tests

```bash
mvn clean verify -DskipITs=false
```

### Test Scenarios Covered

1. **Basic Pipeline Execution**: Start a build and verify tasks are created
2. **State Persistence**: Verify task state is persisted with completed phases
3. **Failure and Retry**: Verify failed tasks retry automatically
4. **Idempotent Execution**: Verify phases are not re-executed when already complete

## 🔧 Configuration

In `application.properties`:

```properties
# Task execution behavior
orchestrator.task.failure-rate=0.3     # 30% chance of simulated failure
orchestrator.task.delay-ms=100         # Simulated work duration
```

## 📚 Key Concepts Explained

### ForEach + Emit Pattern

The coordinator uses a powerful pattern to emit individual events for each task:

```java
forEach((Collection<BuildTask> buildTasks) -> buildTasks,
    emitJson("org.acme.build.task.started", BuildTask.class)
        .inputFrom("$item"))
```

**Key insight**: The `forEach` task passes the original input (entire collection) to sub-tasks, while individual items are stored in context variables (default: `$item`). The `.inputFrom("$item")` reads from the context variable instead of the input.

### Why Event-Driven Choreography?

**Problem**: Monolithic workflows have issues:
- One task failure blocks the entire workflow
- Hard to resume - which step failed?
- Tight coupling between phases

**Solution**: Choreography via events:
- Each task is an independent workflow
- Coordinator emits events, tasks react
- Tasks can fail/retry independently

### How Idempotent Execution Works

1. **Task starts** → TaskWorkflow receives `task.started` event
2. **Reconciliation** → Check if task can be safely executed
3. **Phase Execution** → TaskExecutor checks each phase:
   - If phase already completed → Skip (idempotent)
   - If phase not done → Execute and mark as completed
4. **Retry on Failure** → If execution fails, retry from last completed phase
5. **Completion** → Emit `task.completed` event

## 🔍 Comparison with Monolithic Workflow

### Event-Driven (this example)
```java
// Coordinator
workflow("coordinator")
  .tasks(
    decompose(),
    forEach(task -> emit("task.started")),
    listen("task.completed", COUNT)
  )

// Task (separate workflow)
workflow("task")
  .tasks(
    listen("task.started"),
    reconcile(),
    execute(),
    retry_if_failed(),
    emit("task.completed")
  )
```

**Benefits**:
- Fault isolation
- Easy resume (task-level granularity)
- Clean state management
- Tasks can run in parallel

## 🎓 Learning Outcomes

After studying this example, you'll understand:

1. **Event-driven choreography** - How to build workflows that communicate via events
2. **Idempotent task design** - How to make tasks safely re-executable
3. **Phase-level execution** - How to resume from partial completion
4. **Automatic retry patterns** - How to handle transient failures
5. **Quarkus Flow + Messaging** integration - How to use Kafka with workflows

## ⚠️ Limitations

This is a **learning example** that demonstrates foundational patterns. Before using in production, you would need to address:

### Critical Gaps

1. **No Completion Tracking**
   - Coordinator emits task events but doesn't wait for completion
   - No way to know "is the build done?"
   - **Solution**: Add listener for `task.completed` events in coordinator

2. **Resume Not Implemented**
   - The `/resume` endpoint clears state (same as `/start`)
   - State is lost on application restart (in-memory only)
   - **Solution**: Persist state to database, don't clear on resume

3. **No Exponential Backoff**
   - Retries happen immediately without delay
   - Can overwhelm downstream systems
   - **Solution**: Add `Thread.sleep()` with exponential delay (100ms, 200ms, 400ms...)

4. **Simulated State Reconciliation**
   - Only checks status fields, not real external state
   - Doesn't validate git commits, filesystem, etc.
   - **Solution**: Implement actual validation of external systems

### What's Missing for Production

| Feature | Current State | Production Needs |
|---------|--------------|------------------|
| State Persistence | In-memory (ConcurrentHashMap) | Database (PostgreSQL, etc.) |
| Retry Strategy | Fixed 5 attempts, no backoff | Exponential backoff + jitter |
| State Reconciliation | Simulated (string checks) | Real validation (git, files, DB) |
| Completion Tracking | None | Coordinator waits for all tasks |
| Circuit Breaker | None | Prevent cascading failures |
| Dead Letter Queue | None | Failed tasks go to DLQ |
| Observability | Basic logging | Metrics, tracing, dashboards |
| Resume Capability | Broken (clears state) | Load previous state and resume |

### Design Decisions (Intentional Limitations)

These are **intentional** simplifications for learning:

- **In-Memory State**: Makes example easy to run without database setup
- **Simulated Failures**: `failure-rate` config allows testing retry logic
- **No Real External Systems**: Example doesn't require git, Docker, etc.

## 📖 Next Steps

To make this production-ready, implement in this order:

### 1. Fix Critical Issues (High Priority)

**Remove stateStore.clear() from startBuild():**
```java
@POST
@Path("/start")
public Response startBuild(BuildSpec spec) {
    // DON'T clear state - let tasks resume idempotently
    // stateStore.clear(); ← REMOVE THIS LINE
    
    WorkflowInstance instance = coordinatorWorkflow.instance(spec);
    instance.start();
    return Response.accepted()...;
}
```

**Add exponential backoff to retry:**
```java
consume("checkRetry", (TaskExecutionContext ctx) -> {
    int attempt = ctx.result().attemptNumber();
    if (attempt >= MAX_RETRIES) {
        throw new RuntimeException("Max retries exhausted");
    }
    
    // Exponential backoff: 100ms, 200ms, 400ms, 800ms, 1600ms
    long backoffMs = (long) (100 * Math.pow(2, attempt));
    Thread.sleep(backoffMs);
}).then("retryExecute")
```

### 2. Add Persistence (Medium Priority)

**Replace TaskStateStore with JPA:**
```java
@Entity
@Table(name = "task_states")
public class TaskState {
    @Id
    private String taskId;
    
    @Enumerated(EnumType.STRING)
    private TaskStatus status;
    
    @ElementCollection
    @CollectionTable(name = "completed_phases")
    private List<String> completedPhases = new ArrayList<>();
    
    @Version
    private Long version; // Optimistic locking
    
    // ...
}
```

### 3. Add Real State Reconciliation (Medium Priority)

Check actual external state:
```java
public ReconciliationResult reconcile(String taskId) {
    TaskState state = stateStore.get(taskId);
    
    // Validate git commits
    if (state.isPhaseCompleted("git-commit")) {
        String actualCommit = gitService.getLatestCommit();
        if (!state.getExternalState().equals(actualCommit)) {
            return new ReconciliationResult(false, "Git state mismatch");
        }
    }
    
    // Validate build artifacts
    if (state.isPhaseCompleted("build")) {
        if (!Files.exists(Path.of("/builds/" + taskId + "/artifact.jar"))) {
            return new ReconciliationResult(false, "Artifact missing");
        }
    }
    
    return new ReconciliationResult(true, "External state validated");
}
```

### 4. Nice-to-Have Improvements

- **Add observability**: Micrometer metrics, distributed tracing
- **Add circuit breaker**: Resilience4j integration
- **Add dead letter queue**: Route failed tasks after max retries
- **Add human approval gates**: Wait for manual approval before deploy
- **Add workflow versioning**: Handle schema changes across restarts
- **Add API to query build status**: `GET /api/builds/{buildId}/status`
