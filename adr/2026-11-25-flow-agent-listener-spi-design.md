# Public `FlowAgentListener` SPI - Design Specification

**Date:** 2026-11-25
**Status:** Accepted
**Issue:** [#1183](https://github.com/quarkiverse/quarkus-flow/issues/1183)
**Related:** [langchain4j/langchain4j#7249](https://github.com/langchain4j/langchain4j/issues/7249), [langchain4j/langchain4j#6913](https://github.com/langchain4j/langchain4j/issues/6913), [2026-10-07 agentic trace context propagation](2026-10-07-opentelemetry-agentic-trace-context-propagation.md), [2026-11-03 runtime agent call detection](2026-11-03-runtime-agent-call-detection-design.md)

## Context

`FlowPlanner` runs the sub-agents of a generated agentic workflow on the planner thread or on LangChain4j's executor, not on the thread of the task that requested them. It stores the task's `FlowContextPropagator.Snapshot` in the `AgenticScope`, and `FlowAgentContextListener` activates it in `beforeAgentInvocation` and closes it in `afterAgentInvocation` or `onAgentInvocationError`.

An application `AgentListener` can't rely on that context. LangChain4j's `ComposedAgentListener` keeps its listeners in a `HashSet`, so whether a listener runs before or after `FlowAgentContextListener` follows identity hash codes and changes between boots. langchain4j/langchain4j#7249 proposes registration order, which makes the order fixed but still outside anything the application or Quarkus Flow controls. The use case in #1183 is an application listener recording each agent's inputs and output on that agent's own task span.

Reading the snapshot from the `AgenticScope` works, but it depends on `io.quarkiverse.flow.internal` and on a package-private key.

## Decision

Add a public SPI, `io.quarkiverse.flow.langchain4j.FlowAgentListener`, that Quarkus Flow calls itself while the requesting task's context is current. A public accessor to activate the context was the other option in #1183. It was rejected, see below.

### 1. The SPI lives in `quarkus-flow-langchain4j`

`core/runtime` doesn't depend on LangChain4j, which is why the internal `FlowAgentInvocationListener` only receives the agent name. An SPI that receives `AgentRequest`, `AgentResponse` and `AgentInvocationError` has to live in the LangChain4j module. `FlowAgentInvocationListener` stays as it is, for `quarkus-flow-opentelemetry`.

### 2. It mirrors `AgentListener`, but doesn't extend it

The SPI has `beforeAgentInvocation`, `afterAgentInvocation` and `onAgentInvocationError`, with `AgentListener`'s signatures and empty default implementations. It does not extend `AgentListener`. Quarkus LangChain4j registers every CDI bean of type `AgentListener` on every agent, so a subtype would also be called directly by LangChain4j, outside the task's context, and every callback would run twice.

The other `AgentListener` callbacks (scope creation and destruction, suspension, tool execution, planner responses) don't depend on which task's context is current, so a plain `AgentListener` keeps covering them.

### 3. CDI discovery only

Implementations are CDI beans, collected with `@All`, so they're called in `@Priority` order. `ServiceLoader` discovery was dropped during the review of #1183: an application implementation usually needs injection.

### 4. Who calls it

Every invocation is reported exactly once:

| Agent | Called by | Context |
|---|---|---|
| Leaf or composite sub-agent run by `FlowPlanner` | `FlowAgentContextListener`, after activating the snapshot and before closing it | The requesting task's |
| Composite agent Quarkus Flow builds but doesn't run through `FlowPlanner`, such as the root agentic system | `FlowAgentContextListener` | The invoking thread's |
| Leaf agent not run by `FlowPlanner`, such as one called from a task body | `FlowAgentInvocationNotifier` | The invoking thread's, which is the task's own inside a task body |

`FlowAgentContextListener` is registered on every agentic system that Quarkus Flow builds and inherited by their sub-agents. `FlowAgentInvocationNotifier` is a CDI `AgentListener` bean. Before Quarkus LangChain4j 2.0, those beans are registered on leaf agents only, so composite agents have to be reported by `FlowAgentContextListener`. The notifier skips composite agents, so nothing is reported twice once Quarkus LangChain4j 2.0 registers the beans on composite agents too.

### 5. Failures are isolated

Each implementation is called in its own `try`/`catch`. An exception is logged at `WARN` and doesn't stop the other implementations or the agent. Closing the context and aborting the planner run in a `finally` block, so a failing implementation never leaks a context. This is the same problem as langchain4j/langchain4j#6913.

## Consequences

### Positive

- The context guarantee is part of the design and doesn't depend on listener registration order.
- The context propagation model (snapshots, closing on the same thread, unwinding on suspension) stays internal.
- An application needs a single listener for every agent, so it can't record the same agent twice.

### Negative / risks

- The SPI reports agents unrelated to any workflow, such as a leaf agent invoked from a REST resource. That's intended: an application with one listener sees every agent, and those agents see the same context a plain `AgentListener` would.
- Composite agents that Quarkus Flow doesn't build, such as a supervisor agent, are not reported. Their leaf sub-agents are.
- Agents left running when an agentic system suspends get no `afterAgentInvocation`, as with `AgentListener`.

## Alternatives Considered

- **A public accessor**, for example `Optional<AutoCloseable> FlowAgentContext.activate(AgenticScope, String agentId)`. Rejected: each caller would manage a scope that must be closed on the same thread, including on the error and suspension paths, and the snapshot model would become public API.
- **`FlowAgentListener extends AgentListener`.** Rejected, see Decision 2.
- **Reporting only the sub-agents `FlowPlanner` runs.** Rejected: an application would also need a plain `AgentListener` for the other agents, and would have to tell the two sets apart through the internal key to avoid recording an agent twice.

## Testing Strategy

- `FlowAgentContextListenerTest` checks the callback order against activating and closing the context, for success, failure and nested agents, and that a failing implementation neither stops the others nor leaks the context.
- `FlowAgentInvocationNotifierTest` checks that the notifier covers leaf agents not run by `FlowPlanner`, and only those.
- `AgenticTraceContextIT` records `Span.current()` in each callback for `sequence(classify, parallel(details, summary))` and for a leaf agent called from a task body. It checks that every agent, including the root and the parallel sub-agents, sees the span of the task that requested it, still recording, in both callbacks.
