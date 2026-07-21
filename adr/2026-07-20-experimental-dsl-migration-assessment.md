# Assessment: Move SDK Experimental DSL to Quarkus Flow

**Date:** 2026-07-20  
**Issue:** [#722](https://github.com/quarkiverse/quarkus-flow/issues/722)  
**Status:** Implemented  
**SDK Branch:** `remove-experimental` (sdk-java repo)  
**Quarkus Flow Branch:** `issues/722`

## Context

The Serverless Workflow SDK Java contains an `experimental/` module that provides a Java-native functional DSL for defining workflows using lambdas, functions, and predicates. This code is tightly coupled to Quarkus Flow (it's the primary user-facing API) and has no consumers within the SDK itself. Moving it to Quarkus Flow under a `dsl` namespace gives it a proper home, clearer ownership, and allows independent evolution.

This is a **pure move** — no functional changes, fixes, or additions in this phase.

## What's Being Moved

### Source inventory

| SDK Submodule | Artifact | Main files | Test files | ~LOC | Purpose |
|---|---|---|---|---|---|
| `experimental/types` | `serverlessworkflow-experimental-types` | 35 | 0 | 1,434 | Functional interfaces (SerializableFunction, ContextFunction, FilterFunction, LoopFunction, etc.) and type metadata records |
| `experimental/fluent/func` | `serverlessworkflow-experimental-fluent-func` | 49 | 9 | 6,597 | Fluent builder DSL — `FuncDSL` entry point, task builders, configurers, step interfaces |
| `experimental/fluent/jackson` | `serverlessworkflow-experimental-fluent-serialization-jackson` | 19 | 0 | 863 | Jackson serialization for lambdas (SerializedLambda support, mix-ins, custom serializers/deserializers) |
| `experimental/lambda` | `serverlessworkflow-experimental-lambda` | 16 | 15 | 1,030 | Runtime execution engine — task executors, expression factory for Java lambdas |
| `experimental/model` | `serverlessworkflow-experimental-model` | 6 | 3 | 536 | Pure-Java WorkflowModel implementation (not JSON-based) |
| `experimental/lambda-fluent` | `serverlessworkflow-lambda-fluent` | 0 | 0 | — | Aggregator POM only (combines fluent-func + lambda) |
| `experimental/test` | `serverlessworkflow-experimental-test` | 0 | 12 | — | Integration tests (HTTP, OpenAPI, events, serialization round-trip) |
| **Total** | | **125** | **39** | **~10,460** | |

### Key characteristics

- **Fully self-contained**: Zero SDK modules outside `experimental/` depend on it. The BOM doesn't list it.
- **SPI-heavy**: 7 service provider registrations across 4 modules (CloudEventPredicateFactory, CallableTaskBuilder, TaskExecutorFactory, ExpressionFactory, Jackson Module, CustomObjectMarshaller ×3, WorkflowModelFactory).
- **Native image config**: `fluent/jackson` has a `reflect-config.json` registering 13 classes for GraalVM.
- **External dependencies**: Only `io.cloudevents:cloudevents-core` beyond the SDK itself and Jackson.

## Impact on Quarkus Flow

### Files that import from experimental packages

**88 files** across the Quarkus Flow codebase currently import from experimental packages (~208 import statements). Breakdown:

| Module | Production files | Test files | Scope |
|---|---|---|---|
| `core/runtime` | 4 (Flow.java javadoc, converters, WorkflowApplicationCreator, codestart) | — | `FuncWorkflowBuilder`, `FuncDSL`, `DataTypeConverter`, `JavaModelFactory` |
| `core/deployment` | 1 (FlowNativeProcessor) | 5 | `DataTypeConverter`, `FuncWorkflowBuilder`, `FuncDSL` |
| `core/integration-tests` | 7 | 1 | `FuncWorkflowBuilder`, `FuncDSL` (all workflow definitions) |
| `runner/integration-tests` | 3 | — | `FuncWorkflowBuilder`, `FuncDSL` |
| `oidc/runtime` | — | 5 | `FuncWorkflowBuilder`, `FuncDSL` |
| `oidc/integration-tests` | 2 | — | `FuncWorkflowBuilder`, `FuncDSL` |
| `examples/` | ~17+ | — | `FuncWorkflowBuilder`, `FuncDSL` (all example workflows) |

### What changes for users

After the move, user-facing imports change:

| Before (SDK experimental) | After (Quarkus Flow DSL) |
|---|---|
| `io.serverlessworkflow.fluent.func.FuncWorkflowBuilder` | `io.quarkiverse.flow.dsl.fluent.func.FuncWorkflowBuilder` (or similar) |
| `io.serverlessworkflow.fluent.func.dsl.FuncDSL` | `io.quarkiverse.flow.dsl.fluent.func.dsl.FuncDSL` (or similar) |
| `io.serverlessworkflow.api.types.func.*` | `io.quarkiverse.flow.dsl.types.func.*` (or similar) |

The exact new package structure should be decided — see "Decisions needed" below.

### Current dependency chain

```
Quarkus Flow core/runtime
  └─ serverlessworkflow-experimental-fluent-func
  │    └─ serverlessworkflow-experimental-types
  │    └─ serverlessworkflow-fluent-spec        (stays in SDK)
  │    └─ serverlessworkflow-impl-json          (stays in SDK)
  └─ serverlessworkflow-experimental-lambda
  │    └─ serverlessworkflow-experimental-types
  │    └─ serverlessworkflow-impl-core          (stays in SDK)
  └─ serverlessworkflow-experimental-model
       └─ serverlessworkflow-impl-core          (stays in SDK)
```

After the move, the new `dsl` module(s) within Quarkus Flow will still depend on SDK non-experimental artifacts (`serverlessworkflow-fluent-spec`, `serverlessworkflow-impl-core`, `serverlessworkflow-impl-json`, `serverlessworkflow-types`). These stay in the SDK.

## Impact on SDK Java

### What to remove

1. Delete `experimental/` directory entirely
2. Remove `<module>experimental</module>` from root `pom.xml`
3. No other changes needed — nothing else references it

### SDK version note

The SDK is at `8.0.0-SNAPSHOT`. Quarkus Flow currently uses `7.25.1.Final`. The experimental code being moved is from the `8.0.0-SNAPSHOT` branch (`remove-experimental`). After the move, Quarkus Flow will need the experimental artifacts removed from its SDK dependency (and the code living in its own `dsl` module instead).

## Decision: Inline into `core/` — no new module

The DSL introduces **zero new dependencies** beyond what `core/runtime` already has. Creating a separate `dsl/` module would add build complexity for no benefit. The `fluent` package layer from the SDK was an organizational artifact, not a meaningful boundary — the builders and entry points *are* the DSL.

**All 125 source files go into `core/runtime/src/main/java/` under `io.quarkiverse.flow.dsl.*`.**  
**Tests go into `core/integration-tests/`.**  
**The `lambda-fluent` aggregator POM is dropped** (no source code, no purpose once merged).

### Package mapping

| SDK package | → Quarkus Flow package |
|---|---|
| `io.serverlessworkflow.fluent.func.*` (builders) | `io.quarkiverse.flow.dsl` |
| `io.serverlessworkflow.fluent.func.dsl.*` (FuncDSL, steps, specs) | `io.quarkiverse.flow.dsl` |
| `io.serverlessworkflow.fluent.func.configurers.*` | `io.quarkiverse.flow.dsl.configurers` |
| `io.serverlessworkflow.fluent.func.spi.*` | `io.quarkiverse.flow.dsl.spi` |
| `io.serverlessworkflow.api.types.func.*` | `io.quarkiverse.flow.dsl.types` |
| `io.serverlessworkflow.api.types.utils.*` | `io.quarkiverse.flow.dsl.types.utils` |
| `io.serverlessworkflow.fluent.func.serialization.jackson.*` | `io.quarkiverse.flow.dsl.serialization.jackson` |
| `io.serverlessworkflow.impl.executors.func.*` | `io.quarkiverse.flow.dsl.executors` |
| `io.serverlessworkflow.impl.expressions.func.*` | `io.quarkiverse.flow.dsl.expressions` |
| `io.serverlessworkflow.impl.model.func.*` | `io.quarkiverse.flow.dsl.model` |

### Class renames

- `FuncDSL` → `FlowDSL` — makes ownership obvious, serves as a clear migration signal in docs/examples/user code.
- `FuncWorkflowBuilder` → `FlowWorkflowBuilder` — the other primary user-facing class, renamed for consistency.

Internal `Func*` classes (builders, configurers, step interfaces, SPI) are left as-is — they're internal plumbing users rarely import directly. To be reassessed when reviewing the DSL against the spec.

### What users see

```java
// Before
import static io.serverlessworkflow.fluent.func.FuncWorkflowBuilder.workflow;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.get;

// After
import static io.quarkiverse.flow.dsl.FlowWorkflowBuilder.workflow;
import static io.quarkiverse.flow.dsl.FlowDSL.get;
```

## Execution plan (high level)

### PR 1: Add DSL to Quarkus Flow

1. Copy all 125 source files from SDK `experimental/` into `core/runtime/src/main/java/io/quarkiverse/flow/dsl/` (preserving sub-package structure per mapping above)
2. Rename all packages in copied files
3. Update all SPI service files (`META-INF/services/`) with new class names
4. Update `reflect-config.json` for native image
5. Remove `serverlessworkflow-experimental-*` dependencies from `core/runtime/pom.xml` and `core/deployment/pom.xml`
6. Update all 88 files in Quarkus Flow that import from experimental packages
7. Copy tests from SDK `experimental/` into `core/integration-tests/`, update packages
8. Update codestart template
9. Verify build: `./mvnw clean install`

### PR 2: Remove experimental from SDK

1. Delete `experimental/` directory
2. Remove `<module>experimental</module>` from root `pom.xml`
3. Verify build: `./mvnw clean install`

### Follow-up (separate PRs)

- Update all documentation (`docs/`)
- Update all examples (`examples/`)
- Migration guide for existing users (old imports → new imports)
- Reassess DSL against the specification

## Risks

| Risk | Mitigation |
|---|---|
| SDK version mismatch (QF uses 7.25.1.Final, experimental is at 8.0.0-SNAPSHOT) | Ensure the moved code compiles against the SDK version QF currently uses; may need SDK version bump |
| Native image breakage | Carry over `reflect-config.json`, add Quarkus `@RegisterForReflection` where appropriate |
| SPI registration conflicts during transition | Users can't have both old SDK experimental and new QF dsl on classpath simultaneously |
| Breaking change for users (package rename) | Provide migration guide; consider a one-release compatibility shim if adoption is wide |
| Examples not building | Update all examples in same PR or immediately after |
