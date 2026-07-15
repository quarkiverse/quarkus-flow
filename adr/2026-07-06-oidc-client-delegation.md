# OIDC Client Delegation Implementation

> **Status:** ✅ Implemented (2026-07-13)

**Goal:** Delegate OIDC client management to `quarkus-oidc-client` by registering clients at workflow startup and runtime, using endpoint-based caching for efficient client reuse.

**Architecture:**
- **Build-Time Registration**: YAML/JSON workflows in classpath without runtime expressions → generates `quarkus.oidc-client.*` config properties via `FlowOidcProcessor`, clients created by `quarkus-oidc-client` extension at startup
- **Startup Registration**: Java DSL workflows without runtime expressions → OIDC clients registered eagerly via SDK `AuthProviderFactory`
- **Runtime Registration**: Any workflow with expressions (`${ $secret.xxx }`) → OIDC clients created lazily on first use
- **Named Auth Policies**: Extract `use("name")` references → clients named by policy name
- **Inline Task Auth**: Generate composite name `namespace:name:version.task.taskName` → clients created on-demand
- **Client Matching**: `EndpointKey` (authority + credentials + grant + scopes) → enables client reuse for identical configs
- **User Override**: Optional routing config to redirect policy names to user-configured Quarkus OIDC clients

**Routing Config Pattern:**
```properties
# OPTIONAL - only needed to override auto-generated client names to user-configured Quarkus OIDC clients

# Named policy override - route to existing quarkus.oidc-client.*
quarkus.flow.oidc.client.keycloak.name=prodKeycloak

# Short form (99% case - no namespace/version)
quarkus.flow.oidc.client."orders.task.payment".name=customPaymentAuth
quarkus.flow.oidc.client.orders.name=customOrdersAuth

# Medium form (namespaced - all versions)  
quarkus.flow.oidc.client."acme\:orders.task.payment".name=acmePaymentAuth

# Full form (version-specific)
quarkus.flow.oidc.client."acme\:orders\:1.0.0.task.payment".name=ordersV1Auth
```

**Tech Stack:**
- SDK Integration: `AuthProviderFactory` interface for auth provider creation
- Static Registration: `OidcClientWorkflowRegistrar` called once at startup per workflow  
- Dynamic Registration: `RuntimeExpressionResolver` + lazy client creation
- Client Storage: `OidcClientRegistry` with name-based and `EndpointKey`-based lookups
- `quarkus-oidc-client` (`OidcClients`, `OidcClientConfig`, `OidcClientConfigBuilder`)
- Expression Detection: `TokenAuthPolicyExtractor` filters static vs dynamic policies

## Implementation Decisions

**Three-Phase Registration:**
1. **Build-time** (YAML/JSON workflows in classpath):
   - `FlowOidcProcessor` discovers workflows via `DiscoveredWorkflowBuildItem.fromSpec()`
   - Extracts static token auth policies (no expressions)
   - Generates `quarkus.oidc-client.*` configuration properties
   - Clients created by `quarkus-oidc-client` extension at startup

2. **Startup** (Java DSL workflows):
   - Serverless Workflow SDK calls `AuthProviderFactory.getAuth()` once per workflow
   - `OidcAuthProviderFactory` registers static OIDC clients eagerly
   - Clients stored in `OidcClientRegistry` for runtime lookup

3. **Runtime** (workflows with expressions):
   - Dynamic clients registered lazily on first HTTP request
   - Expressions resolved via `RuntimeExpressionResolver`
   - Clients created via `OidcClients.newClient()` and cached

**Static vs Dynamic Policy Detection:**
- Policies WITHOUT expressions (`${ ... }`) → build-time (YAML/JSON) or startup (Java DSL)
- Policies WITH expressions → skipped at build/startup, registered at runtime (dynamic)
- `TokenAuthPolicyExtractor` uses `ExpressionUtils.isExpr()` to detect expressions in ALL fields

**Client Matching via EndpointKey:**
- `EndpointKey` = (authority, tokenPath, grant, clientId, clientSecret, scopes, audiences, username, password)
- Two policies with identical `EndpointKey` → reuse same OIDC client
- Credentials stored in plaintext (required by OAuth2 protocol to send to auth servers)
- `EndpointKey` acts as immutable cache key with proper equals/hashCode

**PASSWORD Grant Support:**
- Username/password resolved from expressions at runtime via `RuntimeExpressionResolver`
- Added to dynamic grant parameters map alongside TOKEN_EXCHANGE support
- Passed to `OidcClient.getTokens(Map<String, String>)` for token negotiation

**OAuth2 vs OIDC Token Paths:**
- OIDC (`FuncDSL.oidc()`) → discovery enabled, NO explicit tokenPath
- OAuth2 (`FuncDSL.oauth2()`) → discovery disabled, explicit tokenPath required
- Default tokenPath: `/oauth2/token` per CNCF Serverless Workflow spec

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

## Architecture Components
## Architecture Components

### 1. SDK Integration: OidcAuthProviderFactory

**File:** `oidc/runtime/src/main/java/io/quarkiverse/flow/oidc/impl/OidcAuthProviderFactory.java`

**Lifecycle:** Called by Serverless Workflow SDK **once at application startup** per workflow, not per HTTP request.

**Responsibilities:**
- Implements `AuthProviderFactory` interface from Serverless Workflow SDK
- Called when SDK needs an auth provider for OAuth2/OIDC authentication
- Triggers static OIDC client registration via `OidcClientWorkflowRegistrar`
- Returns `OidcClientAuthProvider` instance to SDK (SDK caches it)
- Delegates non-OAuth2/OIDC auth to SDK's `DefaultAuthProviderFactory`

**Key Flow:**
```
SDK calls getAuth() at startup
  → Check if OAuth2/OIDC policy
  → Call OidcClientWorkflowRegistrar.registerStaticOidcClientsFor(workflow)
  → Return new OidcClientAuthProvider(authData, ...)
  → SDK caches the provider for all future executions
```

---

### 2. Policy Extraction: TokenAuthPolicyExtractor

**File:** `oidc/runtime/src/main/java/io/quarkiverse/flow/oidc/TokenAuthPolicyExtractor.java`

**Responsibilities:**
- Scans workflows for all OAuth2/OIDC authentication policies
- Extracts both named policies (`use("name")`) and inline task auth
- **Filters out policies with runtime expressions** (`${ $secret.xxx }`)
- Returns only **static policies** that can be registered at startup

**Expression Detection:**
- Uses SDK's `ExpressionUtils.isExpr()` to detect runtime expressions
- Checks ALL fields: authority, clientId, clientSecret, username, password, scopes
- Policies with ANY expression → skipped (registered lazily at runtime)
- Policies with ALL literals → included (registered eagerly at startup)

**Output:**
```java
List<TokenAuthPolicy> extractStaticTokenAuthPolicies(Workflow workflow)
  → Returns: List of policies safe to register at startup
  → Skips: Policies requiring runtime expression resolution
```

---

### 3. Static Registration: OidcClientWorkflowRegistrar

**File:** `oidc/runtime/src/main/java/io/quarkiverse/flow/oidc/registry/OidcClientWorkflowRegistrar.java`

**Lifecycle:** Called once at startup per workflow by `OidcAuthProviderFactory`.

**Responsibilities:**
- Registers static OIDC clients (policies without expressions)
- Checks if client already exists (user-configured or previously registered)
- Creates new OIDC clients via `OidcClients.newClient(OidcClientConfig)`
- Registers clients by NAME and by ENDPOINT KEY in `OidcClientRegistry`
- Handles dynamic client registration for runtime-expression policies

**Registration Flow:**
```
registerStaticOidcClientsFor(workflow):
  1. Extract static policies via TokenAuthPolicyExtractor
  2. For each policy:
     a. Resolve client name (check routing config override)
     b. Check if user already configured quarkus.oidc-client.<name>
     c. Check if already in our registry
     d. If not exists: create via OidcClients.newClient()
     e. Register: registry.register(name, client, endpointKey)
        → Stores in BOTH maps: by name AND by endpoint
     f. Enables lookup by name OR by endpoint configuration
```

**Dynamic Registration:**
```
registerDynamicOidcClientFor(EndpointKey endpointKey, Duration creationTimeout, Duration connectionTimeout):
  1. Called at runtime when expression-based policy is first used
  2. Create OidcClientConfig from EndpointKey (all fields resolved)
  3. Call OidcClients.newClient(config).await().atMost(creationTimeout)
  4. Register: registry.register(client, endpointKey)
     → Stores ONLY by endpoint (no name) - direct mapping for O(1) lookup
  5. Returns client for immediate use
```

---

### 4. Client Storage: OidcClientRegistry

**File:** `oidc/runtime/src/main/java/io/quarkiverse/flow/oidc/registry/OidcClientRegistry.java`

**Responsibilities:**
- Stores OIDC clients created by Quarkus Flow
- Dual lookup: by **name** (string) AND by **EndpointKey** (config hash)
- Thread-safe via `ConcurrentHashMap`
- Lifecycle management (`@PreDestroy` cleanup)

**Storage:**
```java
// Actual field names in code:
Map<String, OidcClient> runtimeOidcClients        // By-name index
Map<EndpointKey, OidcClient> endpointToOidcClients  // By-endpoint index (direct)
```

**Important:** As of 2026-07-14, the endpoint map stores `OidcClient` instances directly (not names). This fixes a critical registry collision bug where different `EndpointKey` instances could generate the same client name via `defaultOidcId()`, causing the second registration to overwrite the first.

**Lookup Methods:**
- `get(String name)` → user routing overrides, named policies
- `getByEndpoint(EndpointKey key)` → **direct O(1) lookup**, client reuse for identical configs
- `register(String name, OidcClient client, EndpointKey key)` → stores both indexes (package-visible)
- `register(OidcClient client, EndpointKey key)` → stores only by endpoint (package-visible)

**Why Dual Lookup:**
- Name lookup: Fast path for named policies and routing overrides
- Endpoint lookup: Enables client reuse when two policies have identical OAuth2 config
- Same `OidcClient` instance may be indexed multiple ways (memory efficient - only references, not duplicates)

---

### 5. Cache Key: EndpointKey

**File:** `oidc/runtime/src/main/java/io/quarkiverse/flow/oidc/registry/EndpointKey.java`

**Purpose:** Immutable value object representing complete OAuth2/OIDC configuration for client matching.

**Fields:**
```java
record EndpointKey(
    String authority,
    String tokenPath,
    String revocationPath,
    boolean openIdConnect,
    String clientId,
    String clientSecret,
    ClientAuthentication clientAuthMethod,
    OAuth2AuthenticationDataGrant grant,
    List<String> scopes,
    List<String> audiences,
    String username,  // PASSWORD grant
    String password   // PASSWORD grant
)
```

**Security:**
- Stores plaintext credentials (required to send to OAuth2 server)
- `@JsonIgnoreType` prevents accidental serialization
- `toString()` masks sensitive fields (shows "***")
- Exists only in memory, never persisted

**Equality:**
- Two policies with identical EndpointKey → same OIDC client reused
- Enables client sharing without duplicate token negotiations
- Normalizes null collections to empty for consistent equality

---

### 6. Expression Resolution: RuntimeExpressionResolver

**File:** `oidc/runtime/src/main/java/io/quarkiverse/flow/oidc/impl/RuntimeExpressionResolver.java`

**Lifecycle:** Runtime only - resolves expressions when workflow executes.

**Responsibilities:**
- Resolves `${ $secret.xxx }` expressions using workflow context
- Creates `EndpointKey` with resolved values (plaintext credentials)
- Supports expressions in ALL OAuth2 fields: authority, clientId, clientSecret, username, password

**Resolution:**
```java
EndpointKey resolveAll(WorkflowContext workflow, TaskContext task, WorkflowModel model, OAuth2AuthenticationData authData)
  → Resolves all expression fields
  → Returns EndpointKey with plaintext values
  → Used for dynamic client lookup/creation
```

**Expression Resolution:**
- Uses `WorkflowApplication.buildFilter()` to evaluate expressions
- Accesses secrets via workflow context (Quarkus Vault, K8s Secrets, etc.)
- Resolves at HTTP request time (when workflow executes)

---

### 7. Runtime Auth Provider: OidcClientAuthProvider

**File:** `oidc/runtime/src/main/java/io/quarkiverse/flow/oidc/impl/OidcClientAuthProvider.java`

**Lifecycle:** Created once at startup by `OidcAuthProviderFactory`, used for all HTTP requests.

**Responsibilities:**
- Implements `AuthProvider` interface (returns "Bearer" + token)
- Resolves client name via routing config (optional override)
- Gets client from registry (or creates dynamically if needed)
- Resolves dynamic grant parameters (PASSWORD grant, TOKEN_EXCHANGE)
- Negotiates access token via `OidcClient.getTokens()`

**Client Resolution Flow:**
```
content(WorkflowContext, TaskContext, WorkflowModel, URI):
  1. Check for routing config override via OidcConfigResolver
     → quarkus.flow.oidc.<policyName>.name=<override>
  2. Try get client by name: registry.get(overrideOrPolicyName)
  3. If not found, resolve endpoint key from expressions
  4. Try get client by endpoint: registry.getByEndpoint(endpointKey)
  5. If still not found, create dynamic client: registerDynamicOidcClientFor(endpointKey)
  6. Resolve dynamic grant params (username/password for PASSWORD grant)
  7. Get token: client.getTokens(dynamicParams).await()
  8. Return access token (SDK adds "Bearer " prefix)
```

**Dynamic Grant Parameters:**
- **PASSWORD grant**: Adds `username` and `password` to token request
- **TOKEN_EXCHANGE grant**: Adds `subject_token`, `actor_token`, and token types
- All other grants: Empty map

---

### 8. Config & Routing: OidcConfigResolver

**File:** `oidc/runtime/src/main/java/io/quarkiverse/flow/oidc/registry/OidcConfigResolver.java`

**Responsibilities:**
- Resolves routing config overrides (OPTIONAL - only for user overrides)
- Progressive specificity: `name` → `namespace:name` → `namespace:name:version`
- Task-level overrides take precedence over workflow-level

**Resolution Algorithm:**
```
resolve(WorkflowDefinitionId id, String taskName, String authPolicyName):
  // Named policy override
  if authPolicyName != null:
    return config.workflow().get(authPolicyName).name()
  
  // Progressive specificity for workflow + task
  for key in [name, namespace:name, namespace:name:version]:
    workflowConfig = config.workflow().get(key)
    if workflowConfig exists:
      if taskName != null and workflowConfig.task().get(taskName) exists:
        return workflowConfig.task().get(taskName).name()
      else:
        return workflowConfig.name()
  
  return empty  // No override, use auto-generated name
```

---

### 9. Client Config Factory: OidcClientConfigFactory

**File:** `oidc/runtime/src/main/java/io/quarkiverse/flow/oidc/registry/OidcClientConfigFactory.java`

**Responsibilities:**
- Converts `TokenAuthPolicy` → `OidcClientConfig` (Quarkus format)
- Converts `EndpointKey` → `OidcClientConfig` (for dynamic clients)
- Maps grant types: `CLIENT_CREDENTIALS` → `Grant.Type.CLIENT`, etc.
- Maps client auth methods: `CLIENT_SECRET_POST`, `CLIENT_SECRET_BASIC`, etc.
- Validates authority URLs (must be http/https with valid host)

**Key Mappings:**
- **Grant types**: Normalizes to uppercase (`client_credentials` → `CLIENT_CREDENTIALS`)
- **Token paths**: Only set for OAuth2, NOT for OIDC (discovery finds it)
- **Client secret method**: Defaults to `POST` per CNCF spec
- **Default endpoints**: `/oauth2/token`, `/oauth2/revoke` per CNCF spec

**OAuth2 vs OIDC:**
```java
if (isOidc) {
  builder.discoveryEnabled(true)
  // Do NOT set tokenPath - OIDC uses .well-known/openid-configuration
} else {
  builder.discoveryEnabled(false)
  builder.tokenPath(tokenPath)  // OAuth2 needs explicit token endpoint
}
```

---

## Client Registration Flowchart

```
Build Time (YAML/JSON workflows)
  ↓
FlowOidcProcessor scans classpath
  ↓
For each DiscoveredWorkflowBuildItem.fromSpec():
  ↓
  TokenAuthPolicyExtractor.extractStaticTokenAuthPolicies(workflow)
  → Filters out policies with expressions (${ ... })
  → Returns only static policies
  ↓
  For each static policy:
    1. Generate quarkus.oidc-client.<policyName>.auth-server-url
    2. Generate quarkus.oidc-client.<policyName>.client-id
    3. Generate quarkus.oidc-client.<policyName>.credentials.secret
    4. Generate quarkus.oidc-client.<policyName>.grant.type
    5. Generate quarkus.oidc-client.<policyName>.discovery-enabled
    6. (etc. - all OAuth2/OIDC config properties)
  ↓
quarkus-oidc-client extension creates clients at startup

---

Application Startup (Java DSL workflows)
  ↓
SDK calls AuthProviderFactory.getAuth() per workflow
  ↓
OidcAuthProviderFactory.getAuth()
  ↓
  ├─ Check if OAuth2/OIDC policy? 
  │  No → Delegate to DefaultAuthProviderFactory
  │  Yes ↓
  ├─ Call OidcClientWorkflowRegistrar.registerStaticOidcClientsFor(workflow)
  │    ↓
  │    TokenAuthPolicyExtractor.extractStaticTokenAuthPolicies(workflow)
  │    → Filters out policies with expressions (${ ... })
  │    → Returns only static policies
  │    ↓
  │    For each static policy:
  │      1. Resolve client name (check routing override)
  │      2. Check if already exists (user config or registry)
  │      3. If not: Create via OidcClients.newClient()
  │      4. Register: registry.register(name, client, endpointKey)
  │
  └─ Return new OidcClientAuthProvider(...) → SDK caches it

---
  
HTTP Request Time (workflow executes)
  ↓
SDK calls OidcClientAuthProvider.content()
  ↓
  ├─ Check routing override (OidcConfigResolver)
  ├─ Try get client by name: registry.get(name)
  ├─ If not found:
  │    ├─ Resolve expressions → RuntimeExpressionResolver.resolveAll()
  │    ├─ Create EndpointKey with resolved values
  │    ├─ Try get by endpoint: registry.getByEndpoint(endpointKey)
  │    └─ If still not found: registerDynamicOidcClientFor(endpointKey)
  │
  ├─ Resolve dynamic grant params:
  │    ├─ PASSWORD grant → add username/password
  │    └─ TOKEN_EXCHANGE → add subject/actor tokens
  │
  ├─ Get token: client.getTokens(dynamicParams).await()
  └─ Return access token
       ↓
SDK adds "Authorization: Bearer <token>" to HTTP request
```

---

## Test Coverage

**Runtime Unit Tests (62 tests):**
- `TokenAuthPolicyExtractorTest` (14 tests) - Expression filtering, policy extraction
- `OidcAuthProviderFactoryTest` (9 tests) - Factory behavior, delegation
- `OidcWorkflowRegistrarTest` (8 tests) - Client registration, name resolution
- `OidcAuthProviderFactoryIntegrationTest` (3 tests) - End-to-end flows
- `EndpointKeyTest` (18 tests) - Equality, caching, security (toString masking)
- `TokenAuthPolicyTest` (5 tests) - Policy data structures
- `OidcClientNameResolutionTest` (5 tests) - Name generation

**Deployment Tests (3 tests):**
- Build-time configuration generation
- Bean registration

**Integration Tests (5 tests):**
- CLIENT_CREDENTIALS grant with static config
- PASSWORD grant with expressions
- Scope-based authorization
- Endpoint path caching
- Named client usage
- Config override routing

**Example Tests (6 tests):**
- Client credentials flow
- Password grant flow
- Multiple OAuth2 clients
- OpenAPI with OAuth2
- OIDC client usage
- Token exchange grant

**Total:** 76 tests passing ✅

---
## Configuration Reference

**Current config structure:** All routing overrides use `quarkus.flow.oidc.client.*` prefix.

**Task-level routing (composite key format):**
```properties
# Full specificity: namespace:name:version.task.taskName
quarkus.flow.oidc.client."org-acme\:orders\:1.0.0.task.payment".name=paymentAuth

# Medium specificity: namespace:name.task.taskName (applies to all versions)
quarkus.flow.oidc.client."org-acme\:orders.task.payment".name=paymentAuth

# Short specificity: name.task.taskName (99% case)
quarkus.flow.oidc.client."orders.task.payment".name=paymentAuth
```

**Workflow-level routing:**
```properties
# Full: namespace:name:version
quarkus.flow.oidc.client."org-acme\:orders\:1.0.0".name=ordersAuth

# Medium: namespace:name
quarkus.flow.oidc.client."org-acme\:orders".name=ordersAuth

# Short: name
quarkus.flow.oidc.client.orders.name=ordersAuth
```

**Named policy routing:**
```properties
# Named authentication policy (e.g., use("keycloak"))
quarkus.flow.oidc.client.keycloak.name=prodKeycloak
```

**Note:** Colons in composite keys must be escaped as `\:` in properties files.

---

## Summary

**Implementation Approach:**
- **SDK Integration**: Hook into `AuthProviderFactory` called once at startup
- **Static vs Dynamic**: Expression detection determines registration timing
- **Endpoint-Based Caching**: `EndpointKey` enables client reuse for identical configs
- **Lazy Registration**: Dynamic clients created on first use (expressions resolved at runtime)
- **Optional Routing Config**: Override auto-generated names to user-configured clients

**Client Creation Flow:**

```
Build Time (YAML/JSON workflows in classpath):
  FlowOidcProcessor.createOidcClientFromWorkflowDef()
    → For each DiscoveredWorkflowBuildItem.fromSpec()
    → TokenAuthPolicyExtractor.extractStaticTokenAuthPolicies()
    → For each policy without expressions:
       → Generate quarkus.oidc-client.<policyName>.* properties
       → Includes: auth-server-url, client-id, credentials.secret, grant.type, etc.
    → quarkus-oidc-client extension creates clients at startup

Application Startup (Java DSL workflows):
  SDK calls AuthProviderFactory.getAuth()
    → OidcAuthProviderFactory.getAuth()
    → OidcClientWorkflowRegistrar.registerStaticOidcClientsFor()
    → TokenAuthPolicyExtractor filters policies (skip if has expressions)
    → For each static policy: OidcClients.newClient() + registry.register()
    → Return OidcClientAuthProvider (SDK caches it)

HTTP Request Time (workflow executes):
  SDK calls OidcClientAuthProvider.content()
    → Check routing override (optional)
    → Try registry.get(name) - startup-registered or build-time clients
    → If not found: RuntimeExpressionResolver.resolveAll()
    → Create EndpointKey with resolved values
    → Try registry.getByEndpoint(endpointKey) - check for match
    → If still not found: registerDynamicOidcClientFor()
    → Resolve dynamic grant params (PASSWORD, TOKEN_EXCHANGE)
    → client.getTokens(dynamicParams).await()
    → Return access token
```

**Benefits:**
- ✅ Static clients registered at startup (zero runtime resolution overhead)
- ✅ Dynamic clients cached by endpoint config (expression-based policies)
- ✅ Runtime expression support via `${ $secret.xxx }`
- ✅ PASSWORD grant with username/password in dynamic params
- ✅ TOKEN_EXCHANGE grant with subject/actor tokens
- ✅ Client reuse via `EndpointKey` matching (identical configs share clients)
- ✅ User-configured Quarkus OIDC clients take precedence
- ✅ Comprehensive test coverage (76 tests across unit/integration/examples)
- ✅ OAuth2 vs OIDC token path handling (discovery vs explicit)

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
