# Runtime Detection of LangChain4j Agent Calls - Design Specification

**Date:** 2026-11-03
**Status:** Accepted
**Issue:** [#1138](https://github.com/quarkiverse/quarkus-flow/issues/1138)
**Related:** [#1136](https://github.com/quarkiverse/quarkus-flow/pull/1136), [#1056](https://github.com/quarkiverse/quarkus-flow/issues/1056), [2026-10-30 GenAI semantic convention attributes design](2026-10-30-genai-agentic-span-attributes-design.md)

## Context

`quarkus-flow-opentelemetry` can only type a `task.execute` span as a GenAI `invoke_agent` operation when the task was built with the Java DSL `agent(..)` helper, which holds an `AgentFunction` (see the ADR above, Decision 5). A task that calls a LangChain4j agent from a plain `function(..)` body — or calls one conditionally — is invisible to that check: nothing in the task's definition distinguishes it from any other Java call.

`opentelemetry/integration-tests/.../AgenticTracingFlow.java` is exactly this shape when it drives its sub-agents, and so is `examples/newsletter-drafter/.../NewsletterWorkflow.java` (`function("humanEditorAgent", humanEditorAgent::edit, ...)`), although the latter does not call an `@Agent` and so is correctly left untyped (see "What is deliberately out of scope" below).

Build-time detection was rejected during #1136's review: finding `Flow` beans that *inject* agents is easy with Jandex, but injection is not invocation, and mapping a call to a specific task would mean analyzing lambda/method-reference bytecode. Workflows are also built at runtime in `descriptor()`, so there is no single build-time pass that sees every task body.

## Decision

Detect agent invocations at runtime instead, through a LangChain4j `AgentListener`, and stamp the GenAI attributes on the **already-started** task span rather than on the `SpanBuilder`.

### 1. Detection scope: leaf agents only

Only `AgentRequest.agent().leaf()` invocations are detected. Composite agents (`@SequenceAgent`, `@ParallelAgent`, `@LoopAgent`, `@ConditionalAgent`) become generated Flow workflows whose `workflow.execute` span is already typed (Decision 4 of the referenced ADR); detecting them here too would nest two `invoke_agent` spans for one invocation. Skipping composites and detecting only leaves yields exactly one typed span per agent invocation, with no duplicates, regardless of how deeply the invocation is nested.

### 2. Several agents in one task: first-wins attributes, every call gets an event

A task can call more than one agent, conditionally or in sequence. The first agent invoked sets the span's `gen_ai.operation.name`/`gen_ai.agent.name` attributes; later invocations in the same task do not overwrite them, since the conventions model one `invoke_agent` operation per span. Every invocation, including the first, also adds a `gen_ai.invoke_agent` span event naming the agent, so later calls are not silently lost from the trace.

### 3. Config: opt-out, default on

`quarkus.flow.otel.genai.detect-agent-calls` (default `true`). Disabling it only turns off this runtime path; tasks built with `agent(..)` are still typed at build time regardless, since that detection needs no runtime hook.

### 4. A core ServiceLoader SPI bridges the two modules

`quarkus-flow-langchain4j` is where the LangChain4j `AgentListener` must be registered, since that is where the agentic APIs live; `quarkus-flow-opentelemetry` is where the span-stamping logic and the `task.execute` span itself live. Neither module may depend on the other (the same constraint the 2026-10-07 agentic trace context propagation ADR solved for context propagation, with `FlowContextPropagator`).

`core/runtime` gains `FlowAgentInvocationListener`, modeled directly on `FlowContextPropagator`: a public interface with one method, `void onAgentInvoked(String agentName)`, a `ServiceLoader`-backed `Holder` that composes every registered implementation into a static `current()` accessor, and a no-op default when nothing is registered (so `quarkus-flow-langchain4j` works standalone, without OTel on the classpath). `quarkus-flow-opentelemetry` registers the implementation that does the span stamping; `FlowNativeProcessor` registers the interface for native `ServiceLoader` support, next to the existing `FlowContextPropagator` entry.

### 5. A dedicated `ContextKey` holds the task span, not `Span.current()`

Since #1056, `OTelTaskSpanProxy` makes the `task.execute` span current (via `Context.makeCurrent()`) while a `call: Java` task body runs, gated by `quarkus.flow.otel.task-span-current` (default `true`). This makes the enclosing task reachable with no bytecode analysis: the listener just needs to find "the Flow task span currently in scope" when an agent is invoked.

Rather than reading `Span.current()` directly, the proxy stores a `FlowTaskSpanRef` in the `Context` it makes current, under a dedicated `ContextKey`. `FlowTaskSpanRef` holds the span plus an `AtomicReference`-backed first-wins claim (Decision 2). A dedicated key, rather than `Span.current()`, is deliberate: it still resolves the *Flow task* span when a nested span — for example quarkus-langchain4j's AI-service span — is current, and it propagates for free wherever the OTel `Context` propagates, including across the thread hop described next.

### 6. Two call sites, because LangChain4j attaches listeners differently depending on how the agent runs

- **A leaf agent invoked directly from a task body** (the primary case the issue describes) runs synchronously on the task thread, so `Context.current()` already holds the task span. A new `FlowAgentInvocationNotifier`, a CDI `AgentListener` bean in `quarkus-flow-langchain4j`, notifies `FlowAgentInvocationListener.current()` for this case.
- **A leaf sub-agent of a generated agentic workflow** is driven by `FlowPlanner` on a different thread than the requesting task. `FlowAgentContextListener` already restores that task's `FlowContextPropagator.Snapshot` before the sub-agent runs; once that snapshot is active, the task's `Context` — and the `FlowTaskSpanRef` inside it — is current again, so `FlowAgentContextListener` notifies `FlowAgentInvocationListener.current()` itself, right after activating the snapshot. `FlowAgentInvocationNotifier` detects this case (a `FlowContextPropagator.Snapshot` stored in the `AgenticScope` execution context for this agent ID) and stays silent, leaving it to `FlowAgentContextListener`, so the invocation is not double-counted.

### 7. Interplay with build-time detection (`AgentFunction`, #1136)

A task built with `agent(..)` is already typed on the `SpanBuilder`, with the **task name** as `gen_ai.agent.name`, before the span starts. The runtime path must not overwrite that with the LangChain4j agent name instead (which may differ from the task name). `OTelTaskSpanProxy` pre-seeds the `FlowTaskSpanRef`'s first-wins claim as already-claimed when `CallJava.functionOf(task)` yields an `AgentFunction`, so for these tasks runtime detection still adds its `gen_ai.invoke_agent` span events (useful if the `agent(..)` function body itself calls further agents) but never touches the attributes.

## Consequences

### Positive

- Closes the gap in #1136: a plain `function(..)` task that calls a leaf agent, conditionally or not, is now typed the same as one built with `agent(..)`.
- No bytecode or Jandex analysis, and no new workflow-document metadata; detection follows the actual call at runtime, so it is correct regardless of how convoluted the task body's control flow is.
- Composite agents are never double-typed, because leaf-only detection complements rather than duplicates the build-time workflow-level typing from the referenced ADR.

### Negative / risks

- **Attributes set after `startSpan()`.** Unlike every other `gen_ai.*` attribute Quarkus Flow sets (always on the `SpanBuilder`), runtime detection necessarily mutates a span that has already started. These attributes are invisible to samplers and to `onStart` `SpanProcessor` implementations. Documented as a limitation.
- **Depends on `quarkus.flow.otel.task-span-current=true`.** If that flag is turned off, no task span is ever current during the task body, so runtime detection has nothing to attach to and silently detects nothing. Documented alongside the new config property.
- **A second core SPI next to `FlowContextPropagator`.** Both exist to let `quarkus-flow-opentelemetry` and `quarkus-flow-langchain4j` cooperate without a direct dependency; a third such need should prompt revisiting whether a more general "Flow runtime event bus" belongs in core instead of one narrow interface per concern.
- **First-wins attribution can be misleading** when a task genuinely treats two agents as co-equal rather than primary/secondary. The span events preserve the full call history, but `gen_ai.agent.name` only ever names the first.

## Alternatives Considered

1. **Build-time Jandex/bytecode analysis of task bodies.** Rejected during #1136's review for the reasons in the Context section: injection is not invocation, mapping a call to a specific task needs lambda/method-reference bytecode analysis, and workflows can be built at runtime.
2. **Type composite (non-leaf) agent invocations too.** Rejected. Composites already produce a typed `workflow.execute` span for the same invocation (Decision 4 of the referenced ADR); typing the calling task's span as well would nest two `invoke_agent` spans for one logical invocation.
3. **Read `Span.current()` directly instead of a dedicated `ContextKey`.** Rejected. A nested span current at the moment of invocation (for example an AI-service span) would shadow the task span, and `Span.current()` gives no way to also carry the first-wins claim state across the `FlowPlanner` thread hop.
4. **Defer attribute-setting to task-span end**, buffering invoked agent names and applying them in `OTelWorkflowExecutionListener`'s task-completion handling instead of live, to avoid mutating a started span. Rejected: it adds a buffering mechanism and a second code path for what is otherwise one `addEvent`/`setAttribute` call, to work around a limitation (attributes after start are invisible to `onStart` processors and samplers) that is already acceptable and documented; the span's own attributes at export time are unaffected either way.
5. **Always overwrite the attributes with the most recently invoked agent**, instead of first-wins. Rejected. The conventions model `invoke_agent` as a single operation; "most recent" is an arbitrary choice no more meaningful than "first", and first-wins matches the intuition that the first agent a task calls is usually its primary one, while every call is still visible as a span event.
6. **Default the config property to off**, requiring explicit opt-in. Rejected. The build-time path (`agent(..)`) already requires no configuration; making the runtime path opt-in would leave the exact gap this issue reports unfixed by default, which defeats the purpose of fixing #1138 in the first place.

## Testing Strategy

- `core/runtime` unit tests on `FlowAgentInvocationListener.Holder`: no registered listener is a no-op, a single listener is used as-is, composed listeners are all notified — mirroring the existing `FlowContextPropagatorTest`.
- `opentelemetry/runtime` unit tests: `OTelTaskSpanProxyTest` extended to show a `FlowTaskSpanRef` resolves to the task span inside the task body and is gone once the proxy's scope closes. A new `OTelAgentInvocationListenerTest` covers no enclosing task span (no-op), a single agent (attributes plus one event), two distinct agents (attributes from the first only, two events), the same agent invoked twice (attributes once, two events), and detection disabled (no-op).
- `langchain4j/runtime` unit tests: `FlowAgentInvocationNotifierTest` covers a non-leaf agent (not notified), a leaf agent (notified), and a leaf agent whose `AgenticScope` holds a Flow snapshot (left to `FlowAgentContextListener`, not notified here). `FlowAgentContextListenerTest` gained cases showing a leaf sub-agent is notified once its context is active, and a composite sub-agent is not.
- `opentelemetry/integration-tests` (`AgenticTraceContextIT`, `AgentCallDetectionDisabledIT`): the task driving a composite agentic system stays untyped while the generated workflow's leaf sub-agent task spans (`classify`, `details`, `summarize`) are typed `invoke_agent`; a new fixture with a plain `function(..)` task calling a leaf `@Agent` directly is typed with the agent's name (the regression test for the issue); a second fixture calling two leaf agents from one task keeps the first agent's attributes and records two span events; and with `quarkus.flow.otel.genai.detect-agent-calls=false`, no task span is typed at runtime.
