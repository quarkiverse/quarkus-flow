# Resilient Task Orchestrator (Quarkus Flow + Event-Driven Choreography)

A production-ready example demonstrating **resilient, event-driven task orchestration** patterns using Quarkus Flow. This example shows how to build workflows that can handle failures gracefully, resume after interruptions, and maintain consistency between workflow state and external systems.

**Use case**: Build pipeline orchestration with multiple independent tasks (lint, test, build, deploy) that can fail, retry, and resume.

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

### 3. **State Reconciliation**
Before resuming a task, we reconcile workflow state with external state:
```java
reconciliationService.reconcile(taskId);
// Validates: workflow state matches external reality (files, git, etc.)
```

**Why**: Prevents corruption when workflow state and external state diverge.

### 4. **Automatic Retry with Backoff**
Failed tasks automatically retry up to a configured limit:
```java
if (result.status() == FAILED && attempts < MAX_RETRIES) {
    return RETRY;
}
```

**Why**: Handles transient failures without manual intervention.

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
                   +-------------------------+
                   |  CoordinatorWorkflow    |
                   |  (Wait for all tasks)   |
                   +-------------------------+
```

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
# Start a build with default tasks (lint, test, build, deploy)
curl -X POST http://localhost:8080/api/builds/start/my-project

# Or customize the tasks
curl -X POST http://localhost:8080/api/builds/start \
  -H "Content-Type: application/json" \
  -d '{
    "projectName": "my-app",
    "gitRef": "feature/new-feature",
    "tasks": ["lint", "test", "build"]
  }'
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

### Why Event-Driven Choreography?

**Problem**: Monolithic workflows have issues:
- One task failure blocks the entire workflow
- Hard to resume - which step failed?
- Tight coupling between phases

**Solution**: Choreography via events:
- Each task is an independent workflow
- Coordinator emits events, tasks react
- Tasks can fail/retry independently

### How Resume Works

1. **Task fails** → State is persisted with completed phases
2. **Workflow restarts** → TaskWorkflow listens for task event again
3. **Reconciliation** → Check if task can resume safely
4. **Execution** → TaskExecutor skips completed phases (idempotent)
5. **Retry** → Continue from where it left off

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

1. **How to build resilient workflows** that survive failures
2. **Event-driven choreography** vs monolithic orchestration
3. **Idempotent task design** for safe retries
4. **State reconciliation** patterns for resume
5. **Quarkus Flow + Messaging** integration

## 📖 Next Steps

1. **Add persistence**: Replace `TaskStateStore` with a database
2. **Add observability**: Integrate Micrometer metrics
3. **Add human approval gates**: Use newsletter-drafter pattern
4. **Add workflow versioning**: Handle schema changes across restarts
