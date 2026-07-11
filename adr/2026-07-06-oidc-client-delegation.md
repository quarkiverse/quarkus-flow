# OIDC Client Delegation Implementation Plan

> **Configuration Pattern:** This implementation uses the **unified client naming pattern** defined in `2026-07-07-unified-client-naming-pattern.md`. OIDC routing configuration follows the same structure as HTTP and gRPC for consistency.

**Goal:** Delegate OIDC client management to `quarkus-oidc-client` by generating build-time config for SPEC workflows, using CDI events for runtime workflows, and implementing a simple name-based client registry.

**Architecture:**
- **SPEC Workflows (build-time)**: Generate full `quarkus.oidc-client.<name>.*` config → `quarkus-oidc-client` creates clients automatically
- **SOURCE/Runner Workflows (runtime)**: Fire CDI event (`WorkflowRegisteredEvent`) → listener creates clients via `OidcClients.newClient()`
- **Named Auth Policies**: Extract `use("name")` references → OIDC clients named `"name"` (auto-created, config OPTIONAL for overrides)
- **Inline Task Auth**: Generate composite client name `namespace:name:version.task.taskName` → create lazily on first use
- **User Override**: Uses unified routing config pattern with progressive specificity (see below)

**Unified Routing Config Pattern:**
```properties
# OPTIONAL - only needed to override auto-generated client names

# Short form (99% case - no namespace/version)
quarkus.flow.oidc.orders.task.payment.name=customPaymentAuth
quarkus.flow.oidc.orders.name=customOrdersAuth

# Medium form (namespaced - all versions)
quarkus.flow.oidc."acme\:orders".task.payment.name=acmePaymentAuth

# Full form (version-specific)
quarkus.flow.oidc."acme\:orders\:1.0.0".task.payment.name=ordersV1Auth

# Named policy override (OIDC-specific)
quarkus.flow.oidc.keycloak.name=prodKeycloak
```

**Tech Stack:**
- Build-time: `RunTimeConfigurationDefaultBuildItem` for SPEC workflows
- Runtime: CDI Events (`@Observes WorkflowRegisteredEvent`) for SOURCE/Runner workflows
- `quarkus-oidc-client` (`OidcClients`, `OidcClientConfig`)
- Serverless Workflow DSL authentication policies
- Unified client routing pattern (consistent with HTTP/gRPC)

## Global Constraints

- Java 17+ (project baseline)
- Quarkus extension runtime/deployment separation must be maintained
- All existing integration tests must pass without modification
- **BREAKING CHANGE**: Config structure updated to use unified naming pattern (`workflow()` instead of `client()`)
- User-configured OIDC clients (`quarkus.oidc-client.*`) always take precedence
- Support both named auth policies (`use("name")`) and inline task auth
- OAuth2 authentication properties support literal values and property expressions (`${config.key}`), not JQ expressions
- Routing config is **OPTIONAL** - auto-generation works by default, config only needed for overrides

## Design Philosophy: Authentication Reuse

**Inline authentication creates task-specific OIDC clients** (no automatic sharing):

```
workflow("orders")
    .tasks(
        http("payment").uri(..., oauth2(authority, CLIENT_CREDENTIALS, clientId, secret)),
        http("shipping").uri(..., oauth2(authority, CLIENT_CREDENTIALS, clientId, secret))
    )
```

**Result:** Two separate OIDC clients created:
- `org-acme:orders:1.0.0.task.payment`
- `org-acme:orders:1.0.0.task.shipping`

Even with identical credentials, each inline auth gets its own isolated client. This is **by design** - no magic deduplication.

---

**For authentication reuse across tasks, use named policies:**

```
workflow("orders")
    .use(use -> use.authentications(auth ->
        auth.authentication("keycloak", a -> a.oauth2(...))))
    .tasks(
        http("payment").uri(..., use("keycloak")),
        http("shipping").uri(..., use("keycloak"))  // Reuses same client
    )
```

**Result:** Single shared OIDC client:
- Client name: `keycloak`
- Reused by both `payment` and `shipping` tasks

---

**Trade-off:**
- **Inline auth**: Isolated, task-specific → **use for one-off authentication**
- **Named policies**: Shared, reusable → **use for common authentication across tasks/workflows**

**Our responsibility**: Provide predictable client creation tools  
**User's responsibility**: Choose the right pattern (inline vs named) for their reuse needs

**Key insight**: This is a **1:1 mapping with the Serverless Workflow DSL**. The specification already provides the reuse primitive (`use`). We simply honor it:
- Inline auth in DSL → isolated OIDC client in runtime
- Named policy in DSL → shared OIDC client in runtime

No hidden optimization, no magic deduplication - just pure DSL semantics. Users who understand the spec already understand our behavior.

---

## Implementation Tasks

### Task 1: Create CDI Event Infrastructure

**Goal:** Enable workflow registration to trigger CDI events that OIDC (and other modules) can observe.

**Files to create:**
- `core/runtime/.../events/WorkflowRegisteredEvent.java` - CDI event payload
- `core/runtime/.../recorders/WorkflowRegistrationService.java` - Service that wraps workflow registration and fires events

**Files to modify:**
- `core/runtime/.../recorders/WorkflowDefinitionRecorder.java` - Use registration service instead of direct SDK call
- `runner/runtime/.../WorkflowDefinitionRuntimeLoader.java` - Use registration service for runtime-loaded workflows

**What it does:**
- `WorkflowRegisteredEvent`: Simple CDI event containing the registered `Workflow` instance
- `WorkflowRegistrationService`: Wraps `WorkflowApplication.workflowDefinition()`, fires CDI event after registration
- Both recorders updated to inject and use the service

**Why:** Allows OIDC module to observe workflow registration and create clients for SOURCE/Runner workflows at runtime.

---

### Task 2: Extract OAuth2/OIDC Policies from Workflows

**Goal:** Create utility to scan workflows and extract all OAuth2/OIDC authentication policies.

**Files to create:**
- `oidc/deployment/.../OidcPolicyExtractor.java` - Extracts auth policies from `Workflow` instances

**What it does:**
- Scans `workflow.use.authentications` map for named policies with OAuth2/OIDC
- Scans all tasks for inline `EndpointConfiguration.authentication` with OAuth2/OIDC
- Returns structured data: `Map<String, OAuth2Policy>` for named policies, `List<InlineAuth>` for task-level auth
- Handles both `oauth2` and `openIdConnect` authentication types

**Algorithm:**
```
extractPolicies(Workflow):
  namedPolicies = {}
  inlineAuths = []
  
  // Extract named policies from workflow.use.authentications
  for (name, policy) in workflow.use.authentications:
    if isOAuth2OrOIDC(policy):
      namedPolicies[name] = policy
  
  // Extract inline auth from tasks
  for task in workflow.tasks:
    if task.endpoint.authentication exists and isOAuth2OrOIDC:
      inlineAuths.add({
        taskName: task.name,
        policy: task.endpoint.authentication
      })
  
  return (namedPolicies, inlineAuths)
```

---

### Task 3: Generate Build-Time Config for SPEC Workflows

**Goal:** Generate `quarkus.oidc-client.<name>.*` properties for SPEC workflows at build time.

**Files to modify:**
- `oidc/deployment/.../FlowOidcProcessor.java` - Add build step that generates config

**What it does:**
- Use `WorkflowsPathBuildItem` to get all SPEC workflow descriptors
- For each workflow:
  - Extract OAuth2/OIDC policies (named + inline)
  - Generate OIDC client config properties
  - Emit `RunTimeConfigurationDefaultBuildItem`
- Named policies → `quarkus.oidc-client.<policyName>.*`
- Inline auth → `quarkus.oidc-client.<namespace:name:version.task.taskName>.*`

**Property generation:**
```
For named policy "keycloak":
  quarkus.oidc-client.keycloak.auth-server-url=${authority}
  quarkus.oidc-client.keycloak.discovery-enabled=false
  quarkus.oidc-client.keycloak.token-path=${tokenPath}
  quarkus.oidc-client.keycloak.grant-type=${grant}
  quarkus.oidc-client.keycloak.client-id=${clientId}
  quarkus.oidc-client.keycloak.credentials.secret=${clientSecret}
  quarkus.oidc-client.keycloak.credentials.client-secret.method=${method}
  quarkus.oidc-client.keycloak.scopes=${scopes}
  quarkus.oidc-client.keycloak.audience=${audiences}

For inline auth in task "payment":
  quarkus.oidc-client."org-acme:orders:1.0.0.task.payment".auth-server-url=${authority}
  ... (same structure as named policy)
```

**Why:** SPEC workflows can have native Quarkus OIDC clients created at build time - zero runtime overhead.

---

### Task 4: Create Runtime OIDC Client Registry

**Goal:** Manage dynamically-created OIDC clients (SOURCE/Runner workflows, inline auth).

**Files to create:**
- `oidc/runtime/.../OidcClientRegistry.java` - Thread-safe registry for runtime-created clients

**What it does:**
- `getOrCreateClient(name, configSupplier)`: Get existing or create new client
- Uses `ConcurrentHashMap<String, OidcClient>` internally
- Delegates to `OidcClients.newClient()` for actual creation
- Handles lifecycle (`@PreDestroy` cleanup)

**API:**
```
interface OidcClientRegistry:
  getOrCreateClient(name: String, config: () -> OidcClientConfig): OidcClient
  getClient(name: String): Optional<OidcClient>
  close(): void
```

**Why:** `OidcClients.newClient()` creates a client but doesn't register it in the static map, so we need our own registry for runtime-created clients.

---

### Task 5: Create Runtime Workflow Registration Listener

**Goal:** Observe `WorkflowRegisteredEvent` and create OIDC clients for SOURCE/Runner workflows.

**Files to create:**
- `oidc/runtime/.../OidcWorkflowRegistrationListener.java` - CDI observer for workflow registration

**What it does:**
- Observes `WorkflowRegisteredEvent` via `@Observes`
- Extracts OAuth2/OIDC policies from the workflow
- For each named policy: create OIDC client via `OidcClientRegistry.getOrCreateClient(policyName, config)`
- For inline auth: clients are created lazily when first used (see Task 6)

**Pseudo-code:**
```
@ApplicationScoped
class OidcWorkflowRegistrationListener:
  @Inject OidcClientRegistry registry
  @Inject FlowOidcConfig config
  
  void onWorkflowRegistered(@Observes WorkflowRegisteredEvent event):
    workflow = event.getWorkflow()
    (namedPolicies, inlineAuths) = extractPolicies(workflow)
    
    for (name, policy) in namedPolicies:
      // Check if user override exists
      if not userConfiguredClient(name):
        clientConfig = buildOidcClientConfig(policy)
        registry.getOrCreateClient(name, () -> clientConfig)
```

**Why:** SOURCE/Runner workflows are only known at runtime, so we create their clients on-demand when they're registered.

---

### Task 6: Update OidcClientAuthProvider

**Goal:** Use unified routing config pattern and delegate to registry/named clients.

**Files to modify:**
- `oidc/runtime/.../OidcClientAuthProvider.java` - Update resolution logic

**What it does:**
- Use `OidcConfigResolver` to check for routing config override
- For named policies:
  - Check routing override: `quarkus.flow.oidc.<policyName>.name`
  - If override exists, use that client name
  - Otherwise use policy name directly
  - Get client via `OidcClients.getClient(name)` (Quarkus registry)
- For inline auth:
  - Generate composite name: `namespace:name:version.task.taskName`
  - Check routing override (progressive specificity)
  - If override exists, use that client name
  - Otherwise use composite name
  - Get or create via `OidcClientRegistry.getOrCreateClient(name, config)`

**Resolution flow:**
```
resolveClient(workflowId, taskName, authPolicyName, inlinePolicy):
  // Named policy path
  if authPolicyName:
    override = resolver.resolve(workflowId, taskName, authPolicyName)
    clientName = override.orElse(authPolicyName)
    return OidcClients.getClient(clientName)  // Pre-created
  
  // Inline auth path
  compositeName = generateCompositeName(workflowId, taskName)
  override = resolver.resolve(workflowId, taskName, null)
  clientName = override.orElse(compositeName)
  return registry.getOrCreateClient(clientName, () -> buildConfig(inlinePolicy))
```

---

### Task 7: Update FlowOidcConfig

**Goal:** Use unified routing config structure.

**Files to modify:**
- `oidc/runtime/.../FlowOidcConfig.java` - Update config interface

**What changes:**
- Replace `Map<String, ClientOverrideConfig> client()` with `Map<String, WorkflowRoutingConfig> workflow()`
- Add `WorkflowRoutingConfig` interface with `name()` and `task()` map
- Add `TaskRoutingConfig` interface with `name()`
- Remove `ClientOverrideConfig` (no longer needed)
- Keep `enabled()`, `creationTimeout()`, `connectionTimeout()` as-is

**Structure:**
```
@ConfigMapping(prefix = "quarkus.flow.oidc")
interface FlowOidcConfig:
  enabled(): boolean
  creationTimeout(): Duration
  connectionTimeout(): Duration
  workflow(): Map<String, WorkflowRoutingConfig>
  
  interface WorkflowRoutingConfig:
    name(): Optional<String>
    task(): Map<String, TaskRoutingConfig>
  
  interface TaskRoutingConfig:
    name(): Optional<String>
```

---

### Task 8: Update OidcConfigResolver

**Goal:** Implement progressive specificity resolution (short/medium/full).

**Files to modify:**
- `oidc/runtime/.../OidcConfigResolver.java` - Update resolution algorithm

**What changes:**
- Update to use `FlowOidcConfig.workflow()` map
- Implement three-level specificity:
  1. Try `namespace:name:version` (full)
  2. Try `namespace:name` (medium)
  3. Try `name` (short)
- For each level, check task-level first, then workflow-level
- Named policy override: check for `workflow().get(policyName).name()`

**Algorithm:**
```
resolve(workflowId, taskName, authPolicyName):
  // Named policy override
  if authPolicyName:
    override = resolveWorkflow(authPolicyName, null)
    if override.present: return override
    return empty  // Caller uses policyName directly
  
  // Progressive specificity for workflows
  keys = [
    namespace + ":" + name + ":" + version,  // Full
    namespace + ":" + name,                   // Medium
    name                                       // Short
  ]
  
  for key in keys:
    result = resolveWorkflow(key, taskName)
    if result.present: return result
  
  return empty

resolveWorkflow(workflowId, taskName):
  wfConfig = config.workflow().get(workflowId)
  if not wfConfig: return empty
  
  // Task-level first
  if taskName:
    taskConfig = wfConfig.task().get(taskName)
    if taskConfig and taskConfig.name().present:
      return taskConfig.name()
  
  // Workflow-level fallback
  return wfConfig.name()
```

---

### Task 9: Remove CacheKey and Simplify OidcClientFactory

**Goal:** Remove complex cache key, rely on simple name-based registry.

**Files to modify:**
- `oidc/runtime/.../OidcClientFactory.java` - Simplify to name-based lookups

**Files to delete:**
- `oidc/runtime/.../CacheKey.java` - No longer needed

**What changes:**
- Remove `ConcurrentHashMap<CacheKey, OidcClient> cache`
- Remove `CacheKey` class entirely
- Keep helper methods: `getNamedClient(name)`, timeouts
- Inline client creation moves to `OidcClientRegistry`

**Simplified API:**
```
@ApplicationScoped
class OidcClientFactory:
  @Inject OidcClients oidcClients
  
  getNamedClient(name: String): OidcClient
  clientCreationTimeout(): Duration
  connectionTimeout(): Duration
  namedConnectionTimeout(name: String): Duration
```

---

### Task 10: Update Tests

**Goal:** Verify all paths work (build-time, runtime, named, inline, overrides).

**Tests to add/update:**
- **Build-time config generation** test:
  - SPEC workflow with named policy → verify `quarkus.oidc-client.<name>.*` properties generated
  - SPEC workflow with inline auth → verify composite-named client properties generated
- **Runtime registration** test:
  - SOURCE workflow with named policy → verify client created on registration
  - Runner workflow with inline auth → verify client created lazily
- **Routing override** test:
  - Named policy override: `quarkus.flow.oidc.keycloak.name=prodKeycloak`
  - Task-level override (short): `quarkus.flow.oidc.orders.task.payment.name=customAuth`
  - Task-level override (medium): `quarkus.flow.oidc."acme\:orders".task.payment.name=acmeAuth`
  - Task-level override (full): `quarkus.flow.oidc."acme\:orders\:1.0.0".task.payment.name=v1Auth`
- **Existing integration tests** must pass without modification

---

### Task 11: Update Documentation

**Goal:** Document new behavior, migration path, best practices.

**Files to update:**
- `docs/modules/ROOT/pages/oidc.adoc` - Main OIDC documentation

**What to document:**
- **Configuration is OPTIONAL** - auto-generation works by default
- **Named policies** vs **inline auth** (when to use each)
- **Routing config pattern** (progressive specificity)
- **Migration guide** from old config structure
- **Examples** showing:
  - Named policy (no config needed)
  - Named policy with override
  - Inline auth (short/medium/full overrides)
  - Authentication reuse pattern

**Key messaging:**
- 1:1 DSL mapping (no magic)
- Config only for overrides
- Named policies for reuse, inline for one-off

---

## Migration Strategy

**Breaking change:** Config structure changes from `client()` to `workflow()` with `.task.` handler.

**Old pattern:**
```properties
quarkus.flow.oidc.client."org-acme:orders:1.0.0:payment".name=paymentAuth
```

**New pattern:**
```properties
quarkus.flow.oidc.orders.task.payment.name=paymentAuth
```

**Migration steps:**
1. Identify all `quarkus.flow.oidc.client.*` properties
2. For each:
   - Extract workflow ID and task name
   - Determine specificity level needed (short/medium/full)
   - Rewrite using `workflow.<workflowId>.task.<taskName>.name` pattern
3. Remove old properties
4. Test that clients are still correctly routed

**Example migration:**
```properties
# Old (full key with task)
quarkus.flow.oidc.client."org-acme:orders:1.0.0:payment".name=paymentAuth

# New (short - 99% case, don't need namespace/version)
quarkus.flow.oidc.orders.task.payment.name=paymentAuth

# Old (named policy)
quarkus.flow.oidc.client.keycloak.name=prodKeycloak

# New (same - just remove .client)
quarkus.flow.oidc.keycloak.name=prodKeycloak
```

---

## Summary

**Unified client naming pattern** applied to OIDC:
- Progressive specificity: short (99%) → medium (namespaced) → full (versioned)
- Task-level syntax: `.task.<taskName>`
- Config is OPTIONAL (overrides only)
- 1:1 DSL mapping (named policies share, inline isolates)

**Client creation flow:**

```
Named Policies:
  SPEC (build-time):
    FlowOidcProcessor → extract policies → RunTimeConfigurationDefaultBuildItem
      → quarkus-oidc-client creates clients automatically ✅
  
  SOURCE/Runner (runtime):
    WorkflowRegisteredEvent → OidcWorkflowRegistrationListener
      → OidcClientRegistry.getOrCreateClient() ✅

Inline Task Auth (all types):
  Runtime (lazy):
    OidcClientAuthProvider → OidcClientRegistry.getOrCreateClient()
      → composite name: namespace:name:version.task.taskName ✅
```

**Benefits:**
- ✅ SPEC workflows get native Quarkus OIDC clients (build-time)
- ✅ Zero runtime overhead for SPEC workflows
- ✅ Property expressions supported (`${config.key}`)
- ✅ User config always wins
- ✅ Works for all workflow types (SPEC, SOURCE, Runner)
- ✅ Consistent with HTTP/gRPC routing patterns
- ✅ Simple, predictable, no magic

---

## Future Work: Secret-Based OAuth2 Configuration

**Current implementation scope:** Inline OAuth2 properties only (`OAuth2ConnectAuthenticationProperties`)

**Not implemented:** Secret-based OAuth2 configuration (`SecretBasedAuthenticationPolicy`)

### Background

The Serverless Workflow spec defines `OAuth2AuthenticationPolicyConfiguration` as a union type:

```
OAuth2AuthenticationPolicyConfiguration:
  - OAuth2ConnectAuthenticationProperties  // ✅ Implemented (inline config)
  - SecretBasedAuthenticationPolicy        // ❌ Not implemented (secret reference)
```

**Inline configuration (current):**
```yaml
authentication:
  oauth2:
    authority: https://auth.example.com
    grant: client_credentials
    client:
      id: my-client-id
      secret: my-client-secret
```

**Secret reference (future):**
```yaml
authentication:
  oauth2:
    use: myOAuth2Secret  # Reference to externally-managed secret
```

### Implementation Requirements

To support secret-based OAuth2:

1. **Secret resolution**: Integrate with Quarkus secret management (Vault, Kubernetes Secrets, etc.)
2. **Secret structure**: Define expected secret format containing OAuth2 properties:
   - `authority`
   - `grant`
   - `client.id`
   - `client.secret`
   - `scopes`, `audiences`, etc.
3. **Build-time handling**: Cannot resolve secrets at build time → must be runtime-only
4. **OidcPolicyExtractor update**: Handle `SecretBasedAuthenticationPolicy` variant
5. **Client creation**: Resolve secret content → extract OAuth2 properties → create OIDC client

### Design Considerations

**Challenge**: Secrets are runtime-only, but SPEC workflows benefit from build-time client creation.

**Options:**
- **Option A**: Secret-based auth always creates clients at runtime (even for SPEC workflows)
- **Option B**: Allow build-time reference to secret *name*, resolve content at runtime
- **Option C**: Generate placeholder config at build-time, override with secret content at runtime

**Security**: Secret content must never be logged or included in error messages.

**Recommendation**: Defer until user demand is clear. Current inline approach covers most use cases and allows property expressions (`${config.key}`) which can reference secrets indirectly.

### Related Spec Reference

[Serverless Workflow DSL Reference - OAuth2 Authentication](https://github.com/serverlessworkflow/specification/blob/main/dsl-reference.md)
