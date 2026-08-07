# Align HTTP and gRPC Client Resolution with OIDC

**Status:** Proposed  
**Date:** 2026-07-31  
**Deciders:** Quarkus Flow Core Team  
**Supersedes:** Partially implements [ADR 2026-07-07 Unified Client Naming Pattern](./2026-07-07-unified-client-naming-pattern.md)

## Context

ADR 2026-07-07 established a unified client naming pattern with progressive specificity for HTTP, gRPC, and OIDC. 
The OIDC module (`OidcConfigResolver`) has been fully implemented following that pattern, 
but the HTTP and gRPC modules remain on their legacy resolution logic.

### Current State

**OIDC (reference implementation)** — `OidcConfigResolver` + `OidcNamingConvention`

Accepts `WorkflowDefinitionId` (namespace, name, version) and resolves through 7 levels:

| Priority | Key pattern | Example |
|----------|-------------|---------|
| 1 | `namespace:name:version.task.taskName` | `acme:orders:1.0.0.task.payment` |
| 2 | `namespace:name.task.taskName` | `acme:orders.task.payment` |
| 3 | `name.task.taskName` | `orders.task.payment` |
| 4 | `namespace:name:version` | `acme:orders:1.0.0` |
| 5 | `namespace:name` | `acme:orders` |
| 6 | `name` | `orders` |
| 7 | `authPolicyName` (OIDC-specific) | `keycloak` |

Config shape: flat `Map<String, ClientOverrideConfig> client()` under `quarkus.flow.oidc`.

**HTTP** — `RoutingNameResolver`

Accepts plain `String workflowName` (only `getDocument().getName()`, no namespace or version). Resolves through 2 levels:

| Priority | Key pattern | Example |
|----------|-------------|---------|
| 1 | `workflow.<wfName>.task.<taskName>.name` | `workflow.orders.task.payment.name` |
| 2 | `workflow.<wfName>.name` | `workflow.orders.name` |

Config shape: nested `Map<String, WorkflowRoutingConfig> workflow()` under `quarkus.flow.http.client`.

**gRPC** — `GrpcChannelProvider.resolveClientName`

Accepts `WorkflowDefinitionId` but resolves through only 3 override levels plus 1 existence fallback:

| Priority | Key pattern | Example |
|----------|-------------|---------|
| 1 | `namespace:name:version:taskName` | `acme:grpcGreeting:0.0.1:greet` |
| 2 | `namespace:name:version` | `acme:grpcGreeting:0.0.1` |
| 3 | `namespace:name` | `acme:grpcGreeting` |
| 4 | default channel `flowGrpc` (existence check) | — |

Config shape: flat `Map<String, ClientOverrideConfig> client()` under `quarkus.flow.grpc`.

### Problems

1. **Missing specificity levels** — HTTP has no namespace/version awareness at all. gRPC is missing task-level medium/short and workflow-level short keys.
2. **Inconsistent key format** — gRPC uses `:` as the task separator (`ns:name:ver:task`), while OIDC uses `.task.` (`ns:name:ver.task.taskName`). The `.task.` separator is unambiguous because workflow names/versions should not contain `.task.`.
3. **HTTP uses plain strings** — `RoutingNameResolver` and `HttpClientProvider.clientFor` receive `String workflowName` extracted from `getDocument().getName()`, discarding namespace and version. `WorkflowDefinitionId` is readily available at both call sites (`WorkflowApplicationCreator:233`, `FaultToleranceProvider:65`) via `workflowContextData.definition().id()` but is not used.
4. **Different config shapes** — HTTP uses a nested map structure (`workflow → task`), while OIDC and gRPC use flat maps with composite keys. This forces users to learn different config patterns for the same conceptual operation.
5. **`RoutingNameResolver` has zero test coverage** — any refactoring of the HTTP resolver is effectively untested today.

## Decision

Align HTTP and gRPC client resolution to follow the same progressive specificity cascade implemented by `OidcConfigResolver`, as specified in ADR 2026-07-07.

### Resolution Cascade

All three modules will share the same 6-level resolution order (most to least specific):

| Priority | Scope | Key format |
|----------|-------|------------|
| 1 | Task full | `namespace:name:version.task.taskName` |
| 2 | Task medium | `namespace:name.task.taskName` |
| 3 | Task short | `name.task.taskName` |
| 4 | Workflow full | `namespace:name:version` |
| 5 | Workflow medium | `namespace:name` |
| 6 | Workflow short | `name` |

OIDC retains its additional level 7 (`authPolicyName`). gRPC retains its default `flowGrpc` channel fallback after the 6 override levels.

### Config Shape

HTTP retains `workflow()` as the config property name with a flat `Map<String, ClientOverrideConfig>` map. gRPC uses `client()`. The nested `WorkflowRoutingConfig` / `TaskRoutingConfig` structure is replaced with a flat map keyed by composite identifiers, but the `workflow` property name is preserved for backward compatibility.

### Key Format

All modules will use `:` as the namespace/version separator and `.task.` as the task separator, matching `OidcNamingConvention`.

### API Surface

`HttpClientProvider.clientFor` and `RoutingNameResolver.resolveName` (or their replacement) will accept `WorkflowDefinitionId` instead of plain `String workflowName`. Call sites in `WorkflowApplicationCreator` and `FaultToleranceProvider` will be updated to pass `definition().id()`.

### Shared Naming Convention

`OidcNamingConvention` will be extracted to a shared location (e.g., `core/runtime`) and reused by HTTP, gRPC, and OIDC. The class contains only static key-generation methods with no OIDC-specific logic — the `Oidc` prefix is a historical artifact.

## Changes by Module

### `core/runtime` (HTTP)

- **Config**: Keep `FlowHttpConfig.workflow()` as the config property name (flat `Map<String, ClientOverrideConfig>`). `FlowHttpConfig` retains `named()` for actual HTTP client definitions and all other HTTP-specific settings. No property rename needed — backward compatibility is preserved.
- **Resolver**: Rewrite `RoutingNameResolver` (or replace it) to accept `WorkflowDefinitionId` and follow the 6-level cascade using the shared naming convention.
- **Call sites**: Update `WorkflowApplicationCreator.injectHttpClientProvider` (line 233) and `FaultToleranceProvider` (line 65) to pass `workflowContextData.definition().id()` instead of `getDocument().getName()`.
- **Tests**: Add unit tests for the resolver covering all 6 specificity levels, null/blank task names, and fallback behavior.

### `grpc/runtime`

- **Config**: `FlowGrpcConfig.client()` already uses a flat map — no structural change needed. Only the documented key format changes (`:` task separator → `.task.` task separator).
- **Resolver**: Update `GrpcChannelProvider.resolveClientName` to use the shared naming convention and add the 3 missing levels (task medium, task short, workflow short). The default `flowGrpc` channel fallback remains after the 6 override levels.
- **Tests**: Update `GrpcChannelProviderPriorityOrderTest` to cover all 6 override levels plus the default channel fallback.

### `oidc/runtime`

- **No changes** to `OidcConfigResolver` or its resolution logic.
- **Naming convention**: `OidcNamingConvention` moves to `core/runtime` and is renamed (e.g., `ClientNamingConvention`). The OIDC module updates its imports.

## Consequences

### Benefits

1. **Consistency** — All three client types follow the same resolution order and key format. Users learn once.
2. **Full identity resolution for HTTP** — HTTP gains namespace and version awareness, enabling multi-tenant and version-specific routing.
3. **Simpler mental model** — One flat map with composite keys instead of two different config shapes.
4. **Shared code** — Single naming convention utility eliminates duplication and prevents future divergence.

### Drawbacks

1. **No breaking change for HTTP config** — The `workflow` property name is preserved. Only the internal resolution logic changes; existing `quarkus.flow.http.client.workflow.<name>.*` properties continue to work.
2. **Breaking change for gRPC keys** — The task separator changes from `:` to `.task.`, requiring config migration for anyone using task-level gRPC overrides (the `namespace:name:version:taskName` format).
3. **Quoting in config files** — Composite keys containing `:` must be quoted in `application.properties` (e.g., `"acme\:orders\:1.0.0"`). This is standard SmallRye Config behavior but is less ergonomic than the short `name`-only key.

## Migration

### HTTP

No property rename needed — the `workflow` key is preserved:

| Before | After |
|--------|-------|
| `quarkus.flow.http.client.workflow.orders.name=secureA` | No change |
| `quarkus.flow.http.client.workflow."orders.task.payment".name=secureB` | No change |

### gRPC

| Before | After |
|--------|-------|
| `quarkus.flow.grpc.client."org.acme:grpcGreeting:0.0.1:greet".name=x` | `quarkus.flow.grpc.client."org.acme:grpcGreeting:0.0.1.task.greet".name=x` |
| `quarkus.flow.grpc.client."org.acme:grpcGreeting:0.0.1".name=y` | No change (workflow-level keys unchanged) |
| `quarkus.flow.grpc.client."org.acme:grpcGreeting".name=z` | No change (medium keys unchanged) |
| — | `quarkus.flow.grpc.client.grpcGreeting.name=z` (new: short key now supported) |

## Verification

1. Run existing gRPC tests: `mvn test -f grpc/runtime/pom.xml`
2. Run existing OIDC tests: `mvn test -f oidc/runtime/pom.xml`
3. Run full build with integration tests: `mvn clean install -DskipITs=false`
4. Verify examples that use HTTP/gRPC routing still work after config migration

## Related

- [ADR 2026-07-07 Unified Client Naming Pattern](./2026-07-07-unified-client-naming-pattern.md) — the parent design this ADR implements
- [ADR 2026-07-06 OIDC Client Delegation](./2026-07-06-oidc-client-delegation.md) — the OIDC implementation that serves as the reference
- `OidcConfigResolver` — reference implementation of the 7-level cascade
- `OidcNamingConvention` — key generation utility to be shared
