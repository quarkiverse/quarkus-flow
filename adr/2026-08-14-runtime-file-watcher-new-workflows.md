# Runtime File Watcher for New Workflow Definitions - Design Specification

**Date:** 2026-08-14  
**Status:** Proposed  
**Issue:** [#843](https://github.com/quarkiverse/quarkus-flow/issues/843)  
**Related:** [#842](https://github.com/quarkiverse/quarkus-flow/issues/842) (update/remove — requires SDK changes)

## Overview

Add an opt-in polling-based file watcher to the `runner/runtime` module that monitors the `quarkus.flow.runner.source.path` directory for new workflow definition files and registers them automatically, without requiring a pod restart.

This covers **new file detection only**. Updating or removing existing definitions requires upstream SDK changes to `WorkflowApplication` (tracked in #842).

## Goals

1. **Live detection:** Detect new workflow files added to the configured source path after application startup
2. **Automatic registration:** Parse and register new workflows via existing `WorkflowRegistrarService`
3. **Kubernetes-native:** Work correctly with ConfigMap volume mounts (symlink-based rotation)
4. **Opt-in:** Disabled by default for library users; enabled by default in the runner container image
5. **Error resilient:** Malformed files are skipped with a warning, never crash the watcher

## Non-Goals

- Detecting modified workflow files (requires SDK `replaceWorkflowDefinition` — see #842)
- Detecting removed workflow files (requires SDK `removeWorkflowDefinition` — see #842)
- Real-time / event-driven detection (inotify/WatchService unreliable with K8s symlinks)

## Context

### Current Behavior

`WorkflowDefinitionRuntimeLoader` performs a one-time `Files.walk()` scan at startup, triggered by `WorkflowApplicationReadyEvent`. After startup, no further scanning occurs. In Kubernetes, when a new `LogicFlowDefinition` CR is created, the operator updates the ConfigMap, Kubernetes writes the new file to disk, but the runner never picks it up.

### Why Polling Over WatchService

Kubernetes ConfigMap volume mounts use a symlink-based rotation mechanism (`..data` -> `..2024_01_01_...` -> actual files). Java's `WatchService` (backed by `inotify` on Linux) does not reliably fire events when the symlink target rotates. The existing `followSymlinks` config property in the source config already acknowledges this constraint. Polling with `Files.walk()` is the reliable approach.

### Why the SDK Supports New Registrations

`WorkflowApplication.workflowDefinition(Workflow)` uses `computeIfAbsent` on a `ConcurrentHashMap`. Registering a new workflow ID works correctly — the method creates and returns a new `WorkflowDefinition`. Only replacing or removing an existing ID is unsupported (tracked in #842).

## Configuration

### Build-Time Property

New build-time config class `FlowRunnerSourceWatchConfig` with its own prefix `quarkus.flow.runner.source.watch` (separate from the runtime `FlowRunnerConfig` prefix to avoid SmallRye Config conflicts between phases, following the same pattern as `durable-kubernetes` which uses `quarkus.flow.durable.kube.local` for build-time and `quarkus.flow.durable.kube` for runtime):

| Property | Type | Default | Phase | Description |
|---|---|---|---|---|
| `quarkus.flow.runner.source.watch.enabled` | `boolean` | `false` | `BUILD_TIME` | Enable/disable the file watcher |

### Runtime Property

Added to existing `FlowRunnerConfig.Source` as a nested `Watch` interface. Note: this shares the `quarkus.flow.runner.source.watch` namespace with the build-time config above, but SmallRye Config handles this correctly when the prefixes are at different nesting levels — the build-time config owns `quarkus.flow.runner.source.watch.enabled` directly via its root prefix, while the runtime config reaches `quarkus.flow.runner.source.watch.interval` through the nested `Source.Watch` interface under the `quarkus.flow.runner` root:

| Property | Type | Default | Phase | Description |
|---|---|---|---|---|
| `quarkus.flow.runner.source.watch.interval` | `String` | `5s` | `RUN_TIME` | Polling interval (Quarkus scheduler format) |

### Runner App Defaults

In `runner/app/src/main/resources/application.properties`:

```properties
quarkus.flow.runner.source.watch.enabled=true
```

The container image builds with watching enabled. Library users (adding the extension to their own app) get the default `false` and must opt in explicitly.

## Architecture

### Components

**`WorkflowFileWatcher`** — new bean in `runner/runtime`:
- `@ApplicationScoped`, `@Unremovable`
- Only registered when `watch.enabled=true` (gated by `@IfBuildProperty` in the deployment processor)
- Observes `WorkflowApplicationReadyEvent` to start watching after the initial load completes
- Uses the Quarkus programmatic `Scheduler` API (same pattern as `PoolController` in `durable-kubernetes`)

**`FlowRunnerSourceWatchConfig`** — new build-time config class in `runner/runtime`:
- `@ConfigRoot(phase = BUILD_TIME)`, `@ConfigMapping(prefix = "quarkus.flow.runner.source.watch")`
- Holds the `enabled` property

**`FlowRunnerProcessor`** — updated deployment processor:
- New `@BuildStep` gated with `@IfBuildProperty(name = "quarkus.flow.runner.source.watch.enabled", stringValue = "true")`
- Registers `WorkflowFileWatcher` as an additional bean
- Produces `ForceStartSchedulerBuildItem` to ensure the Quarkus scheduler infrastructure is started

### Interaction with Existing Components

The `WorkflowFileWatcher` is **decoupled** from `WorkflowDefinitionRuntimeLoader`. The watcher builds its own baseline `Set<Path>` of known files via an initial `Files.walk()` when it starts. This keeps the two beans independent — the watcher works even if someone uses a different loading mechanism at startup.

Both beans share the same `WorkflowRegistrarService` for registration and read from the same `FlowRunnerConfig` for source path and symlink settings.

### OpenAPI Integration

No changes needed to `WorkflowOpenApiFilter`. The filter uses `RunStage.RUNTIME_PER_REQUEST`, meaning it reads from `WorkflowApplication.workflowDefinitions()` on every OpenAPI document request. Newly registered workflows are automatically included in the OpenAPI document the next time it is fetched (e.g., reloading Swagger UI).

### Lifecycle

```
App starts
  -> WorkflowDefinitionRuntimeLoader.onStart(WorkflowApplicationReadyEvent)
       -> one-time Files.walk() + register all found workflows
  -> WorkflowFileWatcher.onStart(WorkflowApplicationReadyEvent)
       -> initial Files.walk() to build baseline Set<Path>
       -> schedule periodic job via Scheduler API
  -> [every interval]
       -> WorkflowFileWatcher.poll()
            -> Files.walk() -> find new paths not in knownFiles
            -> for each new file: parse, validate, register, add to knownFiles
  -> @PreDestroy
       -> unschedule the job
```

## Watcher Logic

Each poll cycle:

1. `Files.walk(basePath)` with `FileVisitOption.FOLLOW_LINKS` if `followSymlinks` is enabled
2. Filter for regular files with supported extensions (`.yaml`, `.yml`, `.json`)
3. Compare against `knownFiles: Set<Path>` — any path not in the set is new
4. For each new file:
   a. Parse with `WorkflowReader.readWorkflow(path)`
   b. Validate required fields (`namespace`, `name`, `version`)
   c. Check for duplicate `WorkflowDefinitionId` against already-registered definitions via `WorkflowApplication.workflowDefinitions()`
   d. Register via `WorkflowRegistrarService.register(workflow)`
   e. Add path to `knownFiles`
   f. Log at `INFO`: registered workflow `namespace:name:version` from `path`
5. If parsing/validation fails: log at `WARN` and skip the file (do not add to `knownFiles` so it is retried next cycle)

### Error Resilience

- **Malformed files**: `WorkflowReader` throws `IOException` — caught, logged as WARN, file skipped. The file is NOT added to `knownFiles`, so the next poll retries it (handles partially-written files during ConfigMap rotation).
- **Missing required fields**: caught, logged as WARN, file skipped (also retried).
- **Duplicate workflow ID**: a new file with the same `namespace:name:version` as an already-registered workflow is skipped with a WARN (unlike the startup loader which throws `IllegalStateException`).
- **Directory disappears**: `Files.walk()` throws `IOException` — caught, logged as WARN, poll cycle skipped. Watcher continues on next interval.

### Scheduler Job Configuration

- Job identity: `"flow-runner-file-watcher"`
- Concurrent execution: `Scheduled.ConcurrentExecution.SKIP` (prevent stacking if a poll takes longer than the interval)
- Interval: from runtime config `quarkus.flow.runner.source.watch.interval`

## Files Changed

| File | Change |
|---|---|
| `runner/runtime/pom.xml` | Add `quarkus-scheduler` dependency |
| `runner/deployment/pom.xml` | Add `quarkus-scheduler-deployment` dependency |
| `runner/runtime/.../FlowRunnerSourceWatchConfig.java` | **New** — build-time config with `source.watch.enabled` |
| `runner/runtime/.../FlowRunnerConfig.java` | Add `Watch` interface inside `Source` with `interval` property |
| `runner/runtime/.../WorkflowFileWatcher.java` | **New** — polling watcher bean |
| `runner/deployment/.../FlowRunnerProcessor.java` | Add conditional build step for watcher + `ForceStartSchedulerBuildItem` |
| `runner/app/.../application.properties` | Add `quarkus.flow.runner.source.watch.enabled=true` |
| `runner/runtime/src/test/.../WorkflowFileWatcherTest.java` | **New** — unit tests |
| `runner/integration-tests/...` | New integration test for end-to-end verification |

## Testing Strategy

### Unit Tests

In `runner/runtime/src/test/java/`, following existing `WorkflowDefinitionRuntimeLoaderTest` patterns (Mockito + `@TempDir`):

- `test_watcher_detects_new_yaml_file` — add a file after baseline, verify registration
- `test_watcher_detects_new_files_in_subdirectory` — recursive scanning
- `test_watcher_ignores_already_known_files` — baseline files not re-registered
- `test_watcher_ignores_unsupported_extensions` — `.txt`, `.xml` skipped
- `test_watcher_skips_malformed_files_without_crashing` — bad YAML logs warning, watcher continues
- `test_watcher_skips_duplicate_workflow_id` — duplicate `namespace:name:version` skipped with warning
- `test_watcher_detects_multiple_new_files_in_single_poll` — batch detection

### Integration Test

In `runner/integration-tests/`: verify the full lifecycle — app starts with watch enabled, a new workflow file is written to the source path after startup, and within the poll interval it becomes queryable via the `/q/flow/definitions` REST endpoint.

## Alternatives Considered

### ScheduledExecutorService (manual threading)

Use `java.util.concurrent.ScheduledExecutorService` directly instead of Quarkus Scheduler. Zero new dependencies, but requires manual thread lifecycle management and doesn't integrate with Quarkus scheduler subsystem. Rejected because `quarkus-scheduler` is already used in other modules (`scheduler/memory`, `durable-kubernetes`).

### Vert.x setPeriodic

Use `Vertx.setPeriodic()` since Vert.x is transitively available. However, `Files.walk()` is blocking I/O and running it on a Vert.x event loop thread is incorrect — would require `executeBlocking()` adding unnecessary complexity.

### Java WatchService

Use `java.nio.file.WatchService` for event-driven file detection. More efficient (no polling), but unreliable with Kubernetes ConfigMap volume mounts due to symlink-based rotation not triggering `inotify` events consistently.
