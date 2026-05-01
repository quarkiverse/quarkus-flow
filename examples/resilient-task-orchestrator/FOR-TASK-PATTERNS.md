# ForEach Task Patterns in Quarkus Flow

This document explains the different patterns for using `forEach` (for tasks) in Quarkus Flow workflows, based on learnings from the resilient-task-orchestrator example.

## Table of Contents
1. [Basic Concepts](#basic-concepts)
2. [forEach Signatures](#foreach-signatures)
3. [Common Pattern: ForEach + Emit](#common-pattern-foreach--emit)
4. [Variable Access](#variable-access)
5. [Troubleshooting](#troubleshooting)

---

## Basic Concepts

The `for` task in Serverless Workflow iterates over a collection and executes sub-tasks for each item.

**Key Components:**
- **Collection**: The data to iterate over (`.collection()` or `.in()`)
- **Each**: The variable name for the current item (`.each()`)
- **Tasks**: The sub-tasks to execute for each iteration (`.tasks()`)

**Important:** The individual item is stored in a **context variable** (named via `.each()`), but the **input** passed to sub-tasks is still the original collection. This is critical when using tasks like `emit` that serialize the input.

---

## forEach Signatures

### 1. Function-Based (Cleanest for Dynamic Collections)

```java
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.forEach;

forEach(
    (Collection<BuildTask> tasks) -> tasks,  // Collection function
    emitJson("org.acme.build.task.started", BuildTask.class)
        .inputFrom("$item")                   // Read from variable!
)
```

**When to use:** When the collection comes from previous workflow steps.

### 2. Builder-Style (Most Explicit)

```java
list -> list.forEach("emitTasks", f -> f
    .collection((Collection<BuildTask> tasks) -> tasks)
    .each("task")  // Variable name
    .tasks(
        emitJson("org.acme.build.task.started", BuildTask.class)
            .inputFrom("$task")  // Must match .each() name!
    )
)
```

**When to use:** When you need full control over naming and configuration.

### 3. Static Collection

```java
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.forEach;

List<String> items = List.of("item1", "item2", "item3");

forEach(
    items,  // Static collection
    emitJson("org.acme.event.type", String.class)
        .inputFrom("$item")
)
```

**When to use:** When iterating over a fixed list.

---

## Common Pattern: ForEach + Emit

### Problem: Array Deserialization Error

```
Cannot deserialize value of type `BuildTask` from Array value (token `JsonToken.START_ARRAY`)
```

**Cause:** The `forEach` executor passes the **original input** (whole collection) to sub-tasks, not the individual item. The individual item is in a **context variable**.

### ❌ Incorrect Pattern

```java
.forEach("emitTasks", f -> f
    .collection((Collection<BuildTask> tasks) -> tasks)
    .each("task")
    .tasks(
        emitJson("org.acme.build.task.started", BuildTask.class)
        // Missing .inputFrom()! Will try to deserialize the array!
    )
)
```

### ✅ Correct Pattern

```java
.forEach("emitTasks", f -> f
    .collection((Collection<BuildTask> tasks) -> tasks)
    .each("task")  // Creates variable "$task"
    .tasks(
        emitJson("org.acme.build.task.started", BuildTask.class)
            .inputFrom("$task")  // Read from the variable!
    )
)
```

### ✅ Alternative: Function-Based

```java
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.forEach;

forEach(
    (Collection<BuildTask> tasks) -> tasks,
    emitJson("org.acme.build.task.started", BuildTask.class)
        .inputFrom("$item")  // Default variable name is "item"
)
```

---

## Variable Access

### Variable Naming

The variable name is set by `.each("variableName")`:

```java
.each("task")     // Creates variable "$task"
.each("item")     // Creates variable "$item"
.each("element")  // Creates variable "$element"
```

**Default:** When using `forEach()` from `FuncDSL` without explicit `.each()`, the default variable name is `"item"`.

### Accessing Variables in Sub-Tasks

Variables are accessed using the `$` prefix:

```java
.inputFrom("$task")       // Read from variable "task"
.inputFrom("$item")       // Read from variable "item"
.inputFrom("$.task.id")   // Navigate into variable properties
```

### Additional Variables

The forEach also provides:
- **`$index`**: Current iteration index (0-based)
- **`$total`**: Total number of items (if collection size is known)

Example:
```java
.tasks(
    function("logProgress", (BuildTask task, Integer index, Integer total) -> {
        LOG.info("Processing task {} of {}: {}", index + 1, total, task.id());
        return task;
    }, BuildTask.class)
)
```

---

## Troubleshooting

### Issue: "Cannot deserialize Array value"

**Symptom:**
```
Cannot deserialize value of type `BuildTask` from Array value (token `JsonToken.START_ARRAY`)
```

**Solution:** Add `.inputFrom("$variableName")` to read from the forEach variable instead of the input:

```java
emitJson("org.acme.event", BuildTask.class)
    .inputFrom("$task")
```

### Issue: Wrong Items Emitted

**Symptom:** ForEach appears to emit the same item multiple times or skip items in tests.

**Status:** This is typically a **test setup issue**, not an SDK bug. The forEach executor correctly iterates over all items.

**Common Causes:**
1. **Test Consumer Competition:** When testing event emission, your test consumer may be competing with workflow instances for the same Kafka topic/partition, causing some events to be consumed by workflows before your test reads them
2. **Mutable Collection:** Ensure the collection isn't being modified during iteration
3. **Variable Name Mismatch:** Verify `.inputFrom("$name")` matches `.each("name")`

**Solution:** Use proper test isolation - consume from a different topic or use synchronous event brokers (like `InMemoryEvents`) for testing

**Debug Steps:**
```java
forEach((Collection<BuildTask> tasks) -> {
    LOG.info("ForEach collection size: {}", tasks.size());
    tasks.forEach(t -> LOG.info("  - {}", t.id()));
    return tasks;
}, 
// ... rest of forEach
)
```

### Issue: "Variable not found"

**Symptom:** Runtime error about missing variable `$task`.

**Solution:** Ensure `.each("task")` is called before accessing `$task`:

```java
.forEach("myFor", f -> f
    .collection((Collection<?> items) -> items)
    .each("task")  // ← REQUIRED
    .tasks(...)
)
```

---

## Best Practices

1. **Always use `.inputFrom()`** when emitting events in a forEach loop
2. **Use descriptive variable names** that match your domain (e.g., `task`, `item`, `user`)
3. **Test with multiple items** to catch iteration bugs
4. **Prefer the function-based forEach** for cleaner code when possible
5. **Log collection sizes** during development to verify iteration

---

## Example: Complete Coordinator Pattern

From `CoordinatorWorkflow.java`:

```java
@Override
public Workflow descriptor() {
    return workflow("build-coordinator")
        .tasks(
            // 1. Decompose spec into tasks
            function("decompose", (BuildSpec spec) -> {
                return spec.tasks().stream()
                    .map(name -> new BuildTask(
                        spec.projectName() + "-" + name,
                        name,
                        spec.projectName(),
                        spec.gitRef()))
                    .toList();
            }, BuildSpec.class),

            // 2. Emit event for each task
            forEach(
                (Collection<BuildTask> tasks) -> tasks,
                emitJson("org.acme.build.task.started", BuildTask.class)
                    .inputFrom("$item")  // Critical!
            )
        )
        .build();
}
```

**What happens:**
1. `decompose` function creates a `List<BuildTask>`
2. `forEach` iterates over each `BuildTask`
3. For each iteration, `emitJson`:
   - Reads the current item from `$item` variable
   - Serializes it as a CloudEvent
   - Emits to `org.acme.build.task.started` topic
4. Separate `TaskWorkflow` instances consume each event

---

## References

- [CNCF Serverless Workflow Spec - For Task](https://github.com/serverlessworkflow/specification/blob/main/dsl-reference.md#for)
- [SDK Java - FuncDSL.java](https://github.com/serverlessworkflow/sdk-java/blob/main/experimental/fluent/func/src/main/java/io/serverlessworkflow/fluent/func/dsl/FuncDSL.java)
- [SDK Java - ForExecutor.java](https://github.com/serverlessworkflow/sdk-java/blob/main/impl/core/src/main/java/io/serverlessworkflow/impl/executors/ForExecutor.java)
