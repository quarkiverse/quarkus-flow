## Quarkus Flow Enhancement: Event-Driven Sequential Workflows

You just built a sequential workflow coordinating multiple AI agents. Now make it event-driven with [**Quarkus Flow**](https://docs.quarkiverse.io/quarkus-flow/dev) LangChain4j integration - trigger it from Kafka with zero changes to your agent code.

### 1. Add dependencies to `pom.xml`:

```xml
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
```

### 2. Configure Kafka messaging in `application.properties`:

```properties
# Enable Flow messaging defaults
quarkus.flow.messaging.defaults-enabled=true

# Incoming events (car returns)
mp.messaging.incoming.flow-in.connector=smallrye-kafka
mp.messaging.incoming.flow-in.topic=car-returns

# Outgoing events (processing complete)
mp.messaging.outgoing.flow-out.connector=smallrye-kafka
mp.messaging.outgoing.flow-out.topic=car-processed
```

### 3. Create an event payload wrapper in `com.carmanagement.model`:

```java
package com.carmanagement.model;

public record CarReturnEvent(
    CarInfo carInfo,
    Integer carNumber,
    String feedback
) {}
```

### 4. Create the event-driven Flow in `com.carmanagement.flow`:

```java
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
```

### What just happened?

✅ **Zero changes to your agents** - `CarProcessingWorkflow` stays exactly as you built it

✅ **Event filtering** - `one("org.acme.car.returned")` filters incoming CloudEvents by type (CNCF Serverless Workflow spec returns events as collections, even for single events)

✅ **Input mapping** - `inputFrom()` extracts and transforms CloudEvent data into the parameters your workflow expects

✅ **Result emission** - `emitJson()` publishes the `CarConditions` result as a CloudEvent to Kafka

### Visualize and test in Quarkus Dev UI:

```bash
# Start in dev mode (Dev Services auto-starts Kafka)
./mvnw quarkus:dev

# Open Dev UI and navigate to Flow
# http://localhost:8080/q/dev-ui → Flow
```

In the Flow Dev UI, you can:

🎨 **See visual representations** of both:
- Your `CarReturnEventFlow` (event-driven wrapper)
- Your `CarProcessingWorkflow` (the `@SequenceAgent` sequential workflow)

▶️ **Execute workflows directly** - Test your event-driven flow by providing input JSON, no need to send Kafka events manually

📊 **Inspect execution history** - See workflow instances, task execution details, and results

### Try it from the command line:

```bash
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
```

### What you get:

🎯 **Event-driven architecture** - Workflows trigger automatically from Kafka

🤖 **Zero agent changes** - Your `@SequenceAgent` code works unchanged

📤 **Microservice choreography** - Emit events for downstream services

🔄 **Production patterns** - CloudEvents standard, event filtering, async processing

👁️ **Visual workflow inspection** - See and test both Flow and `@SequenceAgent` workflows in Dev UI

**Complete working example:** https://github.com/quarkiverse/quarkus-flow/tree/main/examples/workshop-langchain4j-integration/phase1-events

**Learn more:** https://docs.quarkiverse.io/quarkus-flow/dev/langchain4j.html

---