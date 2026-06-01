# Workshop Step 02 Integration - Event-Driven Sequential Workflows

**Date**: 2026-05-27  
**Status**: Approved  
**Author**: Claude Code (with Ricardo Zanini)  
**Issue**: [#547](https://github.com/quarkiverse/quarkus-flow/issues/547)  
**Related ADR**: [2026-05-13-workshop-langchain4j-integration-design.md](2026-05-13-workshop-langchain4j-integration-design.md)

## Context

The Quarkus LangChain4j Workshop Step 02 teaches developers to build sequential workflows using `@SequenceAgent` to coordinate multiple AI agents. We want to demonstrate how Quarkus Flow can make these sequential workflows event-driven without modifying agent code.

This ADR focuses on **Phase 1** of the overall workshop integration strategy: adding an enhancement box to workshop Step 02 and updating the `phase1-events` example.

## Decision

Update the `phase1-events` example and create enhancement box content for workshop Step 02 that demonstrates:

1. **Zero-modification pattern**: Wrap the workshop's `@SequenceAgent` workflow in a Flow
2. **Event-driven transformation**: Trigger sequential agents from Kafka events
3. **CNCF spec compliance**: Explain event filtering with `one()` 
4. **Dev UI visualization**: Show both Flow and `@SequenceAgent` workflow representations

**Key principle**: Reuse their code, add event-driven orchestration on top.

## Goals

### Success Criteria

**For the example code:**
- ✅ Fully matches workshop Step 02 code (agents, models, packages)
- ✅ Single Flow class wraps the `@SequenceAgent` unchanged
- ✅ Works standalone (complete runnable project)
- ✅ All tests pass (unit + integration)
- ✅ Dev Services auto-start Kafka (zero manual setup)

**For the enhancement box:**
- ✅ Appears at END of Step 02 (after they build `CarProcessingWorkflow`)
- ✅ Copy-paste ready code snippets
- ✅ Explains event filtering (CNCF spec design)
- ✅ Highlights Dev UI visualization capabilities
- ✅ Takes <5 minutes to implement

**For user experience:**
- ✅ Clear value proposition: "same agents, event-driven"
- ✅ No breaking changes to workshop code
- ✅ Works on first try (Linux, macOS, Windows)

## Architecture

### Component Structure

```
phase1-events/
├── pom.xml                          
├── README.md                        
└── src/
    ├── main/
    │   ├── java/com/carmanagement/
    │   │   ├── model/
    │   │   │   ├── CarInfo.java                    # Workshop model (exact match)
    │   │   │   ├── CarConditions.java              # Workshop result (exact match)
    │   │   │   └── CarReturnEvent.java             # NEW: Event payload wrapper
    │   │   ├── agentic/
    │   │   │   ├── agents/
    │   │   │   │   ├── CleaningAgent.java              # Workshop Step 02 (exact copy)
    │   │   │   │   └── CarConditionFeedbackAgent.java  # Workshop Step 02 (exact copy)
    │   │   │   └── workflow/
    │   │   │       └── CarProcessingWorkflow.java      # Workshop @SequenceAgent (exact copy)
    │   │   ├── flow/
    │   │   │   └── CarReturnEventFlow.java         # NEW: Event-driven wrapper
    │   │   ├── service/
    │   │   │   └── CarManagementService.java       # Workshop service (if needed)
    │   │   └── rest/
    │   │       └── CarReturnResource.java          # NEW: REST endpoint to trigger events
    │   └── resources/
    │       └── application.properties               
    └── test/
        └── java/com/carmanagement/
            ├── agentic/
            │   ├── OllamaMockResource.java              # WireMock for Ollama
            │   └── workflow/
            │       └── CarProcessingWorkflowTest.java   # Unit test (mocked LLM)
            └── flow/
                └── CarReturnEventFlowIT.java            # Integration test (Kafka)
```

### Single Flow Pattern

**Workshop Code (unchanged):**
```
┌──────────────────────────────────────────────┐
│ com.carmanagement.agentic.agents             │
│  - CleaningAgent (@RegisterAiService)        │
│  - CarConditionFeedbackAgent                 │
│                                              │
│ com.carmanagement.agentic.workflow           │
│  - CarProcessingWorkflow (@SequenceAgent)    │ ← Coordinates both agents
└──────────────────────────────────────────────┘
                    ↑
                    │ Injected and called
                    │
Quarkus Flow Addition:
┌──────────────────────────────────────────────┐
│ com.carmanagement.flow                       │
│  - CarReturnEventFlow (extends Flow)         │ ← NEW: Event handler only
│     • Listens to Kafka                       │
│     • Calls @SequenceAgent                   │
│     • Emits result                           │
└──────────────────────────────────────────────┘
```

### Event Flow

```
[Kafka: car-returns topic]
         ↓
    CloudEvent (type: "org.acme.car.returned", data: CarReturnEvent)
         ↓
   one("org.acme.car.returned") ← Filters CloudEvent collection by type
         ↓
    inputFrom(extractEventData()) ← Extracts CarReturnEvent from CloudEvent.data
         ↓
    carProcessingWorkflow.processCarReturn() ← Calls @SequenceAgent
         ↓                                      (CleaningAgent → CarConditionFeedbackAgent)
    CarConditions result ← @Output combines both agent results
         ↓
    emitJson("org.acme.car.processed") ← Emits result as CloudEvent
         ↓
    [Kafka: car-processed topic]
```

## Implementation Details

### Key Components

#### 1. CarReturnEvent (NEW)

**Location**: `com.carmanagement.model.CarReturnEvent`

```java
package com.carmanagement.model;

public record CarReturnEvent(
    CarInfo carInfo,
    Integer carNumber,
    String feedback
) {}
```

**Purpose**: Structured payload for CloudEvent data field

#### 2. CarReturnEventFlow (NEW)

**Location**: `com.carmanagement.flow.CarReturnEventFlow`

```java
package com.carmanagement.flow;

import com.carmanagement.agentic.workflow.CarProcessingWorkflow;
import com.carmanagement.model.CarConditions;
import com.carmanagement.model.CarReturnEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.UncheckedIOException;
import java.util.function.Function;

import static io.serverlessworkflow.fluent.func.FuncWorkflowBuilder.workflow;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.*;

@ApplicationScoped
public class CarReturnEventFlow extends Flow {
    
    @Inject
    CarProcessingWorkflow carProcessingWorkflow;  // Workshop @SequenceAgent
    
    @Inject
    ObjectMapper objectMapper;
    
    @Override
    public Workflow descriptor() {
        return workflow("car-return-events")
            // one() filters event collection by type (CNCF Serverless Workflow spec design)
            .schedule(on(one("org.acme.car.returned")))
            .tasks(
                function("processWithAgents", 
                    (CarReturnEvent event) -> carProcessingWorkflow.processCarReturn(
                        event.carInfo(), 
                        event.carNumber(), 
                        event.feedback()
                    ))
                    .inputFrom(extractEventData()),
                
                emitJson("complete", "org.acme.car.processed", CarConditions.class)
            )
            .build();
    }
    
    private Function<JsonNode, CarReturnEvent> extractEventData() {
        return node -> {
            try {
                // CloudEvent data field contains the event payload
                JsonNode data = node.isArray() ? node.get(0).get("data") : node.get("data");
                return objectMapper.treeToValue(data, CarReturnEvent.class);
            } catch (JsonProcessingException e) {
                throw new UncheckedIOException("Failed to parse event data", e);
            }
        };
    }
}
```

**Key design points:**
- **Single responsibility**: Only handles events, delegates to `@SequenceAgent`
- **Event filtering**: `one("type")` filters CloudEvent collections (CNCF spec requirement)
- **Input mapping**: `inputFrom()` extracts and transforms event data
- **Zero agent changes**: Workshop code stays unchanged

#### 3. CarProcessingWorkflow (FROM WORKSHOP)

**Location**: `com.carmanagement.agentic.workflow.CarProcessingWorkflow`

```java
package com.carmanagement.agentic.workflow;

// Exactly as built in workshop Step 02 - zero modifications
public interface CarProcessingWorkflow {
    
    @SequenceAgent(
        outputKey = "carConditions",
        subAgents = { CleaningAgent.class, CarConditionFeedbackAgent.class })
    CarConditions processCarReturn(CarInfo carInfo, Integer carNumber, String feedback);
    
    @Output
    static CarConditions output(String carCondition, String cleaningAgentResult) {
        boolean cleaningRequired = !cleaningAgentResult.toUpperCase().contains("NOT_REQUIRED");
        return new CarConditions(carCondition, cleaningRequired);
    }
}
```

### Dependencies

**pom.xml additions:**

```xml
<dependencies>
    <!-- Quarkus Flow LangChain4j Integration -->
    <dependency>
        <groupId>io.quarkiverse.flow</groupId>
        <artifactId>quarkus-flow-langchain4j</artifactId>
    </dependency>
    
    <!-- LangChain4j Agentic API -->
    <dependency>
        <groupId>io.quarkiverse.langchain4j</groupId>
        <artifactId>quarkus-langchain4j-agentic</artifactId>
    </dependency>
    
    <!-- Ollama for LLM -->
    <dependency>
        <groupId>io.quarkiverse.langchain4j</groupId>
        <artifactId>quarkus-langchain4j-ollama</artifactId>
    </dependency>
    
    <!-- Kafka for events -->
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-messaging-kafka</artifactId>
    </dependency>
    
    <!-- REST for triggering -->
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-rest</artifactId>
    </dependency>
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-rest-jackson</artifactId>
    </dependency>
    
    <!-- Testing -->
    <dependency>
        <groupId>org.wiremock</groupId>
        <artifactId>wiremock</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.awaitility</groupId>
        <artifactId>awaitility</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

### Configuration

**application.properties:**

```properties
# Kafka Messaging
quarkus.flow.messaging.defaults-enabled=true
mp.messaging.incoming.flow-in.connector=smallrye-kafka
mp.messaging.incoming.flow-in.topic=car-returns
mp.messaging.outgoing.flow-out.connector=smallrye-kafka
mp.messaging.outgoing.flow-out.topic=car-processed

# Ollama LLM
quarkus.langchain4j.ollama.base-url=http://localhost:11434
quarkus.langchain4j.ollama.chat-model.model-id=llama3.2

# Logging
quarkus.log.category."io.quarkiverse.flow".level=INFO
```

### Why `one()` Filters a Collection

**CNCF Serverless Workflow spec design**: Event sources (`schedule()`) always receive CloudEvents as a **collection**, even for single events. This enables:
- Batch processing patterns
- Correlation of multiple events
- Flexible filtering strategies

`one("type")` filters the collection to find the single matching event type. For batch processing, you'd use `all()` or custom filters.

## Testing Strategy

### Unit Test: CarProcessingWorkflowTest

**Location**: `src/test/java/com/carmanagement/agentic/workflow/CarProcessingWorkflowTest.java`

**Purpose**: Verify `@SequenceAgent` logic with mocked LLM

```java
@QuarkusTest
class CarProcessingWorkflowTest {
    
    @Inject
    CarProcessingWorkflow workflow;
    
    @Test
    void should_coordinate_agents_sequentially() {
        CarInfo carInfo = new CarInfo("Toyota", "Camry", 2020, "Good");
        String feedback = "Car needs interior cleaning";
        
        CarConditions result = workflow.processCarReturn(carInfo, 1, feedback);
        
        assertThat(result).isNotNull();
        assertThat(result.cleaningRequired()).isTrue();
        assertThat(result.generalCondition()).isNotEmpty();
    }
}
```

### Integration Test: CarReturnEventFlowIT

**Location**: `src/test/java/com/carmanagement/flow/CarReturnEventFlowIT.java`

**Purpose**: Verify end-to-end event-driven flow

```java
@QuarkusTest
class CarReturnEventFlowIT {
    
    KafkaCompanion companion;
    
    @BeforeEach
    void setup() {
        companion = new KafkaCompanion();
    }
    
    @Test
    void should_trigger_workflow_from_kafka_event() {
        // Given: Car return event
        CarReturnEvent event = new CarReturnEvent(
            new CarInfo("Toyota", "Camry", 2020, "Good"),
            1,
            "Car needs interior cleaning"
        );
        
        // When: Emit event to car-returns topic
        companion.produce(String.class, CarReturnEvent.class)
            .fromRecords(new ProducerRecord<>("car-returns", null, event));
        
        // Then: Completion event appears on car-processed topic
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<ConsumerRecord<String, String>> records = 
                companion.consume(String.class, String.class)
                    .fromTopics("car-processed", 1)
                    .awaitCompletion()
                    .getRecords();
            
            assertThat(records).hasSize(1);
            assertThat(records.get(0).value()).contains("cleaningRequired");
        });
    }
}
```

### Test Coverage Requirements

- ✅ All unit tests pass: `mvn test`
- ✅ All integration tests pass: `mvn verify -DskipITs=false`
- ✅ Works on Linux, macOS, Windows
- ✅ No manual setup required (Dev Services handles Kafka)
- ✅ WireMock mocks Ollama responses in tests

## Enhancement Box Content

### Placement
- **Location**: End of workshop Step 02 page
- **Timing**: After users build and test `CarProcessingWorkflow`
- **Format**: AsciiDoc NOTE box

### Content

```adoc
[NOTE]
.📬 Quarkus Flow Enhancement: Event-Driven Sequential Workflows
====
You just built a sequential workflow coordinating multiple AI agents. Now make it event-driven with **Quarkus Flow** - trigger it from Kafka with zero changes to your agent code.

**1. Add dependencies to `pom.xml`:**

[source,xml]
----
<!-- Quarkus Flow LangChain4j Integration -->
<dependency>
    <groupId>io.quarkiverse.flow</groupId>
    <artifactId>quarkus-flow-langchain4j</artifactId>
</dependency>

<!-- Kafka for event-driven workflows -->
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-messaging-kafka</artifactId>
</dependency>
----

**2. Configure Kafka messaging in `application.properties`:**

[source,properties]
----
# Enable Flow messaging defaults
quarkus.flow.messaging.defaults-enabled=true

# Incoming events (car returns)
mp.messaging.incoming.flow-in.connector=smallrye-kafka
mp.messaging.incoming.flow-in.topic=car-returns

# Outgoing events (processing complete)
mp.messaging.outgoing.flow-out.connector=smallrye-kafka
mp.messaging.outgoing.flow-out.topic=car-processed
----

**3. Create an event payload wrapper in `com.carmanagement.model`:**

[source,java]
----
package com.carmanagement.model;

public record CarReturnEvent(
    CarInfo carInfo,
    Integer carNumber,
    String feedback
) {}
----

**4. Create the event-driven Flow in `com.carmanagement.flow`:**

[source,java]
----
package com.carmanagement.flow;

import com.carmanagement.agentic.workflow.CarProcessingWorkflow;
import com.carmanagement.model.CarConditions;
import com.carmanagement.model.CarReturnEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import static io.serverlessworkflow.fluent.func.FuncWorkflowBuilder.workflow;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.*;

@ApplicationScoped
public class CarReturnEventFlow extends Flow {
    
    @Inject CarProcessingWorkflow carProcessingWorkflow;
    @Inject ObjectMapper objectMapper;
    
    @Override
    public Workflow descriptor() {
        return workflow("car-return-events")
            // Listen for car-return events
            // Note: one() filters the CloudEvent collection by type
            // (CNCF spec returns events as collections)
            .schedule(on(one("org.acme.car.returned")))
            .tasks(
                // Call your @SequenceAgent workflow
                function("processWithAgents", 
                    (CarReturnEvent event) -> carProcessingWorkflow.processCarReturn(
                        event.carInfo(), 
                        event.carNumber(), 
                        event.feedback()
                    ))
                    .inputFrom(this::extractEventData),
                
                // Emit completion event
                emitJson("complete", "org.acme.car.processed", CarConditions.class)
            )
            .build();
    }
    
    private CarReturnEvent extractEventData(JsonNode node) {
        JsonNode data = node.isArray() ? node.get(0).get("data") : node.get("data");
        return objectMapper.convertValue(data, CarReturnEvent.class);
    }
}
----

**What just happened?**

✅ **Zero changes to your agents** - `CarProcessingWorkflow` stays exactly as you built it

✅ **Event filtering** - `one("org.acme.car.returned")` filters incoming CloudEvents by type (CNCF Serverless Workflow spec returns events as collections, even for single events)

✅ **Input mapping** - `inputFrom()` extracts and transforms CloudEvent data into the parameters your workflow expects

✅ **Result emission** - `emitJson()` publishes the `CarConditions` result as a CloudEvent to Kafka

**Visualize and test in Quarkus Dev UI:**

[source,bash]
----
# Start in dev mode (Dev Services auto-starts Kafka)
./mvnw quarkus:dev

# Open Dev UI and navigate to Flow
# http://localhost:8080/q/dev-ui → Flow
----

In the Flow Dev UI, you can:

🎨 **See visual representations** of both:
  - Your `CarReturnEventFlow` (event-driven wrapper)
  - Your `CarProcessingWorkflow` (the `@SequenceAgent` sequential workflow)

▶️ **Execute workflows directly** - Test your event-driven flow by providing input JSON, no need to send Kafka events manually

📊 **Inspect execution history** - See workflow instances, task execution details, and results

**Try it from the command line:**

[source,bash]
----
# Send a car return event (in another terminal)
curl -X POST http://localhost:8080/api/cars/return/1 \
  -H "Content-Type: application/json" \
  -d '{
    "carInfo": {
      "make": "Toyota",
      "model": "Camry",
      "year": 2020,
      "condition": "Good"
    },
    "carNumber": 1,
    "feedback": "Car was great but needs interior cleaning"
  }'

# Watch the logs - you'll see:
# 1. Event received
# 2. CleaningAgent analyzing
# 3. CarConditionFeedbackAgent updating condition
# 4. Completion event emitted
----

**What you get:**

🎯 **Event-driven architecture** - Workflows trigger automatically from Kafka

🤖 **Zero agent changes** - Your `@SequenceAgent` code works unchanged

📤 **Microservice choreography** - Emit events for downstream services

🔄 **Production patterns** - CloudEvents standard, event filtering, async processing

👁️ **Visual workflow inspection** - See and test both Flow and `@SequenceAgent` workflows in Dev UI

**Complete working example:** https://github.com/quarkiverse/quarkus-flow/tree/main/examples/workshop-langchain4j-integration/phase1-events

**Learn more:** https://docs.quarkiverse.io/quarkus-flow/dev/langchain4j.html
====
```

## Error Handling & Common Issues

### Issue 1: "My Flow doesn't appear in Dev UI"

**Cause**: Missing dependency or Flow not extending `Flow` class

**Solution**: 
- Verify `quarkus-flow-langchain4j` dependency is present
- Ensure class extends `io.quarkiverse.flow.Flow`
- Check class is `@ApplicationScoped`
- Restart dev mode

### Issue 2: "Kafka won't start / Dev Services error"

**Cause**: Docker/Podman not running, or port conflicts

**Solution**:
- Ensure Docker is running: `docker ps`
- Check for port conflicts: `docker ps | grep kafka`
- Look for "Dev Services started Kafka" in console
- Alternative: Use external Kafka with `quarkus.kafka.devservices.enabled=false`

### Issue 3: "Workflow doesn't trigger from events"

**Cause**: Event type mismatch or wrong topic

**Solution**:
- Verify event type: CloudEvent `type` field must be `"org.acme.car.returned"`
- Check topic config: `mp.messaging.incoming.flow-in.topic=car-returns`
- Enable debug: `quarkus.log.category."io.quarkiverse.flow".level=DEBUG`
- Check Dev UI → Flow → Instances

### Issue 4: "Agent not found / injection fails"

**Cause**: Missing `@RegisterAiService` or wrong package

**Solution**:
- Verify agents have `@RegisterAiService` annotation
- Check agents are in scanned packages: `com.carmanagement.agentic.agents`
- Ensure `quarkus-langchain4j-agentic` dependency is present

### Issue 5: "Event data extraction fails"

**Cause**: CloudEvent structure doesn't match expected format

**Solution**:
- Verify CloudEvent has `data` field with `CarReturnEvent` structure
- Check JSON serialization matches record fields
- Add logging in `extractEventData()` to debug
- Use Dev UI to inspect incoming event payload

## Success Metrics

### Example Code Quality
- ✅ Builds successfully: `mvn clean install`
- ✅ All tests pass: `mvn verify -DskipITs=false`
- ✅ Dev Services start Kafka automatically
- ✅ Works on Linux, macOS, Windows
- ✅ No manual setup required (except Docker)
- ✅ Flow appears in Dev UI with correct visualization
- ✅ Events trigger workflow execution
- ✅ Results emit to output topic

### Enhancement Box Quality
- ✅ Code is copy-paste ready
- ✅ Messaging is clear and accurate
- ✅ Explains CNCF spec event collection design
- ✅ Highlights Dev UI capabilities
- ✅ Links to complete example
- ✅ Takes <5 minutes to implement

### User Experience
- ✅ Workshop attendees can add Flow without breaking existing code
- ✅ Dev UI visualization works for both Flow and `@SequenceAgent`
- ✅ Example runs without errors on first try
- ✅ Clear value proposition: "same agents, event-driven"

## Next Steps

1. ✅ **Spec self-review** - Check for placeholders, contradictions, ambiguity
2. ✅ **User review** - Get feedback on this design
3. 🚀 **Create implementation plan** - Use writing-plans skill
4. 🚀 **Implement Phase 1** - Update phase1-events example
5. 🚀 **Test thoroughly** - Ensure all success criteria met
6. 🚀 **Submit enhancement box** - Contribute to workshop repo

## Consequences

### Positive

- **Clear value proposition**: Shows Flow's event-driven capabilities without requiring workshop code changes
- **Low friction**: Copy-paste ready, works immediately
- **Educational**: Teaches CNCF Serverless Workflow concepts (event filtering, CloudEvents)
- **Visual**: Dev UI makes workflows tangible
- **Reusable**: Example becomes part of Quarkus Flow portfolio

### Negative

- **Maintenance**: Must keep example compatible with workshop updates
- **Version coupling**: Tied to workshop's Quarkus/LangChain4j versions
- **Acceptance risk**: Workshop maintainers might reject enhancement box

### Mitigations

- Keep example in Quarkus Flow repo (we control)
- Use BOMs for version management
- Fallback: Publish as standalone companion guide
- Test against workshop code regularly

## References

- **Workshop Step 02**: https://quarkus.io/quarkus-workshop-langchain4j/section-2/step-02/
- **Overall Workshop Integration ADR**: [2026-05-13-workshop-langchain4j-integration-design.md](2026-05-13-workshop-langchain4j-integration-design.md)
- **Quarkus Flow Docs**: https://docs.quarkiverse.io/quarkus-flow/dev/
- **LangChain4j Integration**: https://docs.quarkiverse.io/quarkus-flow/dev/langchain4j.html
- **CNCF Serverless Workflow Spec**: https://serverlessworkflow.io/
