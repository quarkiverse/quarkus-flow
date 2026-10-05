# Spring StateMachine → Quarkus Flow (Order Lifecycle)

A side-by-side example for teams coming from **Spring StateMachine**. It models the
classic **order lifecycle** state machine as a [Quarkus Flow](https://docs.quarkiverse.io/quarkus-flow/dev/)
workflow, so you can see how each Spring StateMachine concept maps to the Flow Java DSL.

> For the full narrative — concept-by-concept mapping, a before/after of the *same*
> lifecycle on both frameworks, and a FAQ — see the companion migration guide
> [**From Spring StateMachine to Quarkus Flow**](https://zanini.biz/2026/10/02/from-spring-statemachine-to-quarkus-flow-a-maintained-open-source-path-for-state-driven-apps/).

---

## The state machine

```
          placeOrder
   NEW ───────────────▶ AWAITING_PAYMENT
                               │  PAYMENT event
                               ▼
                         (guard: approved?)
                          │            │
                   approved            rejected
                          ▼            ▼
                        PAID        CANCELLED (end)
                          │ fulfillOrder
                          ▼
                   AWAITING_SHIPMENT
                          │  SHIPMENT event
                          ▼
                      DELIVERED (end)
```

## Concept mapping

| Spring StateMachine                         | Quarkus Flow (this example)                      |
|---------------------------------------------|--------------------------------------------------|
| State + entry action                        | `function("placeOrder", this::placeOrder, …)`    |
| Event + waiting state                       | `listen("awaitPayment", toOne(consumed(type)…))` |
| Guard + choice pseudo-state                 | `switchWhenOrElse(PaymentEvent::approved, …)`    |
| Action (code on a state/transition)         | a `function(...)` **call task** → a Java method  |
| End state                                   | `.then(FlowDirectiveEnum.END)`                   |
| `StateMachineListener`                      | `WorkflowExecutionListener` (workflow + task hooks) |
| Interceptor (veto/mutate before a step)     | an explicit `switchWhen` guard or `try`/`catch` task |
| Retries / `onError`                         | built-in retry policies (constant/linear/exponential) + `try`/`catch` |
| Suspend / resume / cancel                   | `instance.suspend()` / `resume()` / `cancel()`   |
| State persistence                           | Redis / JPA / MVStore (see persistence docs)     |

The headline difference: a Spring StateMachine **action** is code bolted onto a
state. In Quarkus Flow it is a **first-class task** in the workflow that calls a
plain Java method — explicit, reusable, and unit-testable on its own.

See [`OrderLifecycleWorkflow`](src/main/java/org/acme/ssm/OrderLifecycleWorkflow.java).

---

## Why this matters

Beyond the 1:1 mapping, moving here buys you things Spring StateMachine either froze or never had:

- **A maintained, Apache-2.0 foundation.** Spring StateMachine's open-source line ends at
  `4.0.x`; future work is commercial-only (Tanzu Spring). Quarkus Flow is actively developed
  and fully open source.
- **An open CNCF specification, not a product.** Workflows target the
  [Open Workflow Specification](https://github.com/open-workflow-specification/specification)
  (successor to Serverless Workflow) — no single vendor can relicense the format out from under you.
- **Safer persistence.** Spring StateMachine's Kryo-based persistence was exposed by
  [CVE-2026-41862](https://spring.io/security/cve-2026-41862/) (CVSS 8.8 — deserialization
  without a class allowlist → remote code execution). Quarkus Flow serializes the workflow model
  as JSON (Jackson) into a *declared target type*, a narrower, more predictable attack surface.
- **Durability, retries, suspend/resume, and tracing are built in** — opt-in, and with no separate
  orchestration cluster to operate.

---

## Run it

### Prerequisites

- Java 17+ and Maven
- Docker (Quarkus Dev Services auto-provisions Kafka for the `listen`/`emit` events)

### Start (contributors building from `main`)

```bash
# from the repo root, install SNAPSHOTs once
./mvnw clean install -DskipTests

# then run this example
cd examples/spring-statemachine-migration
./mvnw quarkus:dev
```

### Drive the machine

```bash
# 1) Start an order → enters AWAITING_PAYMENT, returns an instanceId
curl -s -X POST localhost:8080/orders \
  -H 'Content-Type: application/json' \
  -d '{"orderId":"ORDER#1","customer":"alice","amount":42.0}'
# => {"instanceId":"<id>"}

# 2) Approve payment → guard routes to PAID, enters AWAITING_SHIPMENT
curl -X POST localhost:8080/orders/<id>/payment \
  -H 'Content-Type: application/json' \
  -d '{"orderId":"ORDER#1","approved":true,"reference":"PAY-1"}'

# 3) Dispatch shipment → reaches DELIVERED (end)
curl -X POST localhost:8080/orders/<id>/shipment \
  -H 'Content-Type: application/json' \
  -d '{"orderId":"ORDER#1","carrier":"DHL","trackingId":"TRK-9"}'
```

Rejecting the payment (`"approved":false`) routes the guard to the `CANCELLED` end state.
Watch the `[NEW] / [PAID] / [DELIVERED] / [CANCELLED]` log lines to follow the transitions.

## Test it (no Docker)

The test swaps Kafka for the SmallRye in-memory connector, so it runs without Docker:

```bash
./mvnw test
```

See [`OrderLifecycleWorkflowTest`](src/test/java/org/acme/ssm/OrderLifecycleWorkflowTest.java).
