# Workshop Phase 1: Event-Driven Sequential Workflows

This example demonstrates integrating **Quarkus Flow** with **LangChain4j @SequenceAgent workflows** to create event-driven, orchestrated AI agents.

## What This Demonstrates

### Core Pattern: Zero-Modification Wrapping

```java
// Workshop Step 02 code (unchanged)
@SequenceAgent(subAgents = {CleaningAgent.class, CarConditionFeedbackAgent.class})
CarConditions processCarReturn(CarInfo carInfo, Integer carNumber, String feedback);

// Quarkus Flow wrapper (NEW)
@ApplicationScoped
public class CarReturnEventFlow extends Flow {
    @Inject CarProcessingWorkflow carProcessingWorkflow;
    
    @Override
    public Workflow descriptor() {
        return workflow("car-return-events")
            .schedule(on(one("org.acme.car.returned")))  // Event trigger
            .tasks(
                function("process", event -> 
                    carProcessingWorkflow.processCarReturn(...)  // Call @SequenceAgent
                ).inputFrom(extractEventData()),
                emitJson("complete", "org.acme.car.processed", CarConditions.class)
            )
            .build();
    }
}
```

### Key Features

- **Event-driven orchestration**: Workflows trigger automatically from Kafka events
- **Zero agent modifications**: Workshop `@SequenceAgent` code stays unchanged
- **Event filtering**: `one("type")` filters CloudEvent collections (CNCF spec design)
- **Input mapping**: `inputFrom()` transforms CloudEvents to agent parameters
- **Visual inspection**: Both Flow and `@SequenceAgent` appear in Dev UI
- **CloudEvents standard**: Production-ready event format

## Scenario

Car rental company "Miles of Smiles" processes vehicle returns using AI agents:

1. **Receive** car return event from Kafka
2. **Analyze** with CleaningAgent (determines cleaning needs)
3. **Update** with CarConditionFeedbackAgent (updates car condition)
4. **Combine** results via `@SequenceAgent` coordination
5. **Emit** completion event for downstream processing

## Prerequisites

- Java 17+
- Docker (for Kafka Dev Services)
- Ollama with llama3.2 model (or use mocked tests)

## Running the Example

### Development Mode

```bash
mvn quarkus:dev
```

Quarkus Dev Services automatically starts:
- Kafka broker
- Creates topics: `car-returns`, `car-processed`

### Access Dev UI

```
http://localhost:8080/q/dev-ui → Flow
```

**In Dev UI you can:**
- 🎨 See visual representations of both `CarReturnEventFlow` and `CarProcessingWorkflow`
- ▶️ Execute workflows directly with test input (no Kafka needed)
- 📊 Inspect workflow instances and execution history

### Send Test Event (REST)

```bash
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
```

Watch logs for:
1. Event received on `car-returns` topic
2. CleaningAgent analyzing
3. CarConditionFeedbackAgent updating condition
4. Completion event emitted to `car-processed` topic

### Send Test Event (Kafka)

```bash
# Produce CloudEvent to Kafka (if you have kafkacat/kcat)
echo '{
  "specversion": "1.0",
  "type": "org.acme.car.returned",
  "source": "/api/cars",
  "data": {
    "carInfo": {"make": "Honda", "model": "Civic", "year": 2021, "condition": "Excellent"},
    "carNumber": 2,
    "feedback": "Perfect condition"
  }
}' | kcat -P -b localhost:9092 -t car-returns
```

## Testing

**Unit tests** (mocked LLM):
```bash
mvn test
```

Tests the `@SequenceAgent` workflow with WireMock-mocked Ollama responses.

**Integration tests** (with Kafka):
```bash
mvn verify -DskipITs=false
```

Tests end-to-end: Kafka → Flow → @SequenceAgent → Kafka

## Project Structure

```
src/main/java/com/carmanagement/
├── model/
│   ├── CarInfo.java              # Workshop domain model
│   ├── CarConditions.java        # Workshop result
│   └── CarReturnEvent.java       # Event payload wrapper
├── agentic/
│   ├── agents/
│   │   ├── CleaningAgent.java            # Workshop agent
│   │   ├── CarConditionFeedbackAgent.java # Workshop agent
│   │   └── CleaningTool.java             # Workshop tool
│   └── workflow/
│       └── CarProcessingWorkflow.java     # Workshop @SequenceAgent
├── flow/
│   └── CarReturnEventFlow.java    # NEW: Event-driven wrapper
└── rest/
    └── CarReturnResource.java     # REST endpoint for testing

src/test/java/com/carmanagement/
├── agentic/
│   ├── OllamaMockResource.java            # WireMock for tests
│   └── workflow/
│       └── CarProcessingWorkflowTest.java # Unit test
└── flow/
    └── CarReturnEventFlowIT.java          # Integration test
```

## Key Dependencies

```xml
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

<!-- Kafka for event-driven workflows -->
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-messaging-kafka</artifactId>
</dependency>
```

## Configuration

**application.properties:**
```properties
# Kafka Messaging
quarkus.flow.messaging.defaults-enabled=true
mp.messaging.incoming.flow-in.connector=smallrye-kafka
mp.messaging.incoming.flow-in.topic=car-returns
mp.messaging.outgoing.flow-out.connector=smallrye-kafka
mp.messaging.outgoing.flow-out.topic=car-processed

# Ollama (for production)
quarkus.langchain4j.ollama.base-url=http://localhost:11434
quarkus.langchain4j.ollama.chat-model.model-id=llama3.2
```

## Why `one()` Filters a Collection

**CNCF Serverless Workflow spec design**: Event sources (`schedule()`) always receive CloudEvents as a **collection**, even for single events. This enables:
- Batch processing patterns
- Correlation of multiple events
- Flexible filtering strategies

`one("type")` filters the collection to find the single matching event type. For batch processing, you'd use `all()` or custom filters.

## What's Next?

See the parent directory's ADR for:
- **Phase 2**: Adding persistence and human-in-the-loop (HITL) capabilities
- **Phase 3**: Production monitoring with Micrometer and Kubernetes deployment

## Related Workshop Content

This example corresponds to the **Quarkus LangChain4j Workshop Step 02**, demonstrating how to make sequential agent workflows event-driven:

- **Workshop Step 02**: https://quarkus.io/quarkus-workshop-langchain4j/section-2/step-02/
- **Enhancement Box**: See ADR for the enhancement box content to add to the workshop

## Learn More

- [Quarkus Flow Documentation](https://docs.quarkiverse.io/quarkus-flow/dev/)
- [Quarkus Flow LangChain4j Integration](https://docs.quarkiverse.io/quarkus-flow/dev/langchain4j.html)
- [LangChain4j Agentic Workflows](https://docs.langchain4j.dev/tutorials/agents)
- [CNCF Serverless Workflow Specification](https://serverlessworkflow.io/)
