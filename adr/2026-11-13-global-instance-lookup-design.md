# Workflow-Scoped Instance Discovery (Cross-Shard Lookup) - Design Specification

**Date:** 2026-11-13
**Status:** Accepted
**Issue:** [#1161](https://github.com/quarkiverse/quarkus-flow/issues/1161)
**Related:** [kubesmarts/logic-operator#39](https://github.com/kubesmarts/logic-operator/issues/39) (the consumer), [2026-05-05 Workflow Runner REST API design](2026-05-05-workflow-runner-rest-api-design.md)

> Naming note: the discovery resource name (`discovery`) and the response type name (`InstanceLocation`) are provisional, pending confirmation.

## Context

The runner partitions instance state by `applicationId` — under `durable-kubernetes`, `applicationId` equals the Kubernetes Lease holder identity and therefore the owning pod. Only that pod services the instance, and every existing read path is scoped to the **local pod's shard** (JPA's `readWorkflowInfo(definition, instanceId)` pins `(instanceId, this-pod-applicationId)`; Redis stores instances under `{applicationId}:{namespace}:{name}:{version}:{instanceId}`).

**The driver.** The workflow-gateway (logic-operator#39) routes instance-addressed operations (suspend/cancel/resume) directly to the pod holding the instance's lease: `instanceId → applicationId → pod`. It caches `instanceId → applicationId` per replica. On a **cold cache** (restart, scale-out, or an instance started through another replica) it must recover that mapping from the source of truth. Having the gateway open a database connection was rejected (persistence is pluggable, the store schema is an internal runtime detail on a separate release cycle, and a singleton infra-namespace gateway holding credentials to every store is an unacceptable surface). So the runtime must answer the lookup over HTTP.

**Isolation requirement.** A single database is shared by many runners, each serving its **own** `LogicFlowDefinition`s. A lookup by `instanceId` alone could return — and leak — an instance created by a *different* runner that shares the database. The lookup must therefore be scoped by the full workflow id `(namespace, name, version)`.

## Decision

Expose a read-only, workflow-scoped discovery endpoint on the runner that resolves an instance across the shards of the runtime serving that workflow, and leave everything else untouched.

### 1. A new workflow-scoped discovery resource — `/instances` is left alone

Add a new JAX-RS resource in `runner/app`:

```
GET /q/flow/{namespace}/{name}/{version}/discovery/{instanceId}
```

It returns an `InstanceLocation` carrying `namespace`, `name`, `version`, `instanceId`, `applicationId`, `status`, `startedAt` — a lightweight metainfo record with **no workflow payload**, so the read needs neither the `WorkflowDefinition` nor the blob/hash machinery. `404` when the instance is not found (absent or terminal — terminal rows are purged on completion).

The existing `GET /q/flow/instances` (local, in-memory, this-pod active registry) and the instance operations (`exec`, `suspend`, `cancel`, `resume`) are **unchanged**. `ActiveInstancesResponse` and `InstanceSnapshot` are not modified. The new resource is named to declare a **cluster-wide** lookup, so the existing operations read as "local" to an already-identified instance.

### 2. A workflow-scoped, cross-shard lookup in the persistence backends

The persistence layer gains a lookup keyed by `(namespace, name, version, instanceId)` that returns the owning `applicationId` plus the metainfo above — searching **across shards** but restricted to the given workflow:

- **JPA:** `WHERE key.instanceId = ? AND workflowNamespace = ? AND workflowName = ? AND workflowVersion = ?` (drops the local-`applicationId` pin that `readWorkflowInfo` applies).
- **Redis:** a deterministic discovery-index hash keyed as `instanceId:{instanceId}:{namespace}:{name}:{version}` and storing `applicationId`. The index is written with new instance data and deleted with the instance.

When no durable store is configured (ephemeral mode, or the per-pod `mvstore`), the runner falls back to the in-memory active registry. This is a deliberate, documented limitation: an ephemeral **multi-pod** deployment cannot resolve cross-pod, acceptable because ephemeral deployments are expected to be single-replica.

*(Placement: the contract — `InstanceLocator` + `InstanceLocation` — lives in a dedicated, **bean-free** `persistence-spi` module, so the base (backendless) runner can depend on it without pulling in persistence beans. `persistence-common` depends on `persistence-spi`; the backends (`jpa`, `redis`) depend on `persistence-common`; the base runner depends on `persistence-spi` **only** and obtains the implementation via CDI `Instance<InstanceLocator>`, treating an unsatisfied injection as "no durable backend → 404". The `-standard`/`-messaging` runner flavors bundle a backend, which supplies the bean. The actual persistence SPI used for restore — `PersistenceInstanceOperations` etc. — remains upstream in the serverlessworkflow SDK and is out of scope here.)*

### 3. Isolation by workflow id

Because the database is shared across runners that each own distinct definitions, scoping the query to `(namespace, name, version)` guarantees the resolved instance was created by the runtime running **that** definition; `applicationId` then pins the owning pod within that runtime. This rests on `(namespace, name, version)` being unique to a single runtime — which holds in the operator, where `LogicFlowDefinition` coordinates are unique. (If the same coordinates ever ran in two clusters sharing one database, workflow-id scoping alone would not separate them; that is explicitly not the deployment model.)

### 4. Redis uses a discovery index

The Redis locator reads a deterministic index keyed by `(namespace, name, version, instanceId)`, avoiding a keyspace scan. New instances receive an index entry in the same Redis transaction as their instance data; instance removal deletes that entry. Existing instances created before this index is deployed are not discoverable through Redis until they terminate. This compatibility gap is accepted for the current project; there is no scan fallback or backfill.

### 5. Authorization

The resource carries `namespace`/`name`/`version` in its path, so it follows the same namespace-authorization pattern as `exec`/`suspend`/`cancel`/`resume` (roles `admin`/`invoker`; namespace access validated from the path), returning `404` when the instance is absent.

## What is deliberately out of scope

- Repurposing `/instances` or changing `ActiveInstancesResponse` / `InstanceSnapshot`.
- The gateway-side consumption (logic-operator#39): calling this endpoint via the runtime ClusterIP Service on a cache miss, caching the `applicationId`, resolving `applicationId → pod` live, and handling pod-unreachable / stale-lease.
- Backfilling discovery-index entries for existing Redis instances.
- Redis Cluster co-location (hash-tagging) and rebalancer-driven index maintenance.
