# Unified Client Naming Pattern for gRPC, HTTP, and OIDC

**Status:** Proposed  
**Date:** 2026-07-07  
**Deciders:** Quarkus Flow Core Team  

## Context

Currently, Quarkus Flow has inconsistent client routing configuration across different client types:

- **HTTP**: Uses `quarkus.flow.http.client.workflow.<name>.task.<taskName>.name` (workflow name only, no namespace/version)
- **gRPC**: Uses `quarkus.flow.grpc.client."<namespace>:<name>:<version>:<taskName>".name` (composite keys with colons)
- **OIDC**: Uses `quarkus.flow.oidc.client."<namespace>:<name>:<version>:<taskName>".name` (composite keys with colons)

This inconsistency creates confusion and poor developer experience. Users must learn three different config patterns for the same conceptual operation: routing a workflow/task to a named client.

Additionally, the current patterns have UX issues:
- Forcing users to specify auto-generated namespaces (e.g., `org-acme`) they didn't explicitly set
- No progressive specificity (must use full keys even for simple cases)
- Different resolution orders across client types

## Decision

We will **unify all client routing configuration** to use a single, consistent pattern with progressive specificity:

```properties
quarkus.flow.<clientType>.<workflowId>[.task.<taskName>].name=<clientName>
```

Where `<clientType>` is one of: `http.client`, `grpc`, `oidc`

Where `<workflowId>` has **three levels of specificity**:

1. **Short** (99% use case): `<name>`
2. **Medium** (namespaced): `"<namespace>:<name>"`
3. **Full** (versioned): `"<namespace>:<name>:<version>"`

### Examples

#### HTTP Client
```properties
# Short (99% case)
quarkus.flow.http.client.orders.task.payment.name=paymentHttpClient
quarkus.flow.http.client.orders.name=ordersHttpClient

# Medium (multiple namespaces)
quarkus.flow.http.client."acme\:orders".task.payment.name=acmePaymentClient
quarkus.flow.http.client."contoso\:orders".task.payment.name=contosoPaymentClient

# Full (specific version)
quarkus.flow.http.client."acme\:orders\:1.0.0".task.payment.name=ordersV1Client
quarkus.flow.http.client."acme\:orders\:2.0.0".task.payment.name=ordersV2Client
```

#### gRPC Client
```properties
# Short (99% case)
quarkus.flow.grpc.orders.task.fetchInventory.name=inventoryGrpcChannel
quarkus.flow.grpc.orders.name=ordersGrpcChannel

# Medium (all versions)
quarkus.flow.grpc."acme\:orders".name=acmeGrpcChannel

# Full (specific version)
quarkus.flow.grpc."acme\:orders\:1.0.0".name=ordersV1GrpcChannel
```

#### OIDC Client
```properties
# Short (99% case)
quarkus.flow.oidc.orders.task.payment.name=paymentAuth
quarkus.flow.oidc.orders.name=ordersAuth

# Named policy (OIDC-specific)
quarkus.flow.oidc.keycloak.name=prodKeycloak

# Full (specific version)
quarkus.flow.oidc."acme\:orders\:1.0.0".name=ordersV1Auth
```

## Resolution Algorithm

**Unified resolution order** (same for all client types):

For named policies (OIDC only):
1. `quarkus.flow.oidc.<policyName>.name` (override)
2. Use `<policyName>` directly as client name (default)

For workflows/tasks:
1. Task-level (full): `quarkus.flow.<type>."<namespace>:<name>:<version>".task.<taskName>.name`
2. Task-level (medium): `quarkus.flow.<type>."<namespace>:<name>".task.<taskName>.name`
3. Task-level (short): `quarkus.flow.<type>.<name>.task.<taskName>.name`
4. Workflow-level (full): `quarkus.flow.<type>."<namespace>:<name>:<version>".name`
5. Workflow-level (medium): `quarkus.flow.<type>."<namespace>:<name>".name`
6. Workflow-level (short): `quarkus.flow.<type>.<name>.name`
7. Check if named client exists with workflow ID as name
8. Fallback to default

## Config Structure

All client types will share the same config structure:

```
FlowClientConfig:
  workflow() -> Map<String, WorkflowRoutingConfig>
    - Keys: <name> | <namespace>:<name> | <namespace>:<name>:<version>
    
  WorkflowRoutingConfig:
    - name(): Optional<String>           // Client for all tasks
    - task(): Map<String, TaskRoutingConfig>
    
  TaskRoutingConfig:
    - name(): Optional<String>           // Client for specific task

Examples:
  quarkus.flow.<type>.orders.task.payment.name=paymentClient        (short)
  quarkus.flow.<type>."acme\:orders".name=acmeClient                (medium)
  quarkus.flow.<type>."acme\:orders\:1.0.0".task.payment.name=v1    (full)
```

## Consequences

### Benefits

1. **Consistency**: Same pattern across HTTP, gRPC, and OIDC - learn once, use everywhere
2. **Progressive specificity**: Start simple (`orders`), add namespace/version only when needed
3. **Better UX**: 99% of users never need to know about auto-generated namespaces
4. **Clear semantics**: `.task.<taskName>` always present for task-level config
5. **Easy migration path**: Can support both old and new patterns during transition

### Drawbacks

1. **Breaking change**: Existing configurations will need migration
2. **Migration effort**: Need to update docs, examples, and migration guide
3. **Transition period**: May need to support both patterns temporarily

## Implementation

### Shared Resolution Algorithm

All three client types will use a shared resolution algorithm:

```
Resolution order (most to least specific):

For named policies (OIDC only):
  1. Check config override for policy name
  2. Use policy name directly as client name

For workflows/tasks:
  1. Try full key: namespace:name:version.task.taskName
  2. Try medium key: namespace:name.task.taskName
  3. Try short key: name.task.taskName
  4. Try full key: namespace:name:version
  5. Try medium key: namespace:name
  6. Try short key: name
  7. Fallback to default behavior

For each key attempt:
  - Check task-level config first
  - Fall back to workflow-level config
```

### Module Updates

**HTTP (`HttpClientProvider`):**
- Update config structure to use `workflow()` map
- Implement progressive specificity resolution
- Add deprecation warnings for old pattern

**gRPC (`GrpcChannelProvider`):**
- Update config structure to use `workflow()` map
- Implement progressive specificity resolution
- Add deprecation warnings for old pattern

**OIDC (`OidcConfigResolver`):**
- Update config structure to use `workflow()` map
- Implement progressive specificity resolution
- Add support for named policy override

## Examples

### Simple Workflow (99% case)

**Workflow DSL:**
```
workflow("orders")
    .tasks(
        http("payment").POST().uri(...),
        grpc("inventory").service("...")
    )
```

**Config (OPTIONAL - override default client names):**
```properties
# HTTP
quarkus.flow.http.client.orders.task.payment.name=paymentHttpClient

# gRPC
quarkus.flow.grpc.orders.task.inventory.name=inventoryChannel

# OIDC
quarkus.flow.oidc.orders.task.payment.name=paymentAuth
```

### Multi-Namespace Workflow

**Workflow DSL:**
```
workflow("orders", "acme")...     // Namespace: acme
workflow("orders", "contoso")...  // Namespace: contoso
```

**Config:**
```properties
# Route acme:orders
quarkus.flow.http.client."acme\:orders".name=acmeHttpClient
quarkus.flow.grpc."acme\:orders".name=acmeGrpcChannel
quarkus.flow.oidc."acme\:orders".name=acmeAuth

# Route contoso:orders
quarkus.flow.http.client."contoso\:orders".name=contosoHttpClient
quarkus.flow.grpc."contoso\:orders".name=contosoGrpcChannel
quarkus.flow.oidc."contoso\:orders".name=contosoAuth
```

### Version-Specific Routing

**Config:**
```properties
# Version 1.0.0 uses legacy clients
quarkus.flow.http.client."acme\:orders\:1.0.0".name=legacyHttpClient
quarkus.flow.grpc."acme\:orders\:1.0.0".name=legacyGrpcChannel

# Version 2.0.0 uses new clients
quarkus.flow.http.client."acme\:orders\:2.0.0".name=newHttpClient
quarkus.flow.grpc."acme\:orders\:2.0.0".name=newGrpcChannel
```

## Best Practices

### When to Use Routing Configuration

**Routing configuration is OPTIONAL** - it only overrides auto-generated client names. Most workflows don't need it.

**Use routing config when:**
- Sharing one client across multiple workflows (e.g., all workflows use same HTTP client)
- Version-specific routing (v1 → legacy client, v2 → new client)
- Multi-tenant scenarios (namespace-based routing)

**Don't use routing config when:**
- Default behavior works (one client per task/workflow)
- Each workflow/task needs isolated configuration

### Authentication Reuse (OIDC-specific)

**For task-specific auth**, use inline authentication:
```
http("payment").uri(..., oauth2(authority, CLIENT_CREDENTIALS, clientId, secret))
```
→ Creates isolated OIDC client: `namespace:name:version.task.payment`

**For shared auth across tasks**, use named policies:
```
workflow("orders")
    .use(use -> use.authentications(auth -> auth.authentication("keycloak", ...)))
    .tasks(
        http("payment").uri(..., use("keycloak")),
        http("shipping").uri(..., use("keycloak"))  // Reuses same client
    )
```
→ Single shared OIDC client: `keycloak`

**Trade-off:** Inline auth = isolated (one-off), Named policies = shared (common auth)

**1:1 DSL mapping:** This follows the Serverless Workflow specification exactly - the spec provides the reuse primitive (`use`), we honor it. No framework magic.

## Documentation Updates

1. **Reference Docs**: Update all three client type docs to show new pattern
2. **Migration Guide**: Create comprehensive migration guide with examples
3. **Blog Post**: Announce change, explain benefits, show examples
4. **Examples Repo**: Update all examples to use new pattern
5. **Best Practices Guide**: Document when to use routing config vs defaults, inline vs named auth

## Related

- Issue: #677 (Align HTTP/gRPC routing configuration)
- PR: #692 (Initial HTTP routing alignment)
- Future PR: Implement unified pattern across all three client types

## References

- [PR #692: Align and Update workflow and task routing configuration](https://github.com/quarkiverse/quarkus-flow/pull/692)
- [Issue #677: HTTP routing configuration enhancement](https://github.com/quarkiverse/quarkus-flow/issues/677)
- [Issue #731: OIDC client delegation improvements](https://github.com/quarkiverse/quarkus-flow/issues/731)
- [ADR: OIDC Client Delegation Implementation Plan](./2026-07-06-oidc-client-delegation.md) - Implements unified naming pattern for OIDC
