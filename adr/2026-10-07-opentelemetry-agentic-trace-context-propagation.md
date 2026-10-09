# Trace Context Propagation Across Workflow Tasks and Agentic Topologies - Design Specification

**Date:** 2026-10-07
**Status:** Proposed
**Issue:** [#1056](https://github.com/quarkiverse/quarkus-flow/issues/1056)

## Context

When `quarkus-flow-opentelemetry` and `quarkus-flow-langchain4j` are used together, a workflow task that invokes a LangChain4j agentic system (`@SequenceAgent`, `@ParallelAgent`, `@ConditionalAgent`, `@LoopAgent`) produces several disconnected traces instead of one.

For a task invoking `sequence(classify, parallel(details, summary))`, a build of `main` @ `9ba5347` produces **6 traces**:

1. `workflow.execute <caller>` with its `task.execute` span
2. `workflow.execute intake-agents` (the workflow generated for the `@SequenceAgent`)
3. `workflow.execute extraction-agent` (the workflow generated for the `@ParallelAgent`)
4. to 6. one `langchain4j.aiservices.<Agent>.<method>` trace per AI service call

The reporter's workaround (making the task span current by hand around the agent call) only brings this to 5.

Trace context is lost at three separate boundaries, each needing its own fix:

```
workflow.execute caller
└── task.execute runAgents            (1) task span exists but is never current in the task body
    └── supplyAsync(instance::start)  (2) generated workflow starts on a pool thread with no context
        └── workflow.execute intake-agents
            └── task.execute classify-0
                └── agent queue hop   (3) agent runs on the planner thread / LangChain4j executor
                    └── langchain4j.aiservices.ClassifierAgent.classify   new root trace
```

### Cause 1: task spans are never current inside the task body

`OTelWorkflowExecutionListener.onTaskStarted` starts the `task.execute` span and stores it in `WorkflowInstrumentationContext`, but nothing makes it current. Inside a call task, `Span.current()` is invalid, so every span the task body creates (AI services, REST clients, JDBC) starts a new trace. This affects all workflows, not only agentic ones.

### Cause 2: generated agentic workflows start without the caller's context

`FlowPlanner.firstAction` starts the generated workflow with `CompletableFuture.supplyAsync(instance::start)` on the common pool. Nothing carries the caller's OTel context over, so `onWorkflowStarted` sees no parent and `workflow.execute <generated>` becomes a new root.

### Cause 3: agent invocations lose the requesting task's context

A generated workflow task does not run its agent itself. It queues an `AgentExchange` (`FlowPlanner.executeAgent`), and LangChain4j's `PlannerBasedInvocationHandler` runs the agent on the planner thread or, for parallel topologies, on its own executor. The context of the task that requested the agent never reaches that thread, so the AI service span becomes a new root.

This cause is not in the original issue. Fixing (1) and (2) alone still leaves the parallel agents' calls in separate traces (3 traces instead of 1).

## Decision

Fix each boundary where it occurs, without making `quarkus-flow` core or `quarkus-flow-langchain4j` depend on OpenTelemetry, and without making `quarkus-flow-opentelemetry` depend on LangChain4j.

### 1. Make the task span current around the task body (`quarkus-flow-opentelemetry`)

`OTelTaskSpanProxy` implements the SDK's `CallableTaskProxyBuilder` SPI, the same hook Quarkus Flow already uses for fault tolerance. It is registered through `META-INF/services`. It wraps each call task's `CallableTask` and, while the delegate runs, makes current the context formed by the task's `task.execute` span on top of the task's parent context. If the workflow has no instrumentation context, or the task has no span, it calls the delegate unchanged.

Proxies are applied in ascending priority order. `OTelTaskSpanProxy` uses priority `100`, lower than the fault tolerance proxy's default `1000`. It therefore wraps the task body directly, and the span is current on every retry attempt.

**Scope: Java function call tasks only.** The proxy applies only to `call: Java` tasks. These are what the Java DSL's `function(..)`, `consume(..)` and `agent(..)` produce, including the tasks of generated agentic workflows. Their body runs inside `CallableTask.apply`, or is submitted from inside it to the workflow's managed executor, which carries the context over.

HTTP, OpenAPI, gRPC, A2A, AsyncAPI and MCP call tasks don't do their work inside `apply`; they finish it asynchronously after `apply` returns. Making the task span current around `apply` doesn't reliably reach their client spans. In testing, with HTTP tasks included, most client spans kept the incoming request's span as parent, some got their own task span, and a few got a different task's span. These task types therefore keep the previous behavior. A `call: <function>` that refers to a catalog or inline function can resolve to any of them, so it is excluded too. See Known limitations below.

**Adjustable default.** This is on by default, which fixes (1) for every Java function call task, in any workflow, not only agentic ones. It can be turned off:

```properties
quarkus.flow.otel.task-span-current=false
```

Turning it off restores the previous behavior: the `task.execute` span is recorded but never current. This is the escape hatch for applications that relied on task bodies running with the caller's context, or that manage context themselves. Making it opt-in instead would need only a change of default, with no code change. The negative-control run below shows what the opt-out gives up.

### 2. A neutral context propagation SPI in core (`quarkus-flow`)

`io.quarkiverse.flow.internal.FlowContextPropagator` is a small `ServiceLoader` SPI. Its methods are:

- `capture()`: returns a `Snapshot` of the calling thread's context.
- `Snapshot.activate()`: makes the snapshot current and returns a `Scope`, which restores the previous context when closed.
- `FlowContextPropagator.current()`: composes every registered implementation. When none is registered it is a no-op, so callers never branch on whether OTel is present.

`quarkus-flow-opentelemetry` registers `OTelFlowContextPropagator`, which captures `Context.current()`.

The SPI lives in core because both consumers (`quarkus-flow-langchain4j`) and providers (`quarkus-flow-opentelemetry`) already depend on core. Neither needs to know about the other.

The SPI also has room for other thread-bound context, such as MDC or a security identity, by registering more implementations.

### 3. Carry the caller's context into generated workflows (`quarkus-flow-langchain4j`)

`FlowPlanner.firstAction` captures a snapshot before `supplyAsync` and activates it around `instance.start()`. This fixes (2).

### 4. Carry each requesting task's context to its agent (`quarkus-flow-langchain4j`)

The fix has three parts:

- `FlowPlanner.executeAgent`, which runs on the workflow task's thread with the task span current thanks to (1), captures a snapshot into the `AgentExchange`.
- When the planner hands the queued agents to LangChain4j, it writes each snapshot into the `AgenticScope` execution context under `flow.agent.context:<agentId>`. Execution context is transient and is not persisted with the scope.
- `FlowAgentContextListener`, a public LangChain4j `AgentListener` registered by every `Flow*AgentService.build()` with `inheritedBySubagents() == true`, does the activation:
  - `beforeAgentInvocation` activates the snapshot for that agent id on the thread that actually runs the agent.
  - `afterAgentInvocation`, `onAgentInvocationError` or `onAgenticSystemSuspended` closes it.

This fixes (3). Agents that the planner did not dispatch (the root agentic system call itself, standalone agents) have no stored snapshot and are left alone. Scopes are kept per thread on a stack, keyed by agent id, so nested agentic systems close in the right order.

## Alternatives Considered

- **Optional `opentelemetry-context` dependency in `quarkus-flow-langchain4j`.** The first spike did this. It works, but it puts OTel types in the LangChain4j module, and every other context type would need the same treatment.
- **A bridge interface in `quarkus-flow-langchain4j`, implemented by `quarkus-flow-opentelemetry`.** This keeps LangChain4j free of OTel but makes the OTel extension depend on the LangChain4j extension, runtime and deployment. Every OTel user would then pull in LangChain4j. Rejected.
- **Wrapping LangChain4j's internal `AgentInvoker` in a reflection proxy (for 4).** This was verified in the spike to give the same single trace, but it relies on `dev.langchain4j.agentic.internal` types. The public `AgentListener` approach gave the same result (two runs each, 1 trace and 21 spans) and is what was implemented.
- **Relying on MicroProfile Context Propagation.** Quarkus registers an OTel `ThreadContextProvider`, but `supplyAsync` without an executor uses the common pool, and LangChain4j's executor is not a managed executor. Neither propagates.

## Consequences

### Positive

- An agentic system invoked from a workflow task produces a single trace, with every workflow, task, AI service and model span under its correct parent.
- Any span created in the body of a Java function call task (REST clients, JDBC, custom spans) now nests under its task span, for every workflow.
- No new dependency edges: `quarkus-flow-langchain4j` has no OTel dependency, and `quarkus-flow-opentelemetry` depends only on `quarkus-flow` (which it already needed at runtime) and does not depend on LangChain4j.
- Only public SPIs are used: `CallableTaskProxyBuilder` and LangChain4j's `AgentListener`.

### Negative / Risks

- **Behavior change.** The bodies of Java function call tasks now run with the task span current. Code that read `Span.current()` in such a task body and expected the caller's span will see the task span. The opt-out above covers this.
- `FlowAgentContextListener` relies on LangChain4j calling `beforeAgentInvocation` and its matching end callback on the same thread. This holds for `AgentInvoker.invoke` in LangChain4j 1.14.1. A suspended system ends with `onAgenticSystemSuspended`, which carries no agent id. Every level of a nested system fires it, and they all share one `AgenticScope`, so it closes only the open scopes on top of the thread's stack that belong to that `AgenticScope`. This closes each abandoned context once and never touches an enclosing, unrelated system's context.
- The Ollama client's HTTP `POST` spans still start their own traces. This happens without Quarkus Flow too: a plain `ChatModel.chat(..)` inside an active span gives the same result. It is outside this change.

## Verification

- Unit tests:
  - `FlowContextPropagatorTest` (core) covers no-op, single and composed propagators, and close order.
  - `FlowAgentContextListenerTest` (langchain4j) covers activation and closing on success, error and suspension, agents not dispatched by the planner, and nested agents.
  - `OTelTaskSpanProxyTest` (opentelemetry) covers:
    - the span being current inside the delegate and cleared afterwards;
    - the disabled switch;
    - running with no instrumentation context;
    - which call task types are accepted (`call: Java` only).
- Integration test: `AgenticTraceContextIT` in `opentelemetry/integration-tests` runs a workflow task invoking `sequence(classify, parallel(details, summary))` against a WireMock Ollama. It asserts:
  - `Span.current()` inside the task body is the task span;
  - all 15 Flow, AI service and model spans share one trace;
  - both generated workflows are children of the invoking task;
  - each AI service span is a child of the generated task that ran its agent.
- Negative control: with `quarkus.flow.otel.task-span-current=false`, all 4 assertions fail (18 spans in 9 traces). This shows the test detects the bug.
- Integration test: `HttpCallTaskSpanIT` runs two workflows of HTTP call tasks, one sequential and one fork, 10 times each. It asserts that no HTTP client span is ever attached to a task span. It fails when HTTP tasks are not excluded from the proxy.

## Known limitations

- **Asynchronous call tasks** (HTTP, OpenAPI, gRPC, A2A, AsyncAPI, MCP) are not made children of their task span. They behave as before this change:
  - When the workflow is started from an incoming request, their client spans are children of that request's server span.
  - When the workflow is started directly (`flow.instance(..).start()`), their client spans start their own trace.

  Fixing this needs the context carried into the asynchronous part of those executors. It is tracked as a follow-up; see [PR #1065 discussion](https://github.com/quarkiverse/quarkus-flow/pull/1065#issuecomment-6082482137).

## Related

- **[OpenTelemetry Trace Propagation Across CloudEvent-Triggered Workflow Execution](2026-08-28-opentelemetry-trace-propagation-design.md) ([#908](https://github.com/quarkiverse/quarkus-flow/issues/908)).** Gives a workflow *root* its parent from the incoming CloudEvent's `traceparent`. This ADR covers what happens after that point: context inside a running instance, and between a workflow and the agentic workflows it generates. The two are complementary. With both, a CloudEvent-triggered workflow that invokes agents lands in the producer's trace end to end.
- **[#1013](https://github.com/quarkiverse/quarkus-flow/issues/1013) (log correlation).** Partly helped, not addressed. Quarkus's OTel context storage copies the current span into the logging MDC, so logs written by task code should now carry the task's `traceId`/`spanId`. Flow's own lifecycle logs, which are what #1013 is about, are emitted from listeners outside the task body and are unchanged.
- **[#1040](https://github.com/quarkiverse/quarkus-flow/issues/1040) (trace continuity after JVM restart).** Not addressed. `FlowContextPropagator` snapshots are in-memory and thread-bound, and the per-agent snapshots live in the `AgenticScope`'s transient execution context, so none of them survive persistence or a restart.

## References

- [#1056](https://github.com/quarkiverse/quarkus-flow/issues/1056)
- Serverless Workflow SDK `CallableTaskProxyBuilder` (`serverlessworkflow-impl-core` 7.35.2.Final)
- LangChain4j `dev.langchain4j.agentic.observability.AgentListener` (1.14.1)
