# Security Implementation Plan

## Current State

**Endpoints (Currently Unprotected):**
- `GET /runner/definitions` - List workflow definitions
- `GET /runner/definitions/{namespace}/{name}/{version}` - Get specific workflow definition
- `POST /runner/exec/{namespace}/{name}` - Execute workflow (latest version)
- `POST /runner/exec/{namespace}/{name}/{version}` - Execute workflow (specific version)

**Configuration (Exists but Not Enforced):**
```properties
quarkus.flow.runner.security.type=api-key|oidc|none
quarkus.flow.runner.security.api-keys."name".secret=xxx
quarkus.flow.runner.security.api-keys."name".roles=flow-admin,flow-invoker
quarkus.flow.runner.security.namespace.claim=namespace
quarkus.flow.runner.security.namespace.validate=true
```

---

## Security Requirements

### 1. Authentication (Who are you?)

Three modes to support:

#### **Mode 1: API Key Authentication**
- Read `Authorization: Bearer <api-key>` header
- Validate against configured keys in `security.api-keys.*`
- Map key to roles (`flow-admin`, `flow-invoker`)
- Inject `SecurityIdentity` so `@RolesAllowed` works

#### **Mode 2: OIDC Authentication**
- Leverage Quarkus OIDC extension (already handles JWT validation)
- Extract roles from JWT claims
- No custom code needed - Quarkus does it

#### **Mode 3: None (Dev Only)**
- No authentication
- Log warning on startup
- All requests allowed

---

### 2. Authorization (What can you do?)

Two layers:

#### **RBAC - Role-Based Access Control**

**Predefined Roles:**
- `flow-admin` - Full access (read definitions + execute workflows)
- `flow-invoker` - Execute only (execute workflows + read definitions)

**Endpoint Mapping:**
```java
// Definition endpoints
@RolesAllowed({"flow-admin", "flow-invoker"})  // Both can read
GET /runner/definitions
GET /runner/definitions/{namespace}/{name}/{version}

// Execution endpoints
@RolesAllowed({"flow-admin", "flow-invoker"})  // Both can execute
POST /runner/exec/{namespace}/{name}
POST /runner/exec/{namespace}/{name}/{version}
```

**Note:** Since we don't have definition CRUD (POST/PUT/DELETE), both roles have identical permissions currently.

---

#### **ABAC - Attribute-Based Access Control (Namespace Validation)**

**Purpose:** Ensure users can only access workflows in namespaces they're authorized for.

**How it works:**

1. **Extract namespace** from request path:
   - `/runner/definitions?namespace=my-ns` → filter results to `my-ns`
   - `/runner/exec/my-ns/workflow` → validate access to `my-ns`

2. **Get authorized namespaces** from user:
   - **API Key mode:** Extract from API key config (if we add namespace field)
   - **OIDC mode:** Extract from JWT claim (configurable via `security.namespace.claim`)

3. **Validate:** If user's namespaces don't include requested namespace → 403 Forbidden

**Special case:** `GET /runner/definitions` without namespace filter → return only workflows in user's authorized namespaces

---

## Implementation Components

### Component 1: API Key Authentication Filter

**File:** `runner/runtime/src/main/java/io/quarkiverse/flow/runner/security/ApiKeyAuthenticationMechanism.java`

**Purpose:** Intercept requests, validate API key, create `SecurityIdentity`

**Logic:**
```java
@Alternative
@Priority(1)
@ApplicationScoped
public class ApiKeyAuthenticationMechanism implements HttpAuthenticationMechanism {
    
    @Inject
    FlowRunnerConfig config;
    
    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext context, IdentityProviderManager identityProviderManager) {
        // Only run if security.type=api-key
        if (config.security().type() != FlowRunnerConfig.Security.Type.API_KEY) {
            return Uni.createFrom().nullItem();
        }
        
        // Extract Authorization header
        String authHeader = context.request().getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Uni.createFrom().failure(new AuthenticationFailedException("Missing or invalid Authorization header"));
        }
        
        String apiKey = authHeader.substring(7);
        
        // Find matching key in config
        for (Map.Entry<String, ApiKey> entry : config.security().apiKeys().entrySet()) {
            if (entry.getValue().secret().equals(apiKey)) {
                // Found! Build SecurityIdentity with roles
                return identityProviderManager.authenticate(
                    new ApiKeyAuthenticationRequest(entry.getKey(), entry.getValue().roles())
                );
            }
        }
        
        return Uni.createFrom().failure(new AuthenticationFailedException("Invalid API key"));
    }
}
```

**Additional classes needed:**
- `ApiKeyAuthenticationRequest implements AuthenticationRequest`
- `ApiKeyIdentityProvider implements IdentityProvider<ApiKeyAuthenticationRequest>`

---

### Component 2: Security Mode Selector

**File:** `runner/runtime/src/main/java/io/quarkiverse/flow/runner/security/SecurityModeProducer.java`

**Purpose:** Enable/disable API Key mechanism based on config

**Logic:**
```java
@ApplicationScoped
public class SecurityModeProducer {
    
    @Inject
    FlowRunnerConfig config;
    
    @PostConstruct
    void logSecurityMode() {
        switch (config.security().type()) {
            case NONE -> LOGGER.warn("⚠️  Runner security is DISABLED. All endpoints are unprotected. Use only in development!");
            case API_KEY -> LOGGER.info("Runner security: API Key authentication enabled");
            case OIDC -> LOGGER.info("Runner security: OIDC authentication enabled (configure quarkus.oidc.*)");
        }
    }
}
```

---

### Component 3: Role Constants

**File:** `runner/runtime/src/main/java/io/quarkiverse/flow/runner/security/RunnerRoles.java`

**Purpose:** Centralize role names

```java
public final class RunnerRoles {
    public static final String ADMIN = "flow-admin";
    public static final String INVOKER = "flow-invoker";
    
    private RunnerRoles() {}
}
```

---

### Component 4: Add @RolesAllowed to Endpoints

**Update:** `DefinitionResource.java`

```java
@Path("/runner/definitions")
@Tag(name = "Workflow Definitions", description = "Browse and retrieve workflow definitions")
public class DefinitionResource {
    
    @GET
    @RolesAllowed({RunnerRoles.ADMIN, RunnerRoles.INVOKER})
    @Produces(MediaType.APPLICATION_JSON)
    public Response listDefinitions(...) { ... }
    
    @GET
    @Path("/{namespace}/{name}/{version}")
    @RolesAllowed({RunnerRoles.ADMIN, RunnerRoles.INVOKER})
    @Produces({MediaType.APPLICATION_JSON, "application/yaml"})
    public Response getDefinition(...) { ... }
}
```

**Update:** `RunnerExecResource.java`

```java
@Path("/runner/exec")
@Tag(name = "Workflow Execution", description = "Execute and manage workflow instances")
public class RunnerExecResource {
    
    @POST
    @Path("/{namespace}/{name}")
    @RolesAllowed({RunnerRoles.ADMIN, RunnerRoles.INVOKER})
    public Uni<Response> executeWorkflow(...) { ... }
    
    @POST
    @Path("/{namespace}/{name}/{version}")
    @RolesAllowed({RunnerRoles.ADMIN, RunnerRoles.INVOKER})
    public Uni<Response> executeWorkflow(...) { ... }
}
```

---

### Component 5: Namespace Authorization Filter (Optional - ABAC)

**File:** `runner/runtime/src/main/java/io/quarkiverse/flow/runner/security/NamespaceAuthorizationFilter.java`

**Purpose:** Validate user can access requested namespace

**Logic:**
```java
@Provider
@Priority(Priorities.AUTHORIZATION)
public class NamespaceAuthorizationFilter implements ContainerRequestFilter {
    
    @Inject
    FlowRunnerConfig config;
    
    @Inject
    SecurityIdentity securityIdentity;
    
    @Override
    public void filter(ContainerRequestContext ctx) {
        // Only enforce if validation enabled
        if (!config.security().namespace().validate()) {
            return;
        }
        
        // Extract namespace from path
        String namespace = extractNamespace(ctx);
        if (namespace == null) {
            return; // No namespace in path, skip validation
        }
        
        // Get user's authorized namespaces
        Set<String> authorizedNamespaces = getAuthorizedNamespaces();
        
        // Validate
        if (!authorizedNamespaces.contains(namespace)) {
            throw new ForbiddenException("Access denied to namespace: " + namespace);
        }
    }
    
    private Set<String> getAuthorizedNamespaces() {
        // Option 1: From JWT claim (OIDC mode)
        if (config.security().type() == Type.OIDC) {
            String claim = config.security().namespace().claim();
            JsonArray namespaces = securityIdentity.getAttribute(claim);
            // ... extract and return
        }
        
        // Option 2: From API key config (would need to enhance ApiKey interface)
        // Option 3: Default to "all namespaces" if not configured
        
        return Set.of("*"); // Placeholder
    }
}
```

---

## Dependencies Needed

### Already in pom.xml:
- ✅ `quarkus-rest-jackson` (provides JAX-RS)
- ✅ `quarkus-smallrye-openapi`

### Need to add to `runner/runtime/pom.xml`:

```xml
<!-- Security - provides @RolesAllowed, SecurityIdentity -->
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-security</artifactId>
</dependency>

<!-- OIDC (optional - only if user adds it) -->
<!-- Not added by default - user adds if they want OIDC mode:
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-oidc</artifactId>
</dependency>
-->
```

---

## Configuration Examples

### Example 1: API Key Mode (Development/Testing)

```properties
quarkus.flow.runner.enabled=true
quarkus.flow.runner.source.path=/opt/workflows

# API Key authentication
quarkus.flow.runner.security.type=api-key
quarkus.flow.runner.security.api-keys."admin-key".secret=super-secret-admin-key-123
quarkus.flow.runner.security.api-keys."admin-key".roles=flow-admin

quarkus.flow.runner.security.api-keys."webhook-key".secret=webhook-secret-456
quarkus.flow.runner.security.api-keys."webhook-key".roles=flow-invoker

# Namespace validation disabled for simplicity
quarkus.flow.runner.security.namespace.validate=false
```

**Usage:**
```bash
# As admin
curl -H "Authorization: Bearer super-secret-admin-key-123" \
  http://localhost:8080/runner/definitions

# As invoker
curl -H "Authorization: Bearer webhook-secret-456" \
  -X POST http://localhost:8080/runner/exec/my-ns/my-workflow
```

---

### Example 2: OIDC Mode (Production)

```properties
quarkus.flow.runner.enabled=true
quarkus.flow.runner.source.path=/deployments/workflows

# OIDC authentication
quarkus.flow.runner.security.type=oidc

# Standard Quarkus OIDC config
quarkus.oidc.auth-server-url=https://keycloak.example.com/realms/myrealm
quarkus.oidc.client-id=quarkus-flow-runner
quarkus.oidc.credentials.secret=${OIDC_CLIENT_SECRET}
quarkus.oidc.roles.source=accesstoken

# Namespace ABAC
quarkus.flow.runner.security.namespace.claim=namespaces
quarkus.flow.runner.security.namespace.validate=true
```

**JWT Token Example:**
```json
{
  "sub": "user@example.com",
  "roles": ["flow-invoker"],
  "namespaces": ["team-a", "team-b"]
}
```

---

### Example 3: None Mode (Local Dev)

```properties
quarkus.flow.runner.enabled=true
quarkus.flow.runner.source.path=./workflows

# No security
quarkus.flow.runner.security.type=none
```

**Startup Warning:**
```
⚠️  Runner security is DISABLED. All endpoints are unprotected. Use only in development!
```

---

## Implementation Order

### Phase 1: Basic RBAC (Minimal Security)
1. Add `quarkus-security` dependency
2. Create `RunnerRoles` constants
3. Add `@RolesAllowed` to all endpoints
4. Create `SecurityModeProducer` (log warning for NONE mode)
5. **For OIDC mode:** Quarkus OIDC handles everything automatically
6. **For API Key mode:** Implement `ApiKeyAuthenticationMechanism` + supporting classes
7. **For NONE mode:** No auth, just warnings

**Result:** Authentication + role-based authorization working

### Phase 2: ABAC (Namespace Validation) - Optional
1. Implement `NamespaceAuthorizationFilter`
2. Extract namespace from request path
3. Validate against user's authorized namespaces
4. Handle namespace filtering in `GET /runner/definitions`

**Result:** Users can only access workflows in their namespaces

---

## Testing Strategy

### Unit Tests

**Test:** `ApiKeyAuthenticationMechanismTest`
- Valid API key → SecurityIdentity created with correct roles
- Invalid API key → AuthenticationFailedException
- Missing Authorization header → AuthenticationFailedException
- Wrong Bearer format → AuthenticationFailedException

**Test:** `SecurityModeProducerTest`
- NONE mode → warning logged
- API_KEY mode → info logged
- OIDC mode → info logged

### Integration Tests

**Test:** `DefinitionResourceSecurityTest`
- No auth header → 401 Unauthorized
- Valid API key (flow-admin) → 200 OK
- Valid API key (flow-invoker) → 200 OK
- Invalid API key → 401 Unauthorized

**Test:** `RunnerExecResourceSecurityTest`
- No auth header → 401 Unauthorized
- Valid API key → 202 Accepted (async) or 200 OK (sync)
- Invalid API key → 401 Unauthorized

**Test:** `NamespaceAuthorizationTest` (if ABAC implemented)
- User authorized for namespace → allowed
- User not authorized → 403 Forbidden
- Validation disabled → allowed

---

## Open Questions

### 1. Namespace ABAC for API Keys

**Question:** How do we associate namespaces with API keys?

**Options:**

**A. Extend ApiKey config to include namespaces:**
```properties
quarkus.flow.runner.security.api-keys."team-a-key".secret=xxx
quarkus.flow.runner.security.api-keys."team-a-key".roles=flow-invoker
quarkus.flow.runner.security.api-keys."team-a-key".namespaces=team-a
```

**B. Store in API key name as convention:**
```properties
quarkus.flow.runner.security.api-keys."team-a:invoker".secret=xxx
quarkus.flow.runner.security.api-keys."team-a:invoker".roles=flow-invoker
# Parse "team-a" from key name
```

**C. Skip namespace validation for API Key mode:**
- Only enforce ABAC in OIDC mode (where JWT has namespace claim)
- API keys get access to all namespaces

**Recommendation:** Start with **Option C** (simplest), add **Option A** later if needed.

---

### 2. Default Security Mode

**Question:** What should `security.type` default to?

**Current:** `none`

**Options:**
- **A. Keep `none`** - Easy for quick start, but insecure by default
- **B. Change to `api-key`** - Secure by default, but requires config
- **C. Make it required** - Force user to explicitly choose

**Recommendation:** Keep `none` for now (matches current behavior), but **log loud warning**.

---

### 3. OIDC Dependency

**Question:** Should we add `quarkus-oidc` dependency to runner module?

**Options:**
- **A. Add as optional dependency** - User must explicitly include it
- **B. Add as provided dependency** - Available but not forced
- **C. Document but don't include** - User adds if they want OIDC

**Recommendation:** **Option C** - Don't include it. When `security.type=oidc` but OIDC extension is missing, log clear error:
```
ERROR: security.type=oidc but quarkus-oidc extension not found. 
Add to pom.xml: <dependency><groupId>io.quarkus</groupId><artifactId>quarkus-oidc</artifactId></dependency>
```

---

## Success Criteria

- [ ] API Key authentication works (validate bearer tokens against config)
- [ ] OIDC authentication works (when user adds quarkus-oidc dependency)
- [ ] NONE mode works (with startup warning)
- [ ] All endpoints protected with `@RolesAllowed`
- [ ] Both `flow-admin` and `flow-invoker` roles can access all current endpoints
- [ ] 401 Unauthorized for missing/invalid credentials
- [ ] Integration tests cover all auth modes
- [ ] OpenAPI docs updated with security schemes
- [ ] User documentation with configuration examples

---

## Out of Scope (For Now)

- ❌ Namespace ABAC for API Keys (can add later with Option A from Question 1)
- ❌ Per-user rate limiting
- ❌ Audit logging (who did what when)
- ❌ Custom roles beyond flow-admin/flow-invoker
- ❌ Fine-grained permissions (e.g., execute but not read)

These can be added incrementally in future PRs.
