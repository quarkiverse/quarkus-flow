# Workflow Runner REST API - Design Specification

**Date:** 2026-05-05  
**Status:** Approved  
**Issue:** [#52](https://github.com/quarkiverse/quarkus-flow/issues/52)

## Overview

This specification defines the design for the Quarkus Flow Runner REST API, a new optional extension (`quarkus-flow-runner`) that enables runtime workflow definition loading and REST-based workflow execution. The feature targets development, testing, and cloud deployment scenarios where workflows need to be loaded dynamically without application rebuilds.

## Goals

1. **Runtime Loading:** Load workflow definitions from filesystem paths or classpath at startup
2. **REST Execution API:** Provide HTTP endpoints for executing workflows synchronously or asynchronously
3. **Optional Definition Management:** Allow runtime CRUD operations on workflow definitions (disableable for immutable cloud scenarios)
4. **Enterprise Security:** Support OIDC and API Key authentication with role-based and namespace-level authorization
5. **Cloud-Native:** Design for Kubernetes/OpenShift deployment with operator integration patterns
6. **Callback Support:** Enable async execution results delivery via HTTP callbacks with retry logic

## Non-Goals

- Custom persistence layer (reuse existing Quarkus Flow persistence infrastructure)
- WebSocket streaming (deferred to future release)
- Custom workflow DSL (use existing CNCF Serverless Workflow spec)
- Kubernetes-specific features in the extension (operator handles those)

## Scope Limitations

The following limitations apply to the initial release:

1. **Callback Persistence:** Callback configurations are stored in-memory only and will be lost on application restart. Running workflows will complete, but their callback delivery cannot be guaranteed across restarts. Future releases may add durable callback storage.

2. **Sync Execution Timeouts:** Synchronous executions (`?wait=true`) use Quarkus HTTP handler timeouts. There is no per-execution custom timeout configuration in this release. Users needing fine-grained timeout control should use async execution with client-side polling or rely on the built-in callbacks.

3. **Execution Status History:** The status endpoint only tracks active (non-completed) executions in memory. Once an execution completes, is aborted, or fails, it is removed from memory and status queries will return 404. Users needing execution history should integrate with observability platforms for historical queries.

## Architecture Overview

### High-Level Structure

The `quarkus-flow-runner` extension follows the standard Quarkus extension pattern with runtime/deployment separation:

```
runner/
├── runtime/          # REST endpoints, security, workflow loading
├── deployment/       # Build-time processors, config validation
└── integration-tests/
```

### Core Components

1. **Workflow Loader** - Reads definitions from paths/classpath, registers with `WorkflowRegistry`
2. **Definition Management Resource** - REST CRUD endpoints for workflow definitions (conditionally included)
3. **Execution Resource** - REST endpoints for workflow execution lifecycle
4. **Security Layer** - Dual-mode authentication (OIDC/API Key) with RBAC and ABAC
5. **Callback Service** - Async result delivery with retry logic

### Integration with Existing Quarkus Flow

- Uses existing `WorkflowRegistry.register(Workflow)` for runtime registration
- Executions use existing `WorkflowDefinition` beans and persistence infrastructure
- No changes needed to core workflow engine
- Leverages standard Quarkus security (`@RolesAllowed`, OIDC extension)

### Data Flow

```
Startup → WorkflowLoader reads path/classpath 
       → Parses YAML/JSON 
       → WorkflowRegistry.register() 
       → REST endpoints available

Runtime → Client calls /runner/exec 
        → Creates WorkflowDefinition execution 
        → Uses existing persistence 
        → Returns result (sync) or 202 + callback (async)
```

## Configuration Model

### Build-time Configuration

```properties
# Enable/disable definition management endpoints
quarkus.flow.runner.endpoints.definition.enabled=true|false  # Default: true
# When false, POST/PUT/DELETE /runner/definition endpoints are excluded from build
```

### Runtime Configuration

```properties
# Enable/disable entire runner feature
quarkus.flow.runner.enabled=true|false  # Default: true (if dependency added)

# Workflow definition source (mutually exclusive)
quarkus.flow.runner.source=path|classpath  # Required when enabled
quarkus.flow.runner.source.path=/path/to/workflows  # Required when source=path

# Security: Authentication method (mutually exclusive)
quarkus.flow.runner.security.type=oidc|api-key|none  # Default: none
# When type=oidc, requires quarkus-oidc extension in classpath
# When type=api-key, uses API Key authentication
# When type=none, endpoints are unprotected (dev only)

# Security: API Keys (only when type=api-key)
quarkus.flow.runner.security.api-keys."key1".secret=${FLOW_API_KEY_ADMIN}
quarkus.flow.runner.security.api-keys."key1".roles=flow-admin
quarkus.flow.runner.security.api-keys."key2".secret=${FLOW_API_KEY_INVOKER}
quarkus.flow.runner.security.api-keys."key2".roles=flow-invoker

# Security: ABAC namespace authorization
quarkus.flow.runner.security.namespace.claim=namespace  # JWT claim to check (OIDC mode)
quarkus.flow.runner.security.namespace.validate=true|false  # Default: true

# Callback settings
quarkus.flow.runner.callback.timeout=10s  # HTTP timeout per attempt
quarkus.flow.runner.callback.max-retries=3  # Default retries
quarkus.flow.runner.callback.require-https=true  # Enforce HTTPS in production
quarkus.flow.runner.callback.allowed-hosts=*  # Comma-separated patterns, default: all
quarkus.flow.runner.callback.blocked-ips=10.0.0.0/8,172.16.0.0/12,192.168.0.0/16  # SSRF prevention

# Guardrails
quarkus.http.limits.max-body-size=10M  # Standard Quarkus config
quarkus.flow.runner.limits.max-definitions-per-namespace=100  # Default: unlimited (-1)
quarkus.flow.runner.limits.rate-limit.execution.per-minute=60  # Default: unlimited (-1)
```

### Predefined Roles

- **`flow-admin`** - Full access (definition management + execution)
- **`flow-invoker`** - Execution only (POST/GET `/runner/exec/*`)

### Cloud Deployment Scenario

For immutable cloud deployments:

```properties
quarkus.flow.runner.enabled=true
quarkus.flow.runner.endpoints.definition.enabled=false  # Build-time: exclude definition endpoints
quarkus.flow.runner.source=path
quarkus.flow.runner.source.path=/deployments/workflows  # ConfigMap mount
quarkus.flow.runner.security.type=api-key
quarkus.flow.runner.security.api-keys."invoker".secret=${FLOW_API_KEY}  # From K8s Secret
quarkus.flow.runner.security.api-keys."invoker".roles=flow-invoker
```

## Component Architecture

### 1. Workflow Definition Loader

**Class:** `WorkflowDefinitionLoader`  
**Scope:** `@ApplicationScoped`

**Responsibilities:**
- Reads workflow definitions from configured source at startup
- Parses YAML/JSON using existing SDK detection
- Validates definitions (fail-fast on errors)
- Registers with `WorkflowRegistry`
- Provides manual reload capability

**Lifecycle:**

```java
@ApplicationScoped
public class WorkflowDefinitionLoader {
    
    @Inject
    WorkflowRegistry registry;
    
    @Inject
    FlowRunnerConfig config;
    
    void onStart(@Observes StartupEvent event) {
        if (!config.enabled()) return;
        loadDefinitions(); // Fail-fast if invalid
    }
    
    public void reload() {
        // Manual reload triggered by management endpoint
        // Atomic registry update, fail-fast on invalid definitions
    }
}
```

**Loading Strategy:**
- `source=classpath`: Scan `META-INF/workflows/` (or configurable path)
- `source=path`: Read all `.yaml`, `.yml`, `.json` from filesystem path
- Recursive directory scanning supported
- Duplicate workflow (same namespace+name) → fail-fast

### 2. Definition Management Resource

**Class:** `RunnerDefinitionResource`  
**Path:** `/runner/definition`  
**Conditional Inclusion:** Only when `quarkus.flow.runner.endpoints.definition.enabled=true`

**Endpoints:**

```java
@Path("/runner/definition")
public class RunnerDefinitionResource {
    
    @GET
    @RolesAllowed({"flow-admin", "flow-invoker"})
    @Produces(MediaType.APPLICATION_JSON)
    Response listDefinitions(@QueryParam("namespace") String namespace);
    // Lists all available workflow definitions
    // Optional namespace filter for ABAC-filtered results
    // Returns 200 OK with array of workflow metadata
    
    @POST
    @RolesAllowed("flow-admin")
    @Consumes({MediaType.APPLICATION_JSON, "application/yaml"})
    Response createDefinition(String workflowContent);
    // Returns 201 Created or 409 Conflict if exists
    
    @PUT
    @Path("/{namespace}/{name}")
    @RolesAllowed("flow-admin")
    @Consumes({MediaType.APPLICATION_JSON, "application/yaml"})
    Response updateDefinition(
        @PathParam String namespace, 
        @PathParam String name, 
        String workflowContent);
    // Returns 200 OK or 404 Not Found
    
    @DELETE
    @Path("/{namespace}/{name}")
    @RolesAllowed("flow-admin")
    Response deleteDefinition(
        @PathParam String namespace, 
        @PathParam String name);
    // Unloads definition, allows running executions to complete
    // Returns 204 No Content or 404 Not Found
}
```

**Response Model (GET /runner/definition):**

```json
[
  {
    "namespace": "my-namespace",
    "name": "order-processing",
    "version": "1.0",
    "description": "Process customer orders",
    "inputSchema": {
      "type": "object",
      "properties": {
        "orderId": { "type": "string" },
        "customerId": { "type": "string" },
        "items": {
          "type": "array",
          "items": {
            "type": "object",
            "properties": {
              "productId": { "type": "string" },
              "quantity": { "type": "integer" }
            }
          }
        }
      },
      "required": ["orderId", "customerId"]
    }
  }
]
```

**Input Schema Extraction:**
- Extract from workflow definition's `input` property (JSON Schema format)
- If not defined in workflow, return `null` or empty schema
- Enables clients to validate inputs before execution

**ABAC Filtering:**
- When namespace authorization is enabled, filter results based on user's authorized namespaces
- Extract from JWT claims or API key configuration
- Only return workflows the user can execute

**Build-time Exclusion:**
- When `quarkus.flow.runner.endpoints.definition.enabled=false`, POST/PUT/DELETE endpoints are not included in the native image/JAR
- GET endpoint remains available (discovery is needed even in immutable mode)
- Achieved via build step that conditionally adds write operations

### 3. Execution Resource

**Class:** `RunnerExecutionResource`  
**Path:** `/runner/exec`

**Endpoints:**

```java
@Path("/runner/exec")
@RolesAllowed({"flow-admin", "flow-invoker"})
public class RunnerExecutionResource {
    
    @POST
    @Path("/{namespace}/{name}")
    @Consumes(MediaType.APPLICATION_JSON)
    Response executeWorkflow(
        @PathParam String namespace,
        @PathParam String name,
        @QueryParam("wait") @DefaultValue("false") boolean wait,
        ExecutionRequest request);
    // wait=true: Sync execution, returns result (200 OK)
    // wait=false: Async execution, returns execution ID (202 Accepted)
    
    @GET
    @Path("/{namespace}/{name}/{id}/status")
    Response getStatus(
        @PathParam String namespace, 
        @PathParam String name, 
        @PathParam String id);
    // Returns 200 with status details for active executions
    // Returns 404 if execution not found (never existed, or completed/aborted and removed from memory)
    
    @PUT
    @Path("/{namespace}/{name}/{id}/resume")
    @Consumes(MediaType.APPLICATION_JSON)
    Response resumeExecution(
        @PathParam String namespace, 
        @PathParam String name, 
        @PathParam String id, 
        JsonNode payload);
    // Returns 200 OK or 404
    
    @DELETE
    @Path("/{namespace}/{name}/{id}")
    Response abortExecution(
        @PathParam String namespace, 
        @PathParam String name, 
        @PathParam String id);
    // Returns 204 No Content or 404
}
```

**Request Model:**

```java
public class ExecutionRequest {
    public JsonNode input;           // Workflow input
    public CallbackConfig callback;  // Optional callback configuration
}

public class CallbackConfig {
    public String url;                    // Required: callback URL
    public Map<String, String> headers;   // Optional: custom headers (e.g., auth)
}
```

**Namespace Authorization (ABAC):**
- Custom interceptor validates `{namespace}` param against user's token claims
- Blocks requests if user not authorized for that namespace

### 4. Security Layer

#### 4.1 Authentication Modes

**OIDC Mode** (`security.type=oidc`):
- Leverages standard Quarkus OIDC extension
- Users configure `quarkus.oidc.*` properties as usual
- JWT tokens validated by Quarkus OIDC
- Roles extracted from token claims (configurable claim name)

**API Key Mode** (`security.type=api-key`):
- Custom `ContainerRequestFilter` intercepts requests
- Reads `Authorization: Bearer <api-key>` header
- Validates key against configured secrets
- Maps key to roles (`flow-admin` or `flow-invoker`)
- Injects `SecurityIdentity` for `@RolesAllowed` to work

**None Mode** (`security.type=none`):
- All endpoints unprotected
- Development/testing only
- Log warning on startup when enabled

#### 4.2 Authorization (RBAC + ABAC)

**Role-Based Access Control:**

```java
// Predefined roles
public static final String ROLE_ADMIN = "flow-admin";
public static final String ROLE_INVOKER = "flow-invoker";

// Endpoint protection
@RolesAllowed(ROLE_ADMIN)  // Definition management
@RolesAllowed({ROLE_ADMIN, ROLE_INVOKER})  // Execution
```

**Attribute-Based Access Control (Namespace Validation):**

```java
@Provider
@Priority(Priorities.AUTHORIZATION)
public class NamespaceAuthorizationFilter implements ContainerRequestFilter {
    
    @Override
    public void filter(ContainerRequestContext ctx) {
        String namespace = extractNamespaceFromPath(ctx);
        if (namespace != null && config.namespaceValidateEnabled()) {
            validateNamespaceAccess(securityIdentity, namespace);
            // Throws 403 Forbidden if user not authorized for namespace
        }
    }
}
```

**Namespace Claim Validation:**
- Extract namespace from path: `/runner/exec/{namespace}/...`
- Check JWT claim (e.g., `namespace`, `namespaces`, or custom claim)
- Claim can be single value or array
- If claim missing or doesn't match → 403 Forbidden
- For API Key mode: optionally configure namespace per key

#### 4.3 Attack Surface Mitigation

**Safe Deserialization:**
- Use SDK's built-in YAML/JSON parser (already safe)
- Disable polymorphic type handling if not needed
- Validate workflow schema before registration
- Catch and fail-fast on parse errors

**Quotas and Limits:**
- Max body size: Standard Quarkus `quarkus.http.limits.max-body-size`
- Max definitions per namespace: Track in-memory counter, reject when limit reached
- Rate limiting: Use Quarkus Rate Limiting extension or custom implementation
  - Apply to execution endpoints
  - Per-user or per-namespace limits
  - Returns 429 Too Many Requests

**SSRF Prevention (Callbacks):**
- Validate callback URLs (HTTPS only in production)
- Block private IP ranges by default (10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16, 127.0.0.1)
- Configurable allow-list for internal endpoints if needed
- Timeout per callback attempt to prevent hanging

### 5. Callback Service

**Class:** `CallbackService`  
**Scope:** `@ApplicationScoped`

**Responsibilities:**
- Store callback configurations in-memory keyed by execution ID
- Deliver execution results via HTTP POST on completion/failure
- Implement retry logic with exponential backoff
- Clean up callback state after delivery or exhausted retries

**Flow:**

```
Workflow execution completes/fails
    ↓
Lookup callback config by execution ID
    ↓  (NOT FOUND → skip callback)
Build callback payload:
{
  "executionId": "...",
  "namespace": "...",
  "name": "...",
  "status": "COMPLETED|FAILED|ABORTED",
  "result": { /* output */ },
  "error": "..." // if failed
}
    ↓
POST to callback URL with custom headers
    ↓
Success? → Delete callback from memory
    ↓
Failure? → Retry with exponential backoff
           - Attempt 1: immediate
           - Attempt 2: +1s
           - Attempt 3: +2s
           - Attempt 4: +4s (final)
    ↓
After max retries → Log error, delete from memory
```

**Error Handling:**
- Callback failures do NOT fail the workflow execution
- All callback errors logged with execution ID and URL for debugging
- Observability metrics: callback success/failure counts, retry counts

## Data Flow and Operations

### Startup Flow (Definition Loading)

```
Application Start
    ↓
WorkflowDefinitionLoader.onStart()
    ↓
Check config.enabled() && config.source()
    ↓
Read definitions from path/classpath
    ↓
For each .yaml/.yml/.json file:
    ↓
Parse using SDK (auto-detect format)
    ↓
Validate workflow definition
    ↓  (FAIL-FAST on error)
WorkflowRegistry.register(workflow)
    ↓
Log: "Loaded N workflows from {source}"
    ↓
REST endpoints available
```

### Definition Management Flow

**GET /runner/definition:**

```
Request with optional ?namespace filter
    ↓
Authentication (OIDC/API Key)
    ↓
Authorization (flow-admin/flow-invoker role)
    ↓
Fetch all workflows from WorkflowRegistry
    ↓
ABAC: Filter by authorized namespaces
    ↓  (Extract from JWT claims or API key config)
Apply namespace query filter (if provided)
    ↓
Extract metadata for each workflow:
    - namespace, name, version, description
    - input schema (from workflow definition)
    ↓
200 OK with JSON array
```

**POST /runner/definition:**

```
Request with YAML/JSON body
    ↓
Authentication (OIDC/API Key)
    ↓
Authorization (flow-admin role)
    ↓
Parse workflow definition
    ↓
Extract namespace+name
    ↓
Check if already exists
    ↓  (EXISTS → 409 Conflict)
Validate definition
    ↓  (INVALID → 400 Bad Request)
WorkflowRegistry.register(workflow)
    ↓
201 Created
```

**DELETE /runner/definition/{namespace}/{name}:**

```
Authentication + Authorization
    ↓
Lookup workflow in registry
    ↓  (NOT FOUND → 404)
Remove from registry (definition unloaded)
    ↓  (Running executions continue)
Block new executions for this workflow
    ↓
204 No Content
```

### Execution Flow

**POST /runner/exec/{namespace}/{name}?wait=false (Async with Callback):**

Request body:
```json
{
  "input": { /* workflow input */ },
  "callback": {
    "url": "https://example.com/workflow-results",
    "headers": {
      "Authorization": "Bearer user-token"
    }
  }
}
```

Flow:
```
Authentication (OIDC/API Key)
    ↓
Authorization (flow-admin/flow-invoker)
    ↓
ABAC: Validate namespace access
    ↓  (UNAUTHORIZED → 403 Forbidden)
Rate limiting check
    ↓  (EXCEEDED → 429 Too Many Requests)
Validate callback URL (if provided)
    ↓  (Invalid/blocked → 400 Bad Request)
Lookup WorkflowDefinition
    ↓  (NOT FOUND → 404)
Start workflow execution (async)
    ↓
Generate execution ID
    ↓
Store callback config in-memory (if provided)
    ↓
202 Accepted + {executionId}
    ↓
--- Workflow executes in background ---
    ↓
On completion/failure:
    ↓
If callback configured:
    ↓
    POST to callback URL (with retries)
    ↓
    Delete callback from memory
    ↓
Delete execution from memory
```

**POST /runner/exec/{namespace}/{name}?wait=true (Sync):**

```
Same flow until execution start
    ↓
Start workflow execution (sync)
    ↓
Wait for completion
    ↓
Return result
    ↓
200 OK + {result}
    ↓
(Execution not persisted after return)
```

**GET /runner/exec/{namespace}/{name}/{id}/status:**

```
Authentication + Authorization + ABAC
    ↓
Query existing persistence layer
    ↓  (NOT FOUND/COMPLETED → 404 + observability message)
Return status (RUNNING, WAITING, etc.)
    ↓
200 OK + {status, details}
```

### Manual Reload Flow

**Trigger:** Management endpoint or external signal (ConfigMap change detection)

```
Reload triggered
    ↓
Read definitions from configured source
    ↓
Parse and validate all definitions
    ↓  (FAIL-FAST on any error → crash application)
Atomic registry update
    ↓  (Running executions continue with old definitions)
New executions use new definitions
    ↓
Log: "Reloaded N workflows"
```

## OpenAPI Integration

The runner extension provides automatic OpenAPI documentation via Quarkus SmallRye OpenAPI integration.

**Setup:**

Users add the Quarkus OpenAPI extension to their project:

```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-smallrye-openapi</artifactId>
</dependency>
```

**Generated Documentation:**

- **OpenAPI Spec**: Available at `/q/openapi` (JSON/YAML)
- **Swagger UI**: Available at `/q/swagger-ui` (interactive API documentation)
- **Schema Definitions**: Automatically generated from JAX-RS annotations

**Annotations:**

The runner resources use standard JAX-RS and OpenAPI annotations for documentation:

```java
@Path("/runner/definition")
@Tag(name = "Workflow Definitions", description = "Manage workflow definitions")
public class RunnerDefinitionResource {
    
    @GET
    @Operation(summary = "List workflow definitions", 
               description = "Returns all workflow definitions accessible to the user")
    @APIResponse(responseCode = "200", description = "List of workflows",
                 content = @Content(schema = @Schema(implementation = WorkflowMetadata[].class)))
    @RolesAllowed({"flow-admin", "flow-invoker"})
    Response listDefinitions(@Parameter(description = "Filter by namespace") 
                            @QueryParam("namespace") String namespace);
}
```

**Security Schemes:**

The OpenAPI spec includes security scheme definitions for both authentication modes:

```yaml
securitySchemes:
  bearer-oidc:
    type: http
    scheme: bearer
    bearerFormat: JWT
    description: OIDC JWT token
  bearer-api-key:
    type: http
    scheme: bearer
    description: API Key authentication
```

**Benefits:**

- Automatic API documentation without manual maintenance
- Client code generation support (OpenAPI Generator, etc.)
- Interactive testing via Swagger UI
- Schema validation for request/response models
- Discovery of workflow input schemas via GET /runner/definition

## Error Handling

### Startup Errors (Fail-Fast)

**Invalid Configuration:**
- Missing required config → `RuntimeException` with clear message
- Invalid source path → File not found exception
- Mutually exclusive configs → Validation error

**Definition Loading Errors:**
- Parse error (invalid YAML/JSON) → Fail application startup
- Invalid workflow schema → Fail application startup
- Duplicate workflows → Fail application startup
- Missing external resources (OpenAPI) → Fail application startup

**Security Configuration Errors:**
- Invalid API key config → Fail startup
- OIDC enabled but extension missing → Fail startup with helpful message

### Runtime Errors (Graceful Degradation)

**Definition Management Errors:**

```
GET /runner/definition:
  - 200 OK: Always returns (empty array if no workflows or none authorized)
  - 403 Forbidden: If namespace filter provided but user not authorized for it
  
POST /runner/definition:
  - 400 Bad Request: Invalid YAML/JSON, schema validation failed
  - 409 Conflict: Workflow already exists
  - 413 Payload Too Large: Exceeds max-body-size
  - 429 Too Many Requests: Quota exceeded (max-definitions-per-namespace)
  
PUT /runner/definition/{namespace}/{name}:
  - 400 Bad Request: Invalid definition
  - 404 Not Found: Workflow doesn't exist
  
DELETE /runner/definition/{namespace}/{name}:
  - 404 Not Found: Workflow doesn't exist
```

**Execution Errors:**

```
POST /runner/exec/{namespace}/{name}:
  - 400 Bad Request: Invalid input JSON, invalid callback URL
  - 403 Forbidden: Namespace not authorized (ABAC)
  - 404 Not Found: Workflow not found
  - 429 Too Many Requests: Rate limit exceeded
  - 500 Internal Server Error: Workflow execution start failed
  
GET /runner/exec/{namespace}/{name}/{id}/status:
  - 404 Not Found: "Execution not found. It may have completed, been aborted, 
                    or never existed. Check observability platforms for execution history."
  
PUT /runner/exec/{namespace}/{name}/{id}/resume:
  - 400 Bad Request: Invalid payload
  - 404 Not Found: Execution not found
  - 409 Conflict: Execution not in resumable state
  
DELETE /runner/exec/{namespace}/{name}/{id}:
  - 404 Not Found: Execution not found/already completed
```

**Callback Errors:**

```
Callback delivery failures:
  - Log error with execution ID, callback URL, attempt number
  - Retry with exponential backoff (1s, 2s, 4s)
  - After 3 failures: Log final error, delete from memory
  - Do NOT fail the workflow execution due to callback failure
```

### Error Response Format

Standard JSON error structure:

```json
{
  "error": "WorkflowNotFound",
  "message": "Workflow 'my-namespace:my-workflow' not found",
  "path": "/runner/exec/my-namespace/my-workflow",
  "timestamp": "2026-05-05T10:30:00Z",
  "executionId": "abc-123"
}
```

## Testing Strategy

### Unit Tests

**Workflow Loader Tests:**
- Parse valid YAML/JSON definitions
- Fail-fast on invalid definitions
- Handle missing files/paths
- Duplicate workflow detection

**Security Tests:**
- API Key validation and role mapping
- OIDC token validation (mocked)
- Namespace authorization (ABAC)
- SSRF prevention in callback URLs

**Callback Tests:**
- Retry logic with exponential backoff
- Success on first attempt
- Failure after max retries
- Memory cleanup after delivery

### Integration Tests

**Definition Management:**
- GET list of workflow definitions (all, filtered by namespace)
- GET with ABAC filtering (verify namespace authorization)
- GET input schema extraction from workflow definitions
- POST workflow definition (success, conflict, validation errors)
- PUT to update existing workflow
- DELETE workflow (graceful unload)
- Build-time exclusion test (when `definition.enabled=false`, GET remains but POST/PUT/DELETE excluded)

**Execution Endpoints:**
- Sync execution (`?wait=true`) - returns result
- Async execution (`?wait=false`) - returns 202
- Async with callback - mock HTTP server to verify delivery
- Resume suspended workflow
- Abort running execution
- Status endpoint (running vs 404)

**Security Integration:**
- API Key authentication (valid/invalid keys, role enforcement)
- OIDC authentication (mocked IdP using WireMock)
- Namespace authorization (authorized/forbidden)
- Rate limiting enforcement

**Multi-source Loading:**
- Load from classpath (`source=classpath`)
- Load from filesystem path (`source=path`)
- Fail when source not configured

### Test Patterns

**Mocking:**
- Use `quarkus-mockito` for mocking internal services
- Use `WireMock` for external callback endpoints and OIDC IdP
- Mock LLM calls if workflows use LangChain4j

**Test Isolation:**
- Random ports (no fixed 8080)
- Separate test profiles for different scenarios
- Clean registry state between tests

**AssertJ Assertions:**
- Follow existing project convention
- Use `@DisplayName` with snake_case test names

## Implementation Structure

### Module Layout

```
runner/
├── runtime/
│   ├── config/
│   │   ├── FlowRunnerConfig.java
│   │   ├── FlowRunnerSecurityConfig.java
│   │   └── FlowRunnerCallbackConfig.java
│   ├── loader/
│   │   └── WorkflowDefinitionLoader.java
│   ├── rest/
│   │   ├── RunnerDefinitionResource.java
│   │   └── RunnerExecutionResource.java
│   ├── security/
│   │   ├── ApiKeyAuthenticationFilter.java
│   │   ├── NamespaceAuthorizationFilter.java
│   │   └── ApiKeySecurityIdentityAugmentor.java
│   ├── callback/
│   │   ├── CallbackService.java
│   │   └── CallbackRetryHandler.java
│   └── limits/
│       └── ExecutionRateLimiter.java
├── deployment/
│   ├── FlowRunnerProcessor.java (build steps)
│   └── devui/ (optional Dev UI integration)
└── integration-tests/
    ├── src/test/java/.../
    └── src/test/resources/workflows/
```

### Key Implementation Notes

- Reuse `WorkflowRegistry.register()` - no changes to core needed
- Leverage existing persistence infrastructure - no custom storage layer
- Standard Quarkus patterns (CDI, REST, security)
- Follow existing Quarkus Flow conventions (AssertJ, snake_case tests)

## Future Enhancements

**Deferred to later releases:**

1. **WebSocket Streaming** - `/runner/exec/{ns}/{name}/{id}/subscribe` for real-time execution events
2. **Storage SPI** - Pluggable storage for execution history (Redis, JDBC)
3. **Advanced Rate Limiting** - Per-namespace, per-user quotas with sliding windows
4. **Workflow Versioning** - Support multiple versions of same workflow
5. **Execution History API** - Query completed executions (requires durable storage)
6. **GraphQL API** - Alternative to REST for complex queries
7. **Batch Execution** - Execute multiple workflows in one request

## Security Considerations Summary

1. **Authentication:** OIDC (enterprise) or API Key (M2M/webhooks), runtime-toggled
2. **Authorization:** RBAC (`flow-admin`, `flow-invoker`) + ABAC (namespace validation)
3. **Attack Mitigation:**
   - Safe YAML/JSON deserialization
   - Definition quotas per namespace
   - Rate limiting on execution endpoints
   - SSRF prevention for callbacks (block private IPs, HTTPS enforcement)
   - Request size limits
4. **Runtime Toggle:** Both auth methods in classpath, activated via config (operator injects via Secret)

## Success Criteria

- ✅ Workflows load from filesystem paths and classpath at startup
- ✅ REST API provides full execution lifecycle (start, status, resume, abort)
- ✅ Async execution with callback delivery and retry logic
- ✅ Dual authentication (OIDC + API Key) with RBAC and ABAC
- ✅ Definition management endpoints conditionally excluded at build-time
- ✅ Fail-fast on invalid definitions (startup and reload)
- ✅ Zero changes to core workflow engine
- ✅ Integration tests cover all endpoints and security modes
- ✅ Documentation includes cloud deployment patterns

## References

- Issue: https://github.com/quarkiverse/quarkus-flow/issues/52
- CNCF Serverless Workflow Spec: https://serverlessworkflow.io/
- Quarkus Extension Guide: https://quarkus.io/guides/writing-extensions
- Quarkus OIDC: https://quarkus.io/guides/security-oidc-bearer-token-authentication
