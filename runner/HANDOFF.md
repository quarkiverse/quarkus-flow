# Quarkus Flow Runner - Deferred Features

This document outlines features that were designed but deferred for future implementation. Each section below can be copied as a separate GitHub issue.

---

## Issue 1: Runtime Workflow Definition Management (CRUD API)

### Description

Implement runtime CRUD operations for workflow definitions, allowing users to create, update, and delete workflow definitions via REST API without redeploying the application. This is useful for dynamic workflow management in development and testing environments.

### Current State

- ✅ GET `/runner/definitions` - List workflow definitions (metadata)
- ✅ GET `/runner/definitions/{namespace}/{name}/{version}` - Retrieve full workflow definition
- ❌ POST `/runner/definitions` - Create new workflow definition
- ❌ PUT `/runner/definitions/{namespace}/{name}/{version}` - Update existing workflow definition
- ❌ DELETE `/runner/definitions/{namespace}/{name}/{version}` - Delete workflow definition

### Technical Requirements

**1. Workflow Definition Persistence:**
- Design and implement persistence layer for workflow definitions
- Options to consider:
  - Database table (JPA) - requires schema migration
  - Redis/distributed cache
  - Filesystem with file watching
- Must support versioning (namespace:name:version uniqueness)
- Must handle concurrent updates safely

**2. Definition Lifecycle Management:**
- Validate workflow definitions before persisting (use SDK's `WorkflowReader` validation)
- Prevent deletion of definitions with active executions
- Support graceful shutdown of running workflows when definition is deleted
- Handle definition reloads when updated

**3. Security Considerations:**
- YAML/JSON deserialization safety (already handled by SDK)
- Authorization: only `flow-admin` role can modify definitions
- Validate workflow doesn't reference external resources that could be exploited
- Rate limiting on definition creation to prevent abuse

**4. OpenAPI Documentation:**
- Complete the OpenAPI annotations for POST/PUT/DELETE endpoints
- Document request/response schemas
- Add examples for workflow creation

### API Specification (from ADR)

**POST /runner/definitions:**
```http
POST /runner/definitions
Content-Type: application/json or application/yaml

{workflow definition}

Response:
201 Created
Location: /runner/definitions/{namespace}/{name}/{version}

409 Conflict - if workflow version already exists
400 Bad Request - if definition is invalid
```

**PUT /runner/definitions/{namespace}/{name}/{version}:**
```http
PUT /runner/definitions/{namespace}/{name}/{version}
Content-Type: application/json or application/yaml

{workflow definition}

Response:
200 OK - if updated successfully
404 Not Found - if workflow doesn't exist
400 Bad Request - if definition is invalid
409 Conflict - if version in path doesn't match version in definition
```

**DELETE /runner/definitions/{namespace}/{name}/{version}:**
```http
DELETE /runner/definitions/{namespace}/{name}/{version}

Response:
204 No Content - if deleted successfully
404 Not Found - if workflow doesn't exist
409 Conflict - if workflow has active executions
```

### Acceptance Criteria

- [ ] Design persistence schema for workflow definitions
- [ ] Implement POST endpoint to create workflow definitions
- [ ] Implement PUT endpoint to update workflow definitions
- [ ] Implement DELETE endpoint to remove workflow definitions
- [ ] Validate workflow definitions before persisting
- [ ] Prevent deletion of workflows with active executions
- [ ] Add comprehensive integration tests for all CRUD operations
- [ ] Document API with OpenAPI annotations
- [ ] Update user documentation with examples

### Implementation Notes

- **Build-time flag:** ADR mentions `quarkus.flow.runner.endpoints.definition.enabled` to conditionally include write operations at build time. For immutable cloud deployments, this should be `false`.
- **Atomic updates:** Use optimistic locking or versioning to handle concurrent updates
- **Backward compatibility:** Existing classpath/filesystem-loaded workflows should continue to work
- **Migration path:** Consider how to migrate from filesystem-based to DB-based definitions

### Related Files

- `runner/runtime/src/main/java/io/quarkiverse/flow/runner/resources/DefinitionResource.java` - Add POST/PUT/DELETE endpoints
- `runner/runtime/src/main/java/io/quarkiverse/flow/runner/WorkflowDefinitionRuntimeLoader.java` - May need to support DB-based loading
- `adr/2026-05-05-workflow-runner-rest-api-design.md` - Full specification

---

## Issue 2: Async Execution Callbacks

### Description

Implement HTTP callback mechanism for asynchronous workflow executions, allowing users to receive execution results via HTTP POST to a specified URL. This enables fire-and-forget workflow execution patterns with reliable result delivery.

### Current State

- ✅ Async execution with `wait=false` returns 202 with `instanceId`
- ❌ No callback mechanism - users cannot receive results automatically
- ❌ No callback persistence - callbacks lost on pod restart
- ❌ No retry logic for failed callback deliveries

### Technical Requirements

**1. Callback Configuration Model:**

Add to `ExecutionRequest` (or create new model):
```java
public class ExecutionRequest {
    public Map<String, Object> input;
    public CallbackConfig callback;  // Optional
}

public class CallbackConfig {
    public String url;                    // Required: callback URL
    public Map<String, String> headers;   // Optional: custom headers (auth tokens)
    public int maxRetries;                // Optional: override default retries
}
```

**2. Callback Persistence:**

Design options:
- **Database table** (recommended for durability):
  ```sql
  CREATE TABLE workflow_callbacks (
      instance_id VARCHAR(255) PRIMARY KEY,
      callback_url VARCHAR(2048) NOT NULL,
      headers JSONB,
      max_retries INT DEFAULT 3,
      retry_count INT DEFAULT 0,
      last_attempt TIMESTAMP,
      created_at TIMESTAMP DEFAULT NOW()
  );
  ```
- **Redis** - Fast, supports TTL, good for high-throughput scenarios
- **In-memory with persistence snapshot** - Hybrid approach

**3. Callback Delivery Service:**

```java
@ApplicationScoped
public class CallbackService {
    
    @Inject
    CallbackRepository repository;
    
    @Inject
    @RestClient
    HttpClient httpClient;
    
    @ConsumeEvent("workflow.completed")  // Listen to workflow completion events
    public Uni<Void> onWorkflowCompleted(WorkflowCompletedEvent event) {
        return loadCallback(event.instanceId())
            .onItem().ifNotNull().transformToUni(this::deliverCallback);
    }
    
    private Uni<Void> deliverCallback(CallbackInfo callback) {
        // Build callback payload
        // POST to callback.url with retry logic
        // Use Mutiny retry with exponential backoff
        // Delete callback after successful delivery or exhausted retries
    }
}
```

**4. Callback Payload Format:**

```json
{
  "instanceId": "abc-123",
  "namespace": "my-namespace",
  "name": "my-workflow",
  "version": "1.0.0",
  "status": "COMPLETED|FAILED|ABORTED",
  "startedAt": "2026-06-02T10:00:00Z",
  "completedAt": "2026-06-02T10:05:30Z",
  "output": { /* workflow output */ },
  "error": "Error message if status=FAILED"
}
```

**5. Retry Logic:**

- **Exponential backoff:** 1s, 2s, 4s, 8s (configurable)
- **Max retries:** Default 3 (configurable per callback or globally)
- **Idempotency:** Include `X-Workflow-Instance-Id` header so receivers can deduplicate
- **Failure handling:** Log errors, emit metrics, delete callback after exhausted retries

**6. Security Considerations:**

- **SSRF Prevention:**
  - Block private IP ranges: `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`, `127.0.0.1`
  - Enforce HTTPS in production (configurable)
  - Allow-list for internal endpoints if needed
- **URL Validation:**
  - Validate URL format before persisting
  - Check for redirect loops
  - Set request timeout (default 10s)
- **Header Injection:**
  - Sanitize custom headers
  - Prevent header injection attacks

**7. Configuration:**

```properties
# Callback settings
quarkus.flow.runner.callback.enabled=true
quarkus.flow.runner.callback.timeout=10s
quarkus.flow.runner.callback.max-retries=3
quarkus.flow.runner.callback.retry-backoff=1s,2s,4s,8s
quarkus.flow.runner.callback.require-https=true
quarkus.flow.runner.callback.allowed-hosts=*
quarkus.flow.runner.callback.blocked-ips=10.0.0.0/8,172.16.0.0/12,192.168.0.0/16
```

### Acceptance Criteria

- [ ] Design and implement callback persistence (DB or Redis)
- [ ] Accept `callback` parameter in execution requests
- [ ] Deliver callbacks on workflow completion/failure
- [ ] Implement exponential backoff retry logic
- [ ] Add SSRF protection (block private IPs, enforce HTTPS)
- [ ] Delete callbacks after successful delivery or exhausted retries
- [ ] Add metrics for callback success/failure rates
- [ ] Handle pod restarts gracefully (callbacks survive restarts)
- [ ] Integration tests with WireMock for callback delivery
- [ ] Document callback API with OpenAPI annotations
- [ ] Add user documentation with callback examples

### Implementation Notes

- **Event-driven:** Use CDI events or message bus to trigger callbacks when workflows complete
- **Non-blocking:** Use Mutiny/Reactive patterns for HTTP requests
- **Observability:** 
  - Metrics: callback delivery rate, retry count, failure rate
  - Logs: Include `instanceId` and `callbackUrl` in all log messages
  - Tracing: Propagate trace context in callback requests
- **Idempotency:** Receivers should handle duplicate callbacks (include instance ID in headers)
- **Alternative:** Consider using CloudEvents format for callback payloads

### Related Files

- `runner/runtime/src/main/java/io/quarkiverse/flow/runner/model/ExecutionRequest.java` - Add callback config
- `runner/runtime/src/main/java/io/quarkiverse/flow/runner/model/ExecutionResponse.java` - May need callback status
- `runner/runtime/src/main/java/io/quarkiverse/flow/runner/resources/RunnerExecResource.java` - Accept callback param
- `adr/2026-05-05-workflow-runner-rest-api-design.md` - Lines 487-526 (Callback Service specification)

---

## Issue 3: Multi-Pod Execution Management (Status/Resume/Cancel)

### Description

Implement status, resume, and cancel operations for workflow executions in multi-pod deployments. This requires distributed coordination to route requests to the correct pod or implement a centralized instance registry.

### Problem Statement

**Current Architecture:**
- Each pod has its own `WorkflowApplication` bean with in-memory workflow instances
- Persistence layer (JPA) filters by `applicationId` (pod-specific identifier)
- When a workflow executes on **Pod A**, only Pod A has the instance in memory
- Status/resume/cancel requests may land on **Pod B** via load balancer → 404 Not Found

**Example Scenario:**
```
1. POST /runner/exec/ns/wf → Load balancer → Pod A → instance-123 created
2. GET /runner/exec/instance-123/status → Load balancer → Pod B → 404 (not in Pod B!)
```

### Technical Requirements

**This feature requires distributed coordination and is OUT OF SCOPE for Quarkus Flow library.** The solution should be implemented at the **platform/operator layer**, not within Quarkus Flow itself.

**Potential Solutions (for platform layer):**

**Option 1: Sticky Sessions (Session Affinity)**
- Configure load balancer to route requests from the same client to the same pod
- **Pros:** Simple, no code changes needed
- **Cons:** 
  - Doesn't work across different clients/sessions
  - Breaks if pod restarts
  - Not suitable for API-based access (no session concept)

**Option 2: Centralized Instance Registry**
- Maintain a distributed map: `instanceId → podId/applicationId`
- When status/resume/cancel request arrives, lookup which pod owns the instance
- Forward request to correct pod via service mesh or direct HTTP call
- **Pros:** Works across clients, survives pod restarts if persisted
- **Cons:** 
  - Requires distributed coordination (Redis, etcd, Hazelcast)
  - Adds latency for lookups
  - Registry must be kept in sync

**Option 3: Cluster-Scoped Persistence**
- Modify persistence layer to NOT filter by `applicationId`
- All pods can query all instances
- When pod receives status/resume/cancel, check local memory first, then query persistence
- If found in persistence but not in memory, send message to owning pod (via message bus)
- **Pros:** No sticky sessions needed
- **Cons:**
  - Requires message bus (Kafka, AMQP, etc.)
  - Complex synchronization logic
  - Breaking change to persistence API

**Option 4: Kubernetes StatefulSet + Pod Affinity**
- Use StatefulSets with predictable pod names (`flow-0`, `flow-1`, `flow-2`)
- Hash `instanceId` to determine which pod should handle it
- Configure load balancer to route based on hash
- **Pros:** Deterministic routing, no external dependencies
- **Cons:**
  - Requires Kubernetes-specific setup
  - Doesn't work outside K8s
  - Pod failures require rehashing

**Recommended Approach (for platform layer):**

**Option 5: Operator-Managed Coordination**
- Implement in **Logic Operator** (mentioned by user)
- Operator maintains instance registry using Kubernetes API (ConfigMaps or CRDs)
- Operator injects coordination sidecar or uses service mesh
- Quarkus Flow remains agnostic to deployment topology

### API Specification (Deferred)

**GET /runner/exec/{id}/status:**
```http
GET /runner/exec/{id}/status

Response:
200 OK
{
  "instanceId": "abc-123",
  "namespace": "my-namespace",
  "name": "my-workflow",
  "version": "1.0.0",
  "status": "RUNNING|WAITING|COMPLETED|FAILED|ABORTED",
  "startedAt": "2026-06-02T10:00:00Z",
  "completedAt": null,
  "currentTask": "process-order",
  "progress": "3/5 tasks completed"
}

404 Not Found - if instance doesn't exist or was completed/aborted
```

**PUT /runner/exec/{id}/resume:**
```http
PUT /runner/exec/{id}/resume
Content-Type: application/json

{
  "payload": { /* resume data */ }
}

Response:
200 OK - if resumed successfully
404 Not Found - if instance doesn't exist
409 Conflict - if instance not in resumable state (e.g., already completed)
```

**DELETE /runner/exec/{id}:**
```http
DELETE /runner/exec/{id}

Response:
204 No Content - if aborted successfully
404 Not Found - if instance doesn't exist
409 Conflict - if instance already completed/aborted
```

### Acceptance Criteria

**Note:** This issue should only be implemented at the platform/operator layer, NOT in Quarkus Flow library.

- [ ] Design distributed coordination mechanism (operator-level)
- [ ] Implement instance registry (Kubernetes CRD or external store)
- [ ] Implement request routing to correct pod
- [ ] Handle pod failures and instance migration
- [ ] Add status endpoint with multi-pod support
- [ ] Add resume endpoint with distributed coordination
- [ ] Add cancel endpoint with distributed coordination
- [ ] Integration tests simulating multi-pod deployment
- [ ] Document deployment requirements (K8s StatefulSet, operator, etc.)
- [ ] Update OpenAPI documentation

### Current Limitation for Standalone Users

For users deploying Quarkus Flow **without** the Logic Operator or platform layer:

**Limitation:** Status/resume/cancel operations are **NOT supported** in multi-pod deployments.

**Workarounds:**
1. **Single-pod deployment** - Deploy only one replica (not HA)
2. **Observability instead of status API:**
   - Use Prometheus metrics to track workflow execution
   - Use OpenTelemetry tracing to monitor progress
   - Emit custom CloudEvents at workflow completion
3. **Manual callback implementation:**
   - Add HTTP call at end of workflow to send results
   - Use workflow's built-in function support to POST results
4. **External monitoring:**
   - Query persistence layer directly (if enabled)
   - Build custom dashboard on top of workflow events

### Implementation Notes

- **DO NOT implement this in Quarkus Flow core library**
- Document the limitation clearly in user docs
- Provide example of single-pod deployment for users who need status/resume/cancel
- Provide example of observability-based monitoring as alternative
- Consider adding a "single-pod mode" flag that enables status/resume/cancel (fails if multiple pods detected)

### Related Files

- `runner/runtime/src/main/java/io/quarkiverse/flow/runner/resources/RunnerExecResource.java` - Where endpoints would be added (DON'T add them)
- `persistence/jpa/src/main/java/io/quarkiverse/flow/persistence/jpa/JpaInstanceOperations.java` - Shows `applicationId` filtering issue (line 144)
- `adr/2026-05-05-workflow-runner-rest-api-design.md` - Lines 329-350 (Status/Resume/Abort specification)
- User documentation - Should document this limitation

---

## Summary

These three features were designed in the original ADR but deferred due to architectural complexity:

1. **Runtime Definition CRUD** - Needs persistence layer design
2. **Async Callbacks** - Needs callback persistence and retry infrastructure  
3. **Multi-Pod Management** - OUT OF SCOPE for library, should be handled by platform/operator

Each can be tackled independently in future releases.
