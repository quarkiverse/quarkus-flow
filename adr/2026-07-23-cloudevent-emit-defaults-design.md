# Smart CloudEvent Defaults for Emitted Events

**Date:** 2026-07-23
**Issues:** [quarkus-flow#776](https://github.com/quarkiverse/quarkus-flow/issues/776), [sdk-java#1554](https://github.com/open-workflow-specification/sdk-java/issues/1554)
**Status:** Part B (#776) implemented; Part A (#1554) implemented in an sdk-java branch, pending upstream release + version bump
**SDK Version (current):** `7.25.1.Final`
**Affected modules:** `core/runtime` (quarkus-flow); `impl/core`, `fluent/spec` (sdk-java)

## Context

When a workflow emits a CloudEvent (`emit.event.with`), the CNCF Serverless Workflow
[spec requires](https://github.com/open-workflow-specification/specification/blob/main/dsl-reference.md#event-properties)
`id`, `source`, and `type`. Today the produced events are under-specified:

| Attribute | Current behavior | Layer responsible |
|---|---|---|
| `id` | Runtime auto-generates a UUID per emission — **good** | `EmitExecutor` (SDK) |
| `type` | Runtime throws `IllegalArgumentException` if missing — **correct** | `EmitExecutor` (SDK) |
| `source` | Runtime falls back to `URI.create("reference-impl")` — **weak, no traceability** | `CloudEventUtils.source()` (SDK) |
| `time` | Omitted entirely unless the user sets it — **violates CloudEvents best practice** | `EmitExecutor` (SDK) |

At the Quarkus Flow DSL layer, the `FlowDSL` emit helpers (`emitJson`, `producedJson`,
`produced`, `producedBytes*`) only set `type` and `data`/`dataContentType`. Users must
manually chain `.source(...)`, `.randomId()`, `.now()` to obtain well-formed, traceable
events.

The two issues are complementary and were filed as a dependent pair (#776 *depends on*
#1554). This ADR defines **one design across both repositories** and the layer where each
default belongs.

## Key principle: where each default belongs

The decisive question is *which layer has the information needed to compute a correct
default*.

- **Execution-time attributes** — `id`, `time`, and an identity-derived `source` — depend
  on per-execution or per-definition runtime context that only the **SDK runtime**
  (`EmitExecutor`) can see. A definition-time DSL helper cannot know the running
  workflow's identity, and must not bake a per-execution value (`id`, `time`) into a
  static definition.
- **Definition-time ergonomics and overrides** — making the common path terse and letting
  users override any attribute — belong in the **quarkus-flow DSL** (`FlowDSL`).

This principle directly resolves the tension noted in #776 item 2 ("deriving `source` from
workflow identity requires threading identity into the emit spec"): we do **not** thread
identity through the DSL. The SDK runtime already holds it and is the right place to derive
`source`.

## Decision

### Part A — sdk-java (#1554): fix the runtime defaults

1. **Meaningful `source` default derived from workflow identity.**
   `EmitExecutor.buildCloudEvent(...)` already receives the `WorkflowContext`, which exposes
   `definition().id()` → `WorkflowDefinitionId(namespace, name, version)`. When no `source`
   is supplied, derive it from that identity (rendered as `namespace:name:version` via the
   existing `WorkflowDefinitionId.toString(":")`) rather than the opaque `"reference-impl"`.
   Introduce an identity-aware helper (e.g. `CloudEventUtils.source(WorkflowDefinitionId)`)
   and keep the no-arg `CloudEventUtils.source()` only as a last-resort static fallback,
   changed from `"reference-impl"` to a self-identifying URN
   (e.g. `urn:serverlessworkflow:sdk-java`).

2. **Default `time` to "now".**
   When the `timeFilter` is empty, set `ceBuilder.withTime(OffsetDateTime.now())` instead of
   omitting the attribute (use `ifPresentOrElse`). Explicit user-provided `time`
   (literal or expression) is untouched.

3. **Application-level configurable default `source` (#1554 item 4).**
   Add an optional default source on `WorkflowApplication` (builder method, e.g.
   `withDefaultEventSource(URI)`), so an application can set the source once for all emitted
   events. This is the seam quarkus-flow wires its config into (Part B).

4. **Source precedence (highest wins):**
   1. explicit per-emit `source` (DSL/definition)
   2. application-level configured default source (if set)
   3. identity-derived `source` (`namespace:name:version`)
   4. static SDK fallback URN

   Rationale: an explicit per-emit value is the most specific intent; an app-level default
   is a deliberate global choice and so outranks the automatic identity derivation;
   identity derivation is always better than an opaque constant.

5. **Validation: keep runtime enforcement, add no bean-validation annotations.**
   `EventProperties` is shared between *emit* and *filter* contexts, where required-ness
   differs; annotating it with `@NotNull`/`@NotBlank` would break filter use. With `id`,
   `source`, and `time` all now defaulted, `type` remains the only required emit attribute,
   and `EmitExecutor` already throws when it is missing. We therefore **deliberately do not**
   add model-level annotations and document why.

### Part B — quarkus-flow (#776): inject a default `source` at the registrar seam

**Chosen mechanism (implemented).** Rather than embedding a default in the static `FlowDSL`
emit helpers (which run at *definition* time and cannot see workflow identity), the default
`source` is injected at the one runtime seam both flow shapes share:
`WorkflowRegistrarService.register(Workflow)`. Class-based flows (`Flow.descriptor()`) and
YAML/JSON flows (`WorkflowReader.readWorkflowFromClasspath`) both call `register(...)` before
`application.workflowDefinition(...)`, so a single injection point covers **both**.

1. **`EmitEventSourceInjector` — recursive walker.** Before building the definition, walk the
   workflow's task tree and, for every `EmitTask` whose `event.with.source` is null, set a
   default. The walk is fully recursive over every container that can nest an emit: top-level
   `do`, `DoTask.do`, `ForTask.do`, `ForkTask.fork.branches`, `TryTask.try` + `catch.do`, and
   `ListenTask.foreach.do`. An explicit source (literal or expression) is never overwritten.

2. **Identity-derived default, out of the box.** When no override is configured, the injected
   value is the emitting workflow's identity `namespace:name:version`
   (`WorkflowDefinitionId.of(workflow).toString(":")`) — matching Part A's SDK semantics, so a
   plain `emitJson(type, Payload.class)` yields a traceable event with zero configuration and
   no more `reference-impl`.

3. **`FlowCloudEventsConfig` (`quarkus.flow.cloud-events`).**
   - `source()` (`Optional<String>`) — a fixed global override; when set it replaces the
     identity-derived value for all emits.
   - `deriveSourceFromWorkflow()` (`@WithDefault("true")`) — escape hatch to disable injection
     entirely and preserve raw SDK behavior.
   - Precedence: explicit per-emit `source` → config `source()` → identity-derived → SDK
     fallback (Part A).

4. **`time` stays in the SDK (Part A).** `time` is a per-execution value and cannot be baked
   into a definition, so it is *not* handled at this seam; it is delivered by Part A (#1554)
   once the SDK dependency is bumped. Injecting `source` here needs **no** SDK change and works
   against the current `7.25.1.Final`.

An earlier draft proposed an app-level `withDefaultEventSource(...)` customizer for source; the
registrar-seam injector makes that redundant for `source`, so it was dropped.

### Sequencing / dependency

Part B (source) ships independently against the current `7.25.1.Final` — no SDK bump required.
Part A (`time` default, and the SDK's own `source`/validation improvements) ships in a future
SDK release; quarkus-flow then bumps `io.serverlessworkflow.version` to inherit the `time`
default. Recommended order: land Part B now → land Part A upstream → release SDK → bump the
version in quarkus-flow (picks up `time`).

## Consequences

**Positive**
- Emitted events are well-formed and traceable by default (`source` identifies the emitting
  workflow; `time` is always present).
- The common DSL path (`emitJson(type, Payload.class)`) produces a spec-compliant event with
  no extra chaining.
- Clear layer ownership: per-execution values stay in the runtime; ergonomics/config stay in
  the DSL. No workflow identity is threaded through the DSL.
- Override semantics are unchanged and explicit.

**Negative / risks**
- `OffsetDateTime.now()` in `EmitExecutor` introduces a real-clock dependency; tests must
  assert on presence/bounds, not an exact value, and any deterministic-time need would
  require a clock seam (out of scope here).
- Changing the default `source` from `"reference-impl"` is a **behavioral change**:
  consumers filtering or asserting on `source == "reference-impl"` will break. This is
  acceptable for a pre-1.0 SDK but must be called out in SDK release notes.
- Part B is blocked on an SDK release + version bump (see Sequencing).

## Alternatives considered

- **Embed defaults purely in the DSL (#776 item 1 as written).** Rejected as the primary
  mechanism: the DSL cannot see workflow identity and would either hardcode a constant
  `source` or illegally bake per-execution `id`/`time` into the definition.
- **`FlowDSL.setDefaultSource(String)` mutable static.** Rejected: global mutable state,
  not thread-safe across concurrently built applications, and not Quarkus-idiomatic. Config
  property + application seam is preferred.
- **Add `@NotNull`/`@NotBlank` to `EventProperties`.** Rejected: the type is shared with the
  filter context; runtime validation in `EmitExecutor` is the correct enforcement point.
- **Static SDK `source` such as `io.serverlessworkflow.sdk-java`.** Kept only as the final
  fallback; identity-derived source is preferred for traceability.

## Test plan (high level)

- **sdk-java (#1554):** register a capturing `EventPublisher` via
  `WorkflowApplication.builder().withEventPublisher(...)`, emit with no `source`/`time`, and
  assert (a) `time` is present and close to now, (b) `source` equals the workflow identity, (c)
  app-level default source overrides identity, (d) explicit per-emit `source` overrides the
  app default, (e) missing `type` still throws.
- **quarkus-flow (#776):** unit-test `EmitEventSourceInjector` for each nesting container
  (`do`, `for`, `fork`, `try`/`catch`, `listen.foreach`), explicit-source preservation, and
  null/blank no-ops; integration-test (`@QuarkusTest`) that a class-based emit flow with no
  source gets the identity-derived `namespace:name:version` on its registered definition.
