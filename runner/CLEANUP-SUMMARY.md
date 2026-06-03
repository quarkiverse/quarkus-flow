# Runner Module Cleanup Summary

## Overview

Cleaned up the runner module configuration to align with actually implemented features, removing configuration for deferred features documented in `HANDOFF.md`.

## What Was Removed

### 1. ❌ Callback Configuration (Deferred - See HANDOFF.md Issue #2)

**Removed from `FlowRunnerConfig.java`:**
- `Callback callback()` method
- Entire `Callback` interface with properties:
  - `timeout`
  - `maxRetries`
  - `requiresHttps`
  - `allowedHosts`
  - `blockedIps`

**Reason:** Callback feature requires:
- Callback persistence layer (DB or Redis)
- Callback delivery service with retry logic
- Event-driven architecture for workflow completion
- SSRF prevention implementation

This is a complete feature requiring separate implementation. See **Issue #2** in `HANDOFF.md`.

---

### 2. ❌ Limits Configuration (Deferred - Future Enhancement)

**Removed from `FlowRunnerConfig.java`:**
- `Limits limits()` method
- Entire `Limits` interface with properties:
  - `maxDefinitionsPerNamespace`
  - `rateLimitExecutionPerMinute`

**Reason:** Rate limiting and quotas require:
- Distributed counter (Redis/Hazelcast) for multi-pod support
- Rate limiting interceptors
- Per-namespace/per-user tracking

Can be added in future PRs as needed.

---

### 3. ❌ Build-Time Definition Management Config (Deferred - See HANDOFF.md Issue #1)

**Deleted entire file:** `FlowRunnerBuildTimeConfig.java`

**What it controlled:**
- `quarkus.flow.runner.endpoints.definition.enabled` - flag to include/exclude POST/PUT/DELETE definition endpoints

**Reason:** Definition CRUD endpoints (POST/PUT/DELETE) are not implemented. Only GET endpoints exist. This flag controlled non-existent features.

When definition CRUD is implemented (Issue #1 in `HANDOFF.md`), this config can be re-added.

---

## What Was Kept

### ✅ Core Configuration

**In `FlowRunnerConfig.java`:**

```java
boolean enabled()           // Master on/off switch for runner
Source source()            // Workflow definition source (filesystem path)
Security security()        // Authentication & authorization (TO BE IMPLEMENTED)
```

### ✅ Security Configuration (Kept for Implementation)

**Security interface** was **kept** because you want to implement security:

- `Type type()` - OIDC / API_KEY / NONE authentication modes
- `Map<String, ApiKey> apiKeys()` - API key definitions
- `Namespace namespace()` - Namespace-level ABAC configuration

**Note:** Security is **configured but not yet enforced**. Endpoints are currently unprotected. Implementation needed:
- Authentication filters (API Key or OIDC)
- `@RolesAllowed` annotations on endpoints
- Namespace authorization interceptor

---

## Current Valid Configuration

After cleanup, users can configure:

```properties
# Enable/disable runner
quarkus.flow.runner.enabled=true

# Workflow source
quarkus.flow.runner.source.path=/deployments/workflows

# Security (not yet enforced)
quarkus.flow.runner.security.type=api-key
quarkus.flow.runner.security.api-keys."admin".secret=${ADMIN_KEY}
quarkus.flow.runner.security.api-keys."admin".roles=flow-admin
quarkus.flow.runner.security.api-keys."invoker".secret=${INVOKER_KEY}
quarkus.flow.runner.security.api-keys."invoker".roles=flow-invoker

# Namespace authorization
quarkus.flow.runner.security.namespace.claim=namespace
quarkus.flow.runner.security.namespace.validate=true
```

---

## Implementation Status

| Feature | Config | Implementation | Status |
|---------|--------|---------------|--------|
| **Workflow Loading** | ✅ `source.path` | ✅ `WorkflowDefinitionRuntimeLoader` | **DONE** |
| **List Definitions** | ✅ `enabled` | ✅ `GET /runner/definitions` | **DONE** |
| **Get Definition** | ✅ `enabled` | ✅ `GET /runner/definitions/{ns}/{name}/{ver}` | **DONE** |
| **Execute Workflow** | ✅ `enabled` | ✅ `POST /runner/exec/{ns}/{name}[/{ver}]` | **DONE** |
| **HATEOAS Links** | N/A | ✅ Links in responses | **DONE** |
| **OpenAPI Docs** | N/A | ✅ Annotations on endpoints | **DONE** |
| **Authentication** | ✅ `security.type/api-keys` | ❌ No filters/interceptors | **TODO** |
| **Authorization (RBAC)** | ✅ `security.api-keys.roles` | ❌ No `@RolesAllowed` | **TODO** |
| **Authorization (ABAC)** | ✅ `security.namespace.*` | ❌ No namespace validation | **TODO** |
| **Definition CRUD** | ❌ Removed | ❌ No POST/PUT/DELETE | **Deferred (Issue #1)** |
| **Callbacks** | ❌ Removed | ❌ No callback service | **Deferred (Issue #2)** |
| **Status/Resume/Cancel** | ❌ N/A | ❌ No endpoints | **Out of scope (Issue #3)** |
| **Rate Limiting** | ❌ Removed | ❌ No enforcement | **Future Enhancement** |

---

## Next Steps

### Immediate (Current PR/Issue)
1. ✅ Cleanup configuration (DONE)
2. **TODO:** Implement security (authentication + authorization)
   - API Key authentication filter
   - OIDC integration (leverage Quarkus OIDC extension)
   - Add `@RolesAllowed` to endpoints
   - Namespace ABAC validation interceptor

### Future Issues (See HANDOFF.md)
1. **Issue #1:** Runtime Definition Management (POST/PUT/DELETE endpoints)
2. **Issue #2:** Async Execution Callbacks
3. **Issue #3:** Multi-Pod Execution Management (platform-layer responsibility)

---

## Files Modified

### Modified
- `runner/runtime/src/main/java/io/quarkiverse/flow/runner/FlowRunnerConfig.java`
  - Removed `Callback` interface
  - Removed `Limits` interface
  - Removed `callback()` method
  - Removed `limits()` method
  - Kept `Security` interface for implementation

### Deleted
- `runner/runtime/src/main/java/io/quarkiverse/flow/runner/FlowRunnerBuildTimeConfig.java`

### Verification
- ✅ Compilation successful
- ✅ No references to deleted classes
- ✅ Existing tests still pass (WorkflowDefinitionRuntimeLoaderTest, DefinitionResourceTest, RunnerExecResourceTest, WorkflowFormatUtilsTest)
