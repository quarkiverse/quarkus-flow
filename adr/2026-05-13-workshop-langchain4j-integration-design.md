# Workshop LangChain4j Integration - Design Specification

**Date**: 2026-05-13  
**Status**: Proposed  
**Author**: Claude Code (with Ricardo Zanini)  
**Issue**: [#547](https://github.com/quarkiverse/quarkus-flow/issues/547)

## Context

The Quarkus LangChain4j Workshop (https://quarkus.io/quarkus-workshop-langchain4j/) Section 2 teaches developers about agentic workflows using the car management system scenario. We want to integrate Quarkus Flow into this workshop to showcase Flow's production-ready capabilities (persistence, events, monitoring, K8s deployment) to Java developers.

## Decision

Enhance the existing workshop with **progressive enhancement callout boxes** that show how to add Quarkus Flow capabilities to the LangChain4j examples. This approach:
- Keeps the workshop fully functional without Flow
- Makes each enhancement opt-in and self-contained
- Demonstrates Flow's value proposition through hands-on examples
- Provides a natural learning path from basic AI agents to production-ready workflows

## Goals

### Success Criteria
1. **Non-disruptive**: Workshop remains fully functional without Flow enhancements
2. **Opt-in**: Each enhancement is a self-contained "try this" box users can skip
3. **Progressive**: Complexity increases step-by-step (visualization → events → persistence → production)
4. **Actionable**: Every enhancement includes copy-paste code that works immediately
5. **Showcase**: Demonstrates Flow's unique value: automatic workflow orchestration for LangChain4j agentic patterns

### Key Value Propositions to Communicate

**Note**: We're skipping "Dev UI visualization" as Phase 1 because `quarkus-langchain4j-agentic` already provides its own Dev UI showing topology and agent call sequences. Instead, we focus on what Flow uniquely adds:

- **Phase 1**: "Turn agents into event-driven workflows" (Kafka/CloudEvents integration)
- **Phase 2**: "Workflows survive restarts and handle async approvals" (Persistence + HITL)
- **Phase 3**: "Production observability out of the box" (Monitoring + K8s deployment)

### What Flow Adds (vs Pure LangChain4j)

**LangChain4j Agentic already provides**:
- Dev UI with topology visualization and agent call sequences
- Basic agent orchestration (@SequenceAgent, @ParallelAgent, etc.)

**Quarkus Flow uniquely adds**:
- **Event-driven orchestration**: Trigger workflows from Kafka/CloudEvents, emit completion events
- **CNCF Serverless Workflow engine**: Wrap agents in standardized workflows that mix AI + non-AI tasks
- **Production durability**: Persistence, pause/resume, long-running workflows
- **Observability**: Micrometer metrics, distributed tracing
- **K8s-ready deployment**: Leader election, multi-pod safety

**Value proposition**: Turn your LangChain4j agents into production-grade, event-driven workflows.

---

## Architecture

### Phase-by-Phase Integration

#### Phase 1: Event-Driven Workflows (Steps 01-03)

**Target Workshop Steps**:
- Step 03: Supervisor orchestration
- Step 04: (depends on workshop content - assuming workflow coordination)

**Enhancement Box Location**: Beginning of Step 03, before supervisor pattern explanation

**What Users Add**:

1. **Kafka dependency**:
```xml
<dependency>
  <groupId>io.quarkus</groupId>
  <artifactId>quarkus-messaging-kafka</artifactId>
</dependency>
```

2. **Configuration**:
```properties
quarkus.flow.messaging.defaults-enabled=true
mp.messaging.incoming.flow-in.connector=smallrye-kafka
mp.messaging.incoming.flow-in.topic=car-returns
mp.messaging.outgoing.flow-out.connector=smallrye-kafka
mp.messaging.outgoing.flow-out.topic=car-cleaned
```

3. **Flow wrapper** (actual code users write):
```java
@ApplicationScoped
public class CarCleaningWorkflow extends Flow {
    
    @Inject CleaningAgent cleaningAgent;
    
    @Override
    public Workflow descriptor() {
        return FuncWorkflowBuilder
            .workflow("car-cleaning-events")
            // Listen for car-return events
            .schedule(on(toOne("org.acme.car.returned")))
            .tasks(
                // Call your existing agent
                function("evaluate", cleaningAgent::evaluate, CarInfo.class),
                
                // Emit completion event
                emit("done", "org.acme.car.cleaned")
            )
            .build();
    }
}
```

**Enhancement Box Content**:
```adoc
📬 **Quarkus Flow Enhancement: Event-Driven Car Processing**

Turn your car management system into an event-driven microservice by wrapping 
your agent in a Flow workflow:

1. Add Kafka support:
[source,xml]
----
<dependency>
  <groupId>io.quarkus</groupId>
  <artifactId>quarkus-messaging-kafka</artifactId>
</dependency>
----

2. Configure messaging in application.properties:
[source,properties]
----
quarkus.flow.messaging.defaults-enabled=true
mp.messaging.incoming.flow-in.connector=smallrye-kafka
mp.messaging.incoming.flow-in.topic=car-returns
mp.messaging.outgoing.flow-out.connector=smallrye-kafka  
mp.messaging.outgoing.flow-out.topic=car-cleaned
----

3. Create an event-driven Flow:
[source,java]
----
@ApplicationScoped
public class CarCleaningWorkflow extends Flow {
    
    @Inject CleaningAgent cleaningAgent;
    
    @Override
    public Workflow descriptor() {
        return FuncWorkflowBuilder
            .workflow("car-cleaning-events")
            // Listen for car-return events
            .schedule(on(toOne("org.acme.car.returned")))
            .tasks(
                // Call your existing agent
                function("evaluate", cleaningAgent::evaluate, CarInfo.class),
                
                // Emit completion event
                emit("done", "org.acme.car.cleaned")
            )
            .build();
    }
}
----

Now your system:
- 🎯 Auto-triggers when car-return events arrive on Kafka
- 🤖 Processes with your CleaningAgent
- 📤 Emits car-cleaned events when done
- 🔄 Enables microservice choreography patterns

Test it: `curl -X POST http://localhost:8080/api/cars/return/ABC123` 
→ triggers event → workflow runs → completion event emitted!

*Quarkus Dev Services automatically starts Kafka* - no manual setup!
```

**Learning Outcome**: Users see the complete event-driven pattern: listen → process → emit.

---

#### Phase 2: Human-in-the-Loop & Persistence (Step 05)

**Target Workshop Step**: Step 05 (Human-in-the-loop - already in workshop)

**Enhancement Box Location**: After the basic HITL pattern is shown, before multimodal features

**What Users Add**:
```xml
<dependency>
  <groupId>io.quarkiverse.flow</groupId>
  <artifactId>quarkus-flow-persistence</artifactId>
</dependency>
<dependency>
  <groupId>io.quarkus</groupId>
  <artifactId>quarkus-jdbc-postgresql</artifactId>
</dependency>
```

```properties
quarkus.flow.persistence.enabled=true
quarkus.datasource.db-kind=postgresql
# Dev Services auto-starts PostgreSQL
```

**What Happens Automatically**:
- Workflow state persists to database
- Workflows survive application restarts
- Human approval workflows can wait indefinitely
- Resume from exactly where they paused

**Enhancement Box Content**:
```adoc
💾 **Quarkus Flow Enhancement: Durable Human-in-the-Loop**

Make your human approval workflows survive restarts:

1. Add persistence:
[source,xml]
----
<dependency>
  <groupId>io.quarkiverse.flow</groupId>
  <artifactId>quarkus-flow-persistence</artifactId>
</dependency>
<dependency>
  <groupId>io.quarkus</groupId>
  <artifactId>quarkus-jdbc-postgresql</artifactId>
</dependency>
----

2. Enable in application.properties:
[source,properties]
----
quarkus.flow.persistence.enabled=true
quarkus.datasource.db-kind=postgresql
----

Now your workflows:
- ⏸️ Pause waiting for human approval (emit CloudEvent)
- 💾 Survive application restarts (state persisted)
- ▶️ Resume when approval event arrives (from Kafka)
- 🔄 Handle async approvals that take hours/days

Try it: Start a workflow → Restart Quarkus → Send approval event → Workflow resumes!

*Dev Services auto-starts PostgreSQL* - no manual database setup!
```

**Learning Outcome**: Users understand Flow's durability model and how it handles long-running workflows.

---

#### Phase 3: Production Ready - Monitoring & K8s (Steps 06-07 + Optional)

**Target Workshop Steps**: 
- Step 06-07: Advanced features
- Optional separate step: "Deploying to Kubernetes"

**Enhancement Box Location**: Final step or separate optional section

**What Users Add**:
```xml
<dependency>
  <groupId>io.quarkiverse.flow</groupId>
  <artifactId>quarkus-flow-micrometer</artifactId>
</dependency>
<dependency>
  <groupId>io.quarkus</groupId>
  <artifactId>quarkus-micrometer-registry-prometheus</artifactId>
</dependency>
```

```properties
quarkus.micrometer.enabled=true
quarkus.micrometer.export.prometheus.enabled=true
```

**For K8s deployment** (optional advanced step):
```xml
<dependency>
  <groupId>io.quarkiverse.flow</groupId>
  <artifactId>quarkus-flow-durable-kubernetes</artifactId>
</dependency>
```

**Enhancement Box Content**:
```adoc
🔍 **Quarkus Flow Enhancement: Production Observability**

Add monitoring to your workflows:

[source,xml]
----
<dependency>
  <groupId>io.quarkiverse.flow</groupId>
  <artifactId>quarkus-flow-micrometer</artifactId>
</dependency>
<dependency>
  <groupId>io.quarkus</groupId>
  <artifactId>quarkus-micrometer-registry-prometheus</artifactId>
</dependency>
----

Get automatic metrics:
- 📊 Workflow execution counts & durations
- ⚡ Task-level performance tracking
- ❌ Error rates and failure patterns
- 🎯 Custom business metrics

Metrics available at: http://localhost:8080/q/metrics

For Kubernetes deployment:
[source,xml]
----
<dependency>
  <groupId>io.quarkiverse.flow</groupId>
  <artifactId>quarkus-flow-durable-kubernetes</artifactId>
</dependency>
----

Enables:
- 🔒 Leader election for durable workflows
- 📦 Multi-pod deployment safety
- 🔄 Automatic lease management

See: examples/durable-workflows-k8s for complete K8s setup
```

**Learning Outcome**: Users see how Flow provides production-grade observability and deployment patterns.

---

## Implementation Details

### File Structure & Enhancement Placement

The workshop is hosted at `quarkus.io/quarkus-workshop-langchain4j/`. We'll contribute enhancement boxes to the existing AsciiDoc files.

**Target Files**:
```
quarkus-workshop-langchain4j/
├── section-2/
│   ├── step-01.adoc          # Add Phase 1 enhancement
│   ├── step-02.adoc          # Reinforce Phase 1 
│   ├── step-03.adoc          # Add Phase 2 enhancement
│   ├── step-04.adoc          # Reinforce Phase 2
│   ├── step-05.adoc          # Add Phase 3 enhancement
│   ├── step-06.adoc          # Add Phase 4 enhancement
│   └── step-07.adoc          # Reinforce Phase 4
└── examples/
    └── section-2-flow/       # NEW: Complete working examples
        ├── phase1-devui/
        ├── phase2-events/
        ├── phase3-hitl/
        └── phase4-production/
```

**Enhancement Box Format** (AsciiDoc):
```adoc
[NOTE]
.💡 Quarkus Flow Enhancement: <Title>
====
<Description paragraph>

<Step-by-step instructions>

<Code blocks>

<Testing instructions>

<Learning outcome>
====
```

---

### Code Examples Strategy

Each phase needs **runnable code** that users can copy-paste. We'll provide two levels:

**Level 1: Inline Snippets** (in enhancement boxes)
- Minimal code needed to add the feature
- Assumes workshop code already exists
- Copy-paste ready

**Level 2: Complete Examples** (in Quarkus Flow repo at `examples/workshop-langchain4j-integration/`)
- Full working applications
- Each phase is a standalone Quarkus app
- Includes tests, README, and configuration
- Users can clone and run independently

**Example Structure**:
```
examples/workshop-langchain4j-integration/phase2-events/
├── pom.xml                          # Self-contained, copy-pasteable
├── src/
│   ├── main/
│   │   ├── java/org/acme/
│   │   │   ├── CarCleaningWorkflow.java      # Flow wrapper
│   │   │   ├── CleaningAgent.java            # From workshop
│   │   │   ├── CarInfo.java                  # From workshop
│   │   │   └── CleaningTool.java             # From workshop
│   │   └── resources/
│   │       └── application.properties         # Kafka config
│   └── test/
│       └── java/org/acme/
│           └── CarCleaningWorkflowIT.java     # Integration test
└── README.md                         # How to run, what it demonstrates
```

---

### Testing Strategy

Each phase example must be **fully tested** to ensure workshop users don't hit broken code.

**Phase 1 Tests** (Dev UI):
```java
@QuarkusTest
class DevUIVisualizationIT {
    
    @Test
    void workflow_appears_in_devui() {
        // Verify workflow is registered
        given()
            .when().get("/q/flow/workflows")
            .then()
            .statusCode(200)
            .body("name", hasItem("car-cleaning-events"));
    }
    
    @Test
    void workflow_executes_successfully() {
        // Verify execution works
        CarInfo input = new CarInfo("ABC123", "needs cleaning");
        WorkflowInstance instance = workflow.instance(input);
        instance.start();
        
        assertThat(instance.status()).isEqualTo(COMPLETED);
    }
}
```

**Phase 2 Tests** (Events):
```java
@QuarkusTest
class EventDrivenWorkflowIT {
    
    @InjectKafkaCompanion
    KafkaCompanion companion;
    
    @Test
    void workflow_triggered_by_event() {
        // Emit car-return event
        companion.produce(String.class, CarInfo.class)
            .fromRecords(new ProducerRecord<>(
                "car-returns",
                null,
                new CarInfo("ABC123", "clean")
            ));
        
        // Wait for completion event
        await().atMost(10, SECONDS).untilAsserted(() -> {
            var records = companion.consume(String.class, String.class)
                .fromTopics("car-cleaned", 1);
            assertThat(records).hasSize(1);
        });
    }
}
```

**Phase 3 Tests** (Persistence + HITL):
```java
@QuarkusTest
class DurableHITLWorkflowIT {
    
    @Inject
    WorkflowStateRepository stateRepo;
    
    @Test
    void workflow_survives_restart() {
        // Start workflow, wait for human approval state
        WorkflowInstance instance = workflow.instance(input);
        instance.start();
        
        String instanceId = instance.id();
        await().until(() -> 
            stateRepo.find(instanceId).status() == WAITING);
        
        // Simulate restart: clear in-memory, reload from DB
        instance = workflow.reload(instanceId);
        
        // Send approval event
        instance.resume(new ApprovalEvent(APPROVED));
        
        // Workflow completes
        assertThat(instance.status()).isEqualTo(COMPLETED);
    }
}
```

**Phase 4 Tests** (Metrics):
```java
@QuarkusTest
class MetricsExportIT {
    
    @Test
    void metrics_exported() {
        // Execute workflow
        workflow.instance(input).start();
        
        // Verify Prometheus metrics
        given()
            .when().get("/q/metrics")
            .then()
            .statusCode(200)
            .body(containsString("flow_workflow_executions_total"))
            .body(containsString("flow_task_duration_seconds"));
    }
}
```

---

### Error Handling & Edge Cases

#### Common User Pitfalls & Solutions

**Issue 1: "My workflow doesn't appear in Dev UI"**

**Cause**: User added `quarkus-flow-langchain4j` but their agent isn't using agentic annotations (`@SequenceAgent`, etc.)

**Solution**: Enhancement box should clarify:
```adoc
[NOTE]
If using plain `@RegisterAiService` (not `@SequenceAgent`/`@ParallelAgent`), 
you'll need to wrap it in a Flow manually. Automatic workflow generation only 
works with LangChain4j Agentic API annotations.
```

**Issue 2: "Kafka won't start" / "Dev Services container error"**

**Cause**: Docker/Podman not running, or port conflicts

**Solution**: Enhancement box includes troubleshooting:
```adoc
**Troubleshooting Dev Services:**
- Ensure Docker or Podman is running: `docker ps`
- If port conflicts, check: `docker ps | grep kafka`
- Logs: Check console for "Dev Services started" message
- Manual override: Set `kafka.bootstrap.servers=localhost:9092` to use external Kafka
```

**Issue 3: "Workflow doesn't trigger from events"**

**Cause**: Wrong event type in `schedule(on(toOne("type")))` or topic mismatch

**Solution**: Add debugging guidance:
```adoc
**Debug event triggering:**
1. Check topics match:
   - Incoming: `mp.messaging.incoming.flow-in.topic=car-returns`
   - Schedule: `schedule(on(toOne("org.acme.car.returned")))`
2. Verify event type matches CloudEvent `type` field
3. Enable debug logging: `quarkus.log.category."io.quarkiverse.flow".level=DEBUG`
4. Check Dev UI → Flow → Instances (see if workflow created)
```

**Issue 4: "Persistence not working after restart"**

**Cause**: Database schema not created, or in-memory mode

**Solution**: Clarify database setup:
```adoc
**Verify persistence:**
1. Check Dev Services started PostgreSQL: Look for "PostgreSQL Dev Service started"
2. Verify tables created: Connect to DB and check `flow_workflow_instances` table
3. Ensure not using H2 in-memory: `quarkus.datasource.db-kind=postgresql`
4. Check workflow status in DB:
   ```sql
   SELECT id, status, definition_name FROM flow_workflow_instances;
   ```
```

**Issue 5: "Human-in-the-loop workflow never resumes"**

**Cause**: CloudEvent not matching expected type/source, or sent to wrong topic

**Solution**: Add event debugging:
```adoc
**Debug HITL resume:**
1. Check workflow is in WAITING status: Check Dev UI or DB
2. Verify event structure matches:
   ```java
   CloudEvent event = CloudEventBuilder.v1()
       .withType("org.acme.approval.done")    // Matches listen()?
       .withSource(URI.create("/approvals"))
       .withData(approvalData)
       .build();
   ```
3. Send to correct topic: `mp.messaging.incoming.flow-in.topic`
4. Check logs for "Workflow resumed" message
```

---

## Delivery & Rollout Plan

### Phased Delivery Strategy

We'll implement and contribute in **iterative phases**, each independently valuable:

**Phase 1: Event-Driven Workflows (Week 1-2)**
- ✅ Create `examples/workshop-langchain4j-integration/phase1-events/`
- ✅ Write Kafka integration tests (mocked LLM)
- ✅ Draft enhancement box for Steps 01-03
- ✅ Verify Kafka Dev Services work smoothly
- 📤 **Deliverable**: Event-driven workflow example + enhancement box text

**Phase 2: Persistence + HITL (Week 3-4)**
- ✅ Create `examples/workshop-langchain4j-integration/phase2-hitl/`
- ✅ Write durability tests (restart scenarios)
- ✅ Draft enhancement box for Step 05
- ✅ Test PostgreSQL Dev Services
- 📤 **Deliverable**: Durable HITL example + enhancement box text

**Phase 3: Production (Week 5-6)**
- ✅ Create `examples/workshop-langchain4j-integration/phase3-production/`
- ✅ Add Micrometer + K8s durable example
- ✅ Draft enhancement box for Step 06-07
- ✅ Write K8s deployment guide (optional section)
- 📤 **Deliverable**: Production-ready example + enhancement boxes

**Phase 4: Workshop Integration (Week 7)**
- 📝 Fork workshop repository
- 📝 Add enhancement boxes to AsciiDoc files
- 📝 Add screenshots and visual aids
- 🤝 Submit PR to workshop maintainers
- 📤 **Deliverable**: Complete workshop integration PR

---

### Success Metrics

**Engagement Metrics** (What we want to see):
- ✅ Java developers trying Flow enhancements in workshop
- ✅ Users completing multiple phases (not just Phase 1)
- ✅ Questions/issues on Quarkus Flow GitHub about workshop examples
- ✅ Workshop attendees asking "how does Flow compare to X?"

**Quality Metrics** (What we measure):
- ✅ 100% integration test pass rate
- ✅ Zero "this doesn't work" workshop feedback
- ✅ <5 minute setup time per phase
- ✅ Examples work on macOS, Linux, Windows

**Adoption Indicators** (Long-term):
- ✅ Workshop maintainers accept enhancement boxes
- ✅ Flow mentioned in workshop discussions/feedback
- ✅ Increased Quarkus Flow Maven downloads
- ✅ Blog posts/videos mentioning "workshop + Flow"

---

### Risk Mitigation

**Risk 1: Workshop maintainers reject enhancement boxes**

**Mitigation**:
- Keep changes minimal and non-invasive
- Provide complete working examples in Flow repo first
- Show clear value: "helps users understand production deployment"
- Offer to maintain enhancement boxes long-term
- **Fallback**: Publish as standalone "Quarkus Flow Workshop Companion" guide

**Risk 2: Workshop uses incompatible Quarkus version**

**Mitigation**:
- Check workshop's Quarkus version upfront
- Test Flow examples against that version
- Use BOM for version alignment
- **Fallback**: Note minimum Quarkus version in enhancement boxes

**Risk 3: Examples break when workshop updates**

**Mitigation**:
- Keep examples in Quarkus Flow repo (we control maintenance)
- Set up CI to test against latest workshop code
- Subscribe to workshop repo changes
- **Fallback**: Version enhancement boxes (e.g., "For workshop v2.1+")

**Risk 4: Users confused by "two ways to do things"**

**Mitigation**:
- Clear messaging: "LangChain4j = AI logic, Flow = orchestration"
- Emphasize **additive** not **replacement**
- Use consistent terminology
- **Fallback**: Add "When to use Flow vs pure LangChain4j" decision tree

---

### Next Steps

**0. 🌿 Create feature branch**
```bash
git checkout main
git pull origin main
git checkout -b feature/workshop-langchain4j-integration
```

1. ✅ **Spec self-review** (check for placeholders, contradictions)
2. ✅ **User review** (get feedback on this spec)
3. ✅ **Create implementation plan for Phase 1** (via writing-plans skill)
   - **Note**: This design covers all 4 phases for the overall vision
   - **Implementation scope**: We'll implement Phase 1 first as a complete cycle
   - Future phases will be separate implementation cycles
4. 🚀 **Implement Phase 1** (Dev UI example + enhancement box)

---

## Consequences

### Positive

- **Low Risk**: Progressive enhancement means workshop still works without Flow
- **High Value**: Demonstrates Flow's production readiness naturally
- **Reusable**: Examples become part of Quarkus Flow example portfolio
- **Marketing**: Reaches Java developers learning LangChain4j
- **Feedback Loop**: Real users trying Flow features, providing feedback

### Negative

- **Maintenance**: Need to keep examples compatible with workshop updates
- **Acceptance Risk**: Workshop maintainers might reject PR
- **Version Coupling**: Tied to workshop's Quarkus/LangChain4j versions
- **Complexity**: Users might feel overwhelmed by "too many options"

### Mitigations

- Keep examples in Quarkus Flow repo (we control)
- Fallback: publish as standalone companion guide
- Use BOMs for version management
- Clear messaging: "opt-in enhancements"

---

## Alternatives Considered

### Alternative 1: Fork Workshop Entirely
Create `quarkus-flow-workshop` from scratch.

**Rejected because**: 
- High maintenance burden
- Duplicate content
- Splits community
- Loses existing workshop's polish

### Alternative 2: Parallel Examples Only
Just create examples, no workshop integration.

**Rejected because**:
- Misses engaged audience (workshop attendees)
- No "try this now" moment
- Weaker discovery

### Alternative 3: Replace LangChain4j Code
Rewrite workshop steps to use Flow exclusively.

**Rejected because**:
- Invasive changes
- Likely to be rejected by maintainers
- Misses value proposition (Flow enhances LangChain4j, doesn't replace)

---

## References

- Quarkus LangChain4j Workshop: https://quarkus.io/quarkus-workshop-langchain4j/
- LangChain4j Agentic API: https://docs.langchain4j.dev/tutorials/agents
- Quarkus Flow Docs: https://docs.quarkiverse.io/quarkus-flow/dev/
- Quarkus Flow LangChain4j Integration: https://docs.quarkiverse.io/quarkus-flow/dev/langchain4j.html
- Newsletter Drafter Example (HITL): examples/newsletter-drafter/
- Resilient Task Orchestrator Example (Events): examples/resilient-task-orchestrator/
