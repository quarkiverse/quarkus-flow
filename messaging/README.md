# Quarkus Flow :: Messaging

[![Version](https://img.shields.io/maven-central/v/io.quarkiverse.flow/quarkus-flow-messaging?logo=apache-maven\&style=flat-square)](https://central.sonatype.com/artifact/io.quarkiverse.flow/quarkus-flow-messaging)

**Quarkus Flow :: Messaging** bridges the CNCF Serverless Workflow Java runtime with **MicroProfile Reactive Messaging** (SmallRye), so your workflows can **listen to** and **emit** [CloudEvents](https://cloudevents.io/) over Kafka (or any supported connector).

It is an **optional** module: bring it in if you want event-driven workflows.

---

## What you get

* 🔌 **Default channels** (`flow-in`, `flow-out`) – zero boilerplate to wire CloudEvents
* 🧱 **Connector-agnostic** – works with any SmallRye connector (Kafka, JMS, AMQP, …)
* 📦 **Structured CloudEvents JSON** end-to-end
* 🧭 **Lifecycle events (optional)** – publish engine lifecycle CloudEvents on a separate channel
* 🧩 **BYO integration** – provide your own `EventConsumer`/`EventPublisher` beans if you need full control

---

## Install

Add your messaging module alongside `quarkus-flow`:

```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-messaging-kafka</artifactId>
</dependency>
```

> This module will be included automatically on your application if `quarkus-messaging` is on classpath.

---

## Quickstart

### 1) Enable the defaults (register the bridge bean)

```properties
# Registers the default bridge bean (FlowMessagingEvents)
quarkus.flow.messaging.defaults-enabled=true
```

This registers a CDI bean that:

* **Consumes** CloudEvents from the `flow-in` channel
* **Publishes** **domain** CloudEvents (events you `emit` from the workflow) to the `flow-out` channel
* **Ignores** engine lifecycle events by default (see [Lifecycle events](#lifecycle-events) to enable)

### 2) Map the channels to your broker (Kafka example)

```properties
# Inbound CloudEvents (structured JSON)
mp.messaging.incoming.flow-in.connector=smallrye-kafka
mp.messaging.incoming.flow-in.topic=flow-in
mp.messaging.incoming.flow-in.value.deserializer=org.apache.kafka.common.serialization.ByteArrayDeserializer
mp.messaging.incoming.flow-in.key.deserializer=org.apache.kafka.common.serialization.StringDeserializer

# Outbound CloudEvents (structured JSON)
mp.messaging.outgoing.flow-out.connector=smallrye-kafka
mp.messaging.outgoing.flow-out.topic=flow-out
mp.messaging.outgoing.flow-out.value.serializer=org.apache.kafka.common.serialization.ByteArraySerializer
mp.messaging.outgoing.flow-out.key.serializer=org.apache.kafka.common.serialization.StringSerializer

# Optional if you are not using Dev Services
# kafka.bootstrap.servers=localhost:9092
```

> We send/receive **structured** CloudEvents as raw `byte[]`. Your connector sees a single JSON payload per record/message.

### 3) A tiny event-driven workflow

```java
package org.acme;

import jakarta.enterprise.context.ApplicationScoped;
import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.spec.WorkflowBuilder;
import static io.serverlessworkflow.fluent.spec.dsl.DSL.event;
import static io.serverlessworkflow.fluent.spec.dsl.DSL.to;

@ApplicationScoped
public class HelloMessagingFlow extends Flow {
  @Override
  public Workflow descriptor() {
    return WorkflowBuilder.workflow()
        .tasks(t -> t
            .listen(to().one(e -> e.type("org.acme.hello.request")))
            // events are treated as an array on the engine
            .set("{ message: \"Hello \" + .[0].name }")
            .emit(e -> e.event(event().type("org.acme.hello.response").jsonData("{ message }")))
        )
        .build();
  }
}
```

* The workflow waits for **one** CloudEvent of type `org.acme.hello.request` from `flow-in`.
* It builds a payload with JQ and **emits** `org.acme.hello.response` to `flow-out`.

> Tip: The input CloudEvent **data** must be a JSON object (not an array) if you reference fields like `.name` in `set`.

Learn more about working with events in the specification documentation:
* [DSL Reference - Listen](https://github.com/serverlessworkflow/specification/blob/main/dsl-reference.md#listen)
* [DSL Reference - Emit](https://github.com/serverlessworkflow/specification/blob/main/dsl-reference.md#emit)

---

## Lifecycle events

The runtime emits **lifecycle** CloudEvents for tracing/observability (e.g., *task started*, *task completed*, *workflow faulted*). These are separate from your **domain** events (the ones you `emit` in your workflow).

**By default, the default publisher only forwards domain events** and **ignores** lifecycle events (types prefixed with `io.serverlessworkflow.`).

If you want lifecycle events too, enable the dedicated **lifecycle publisher** and map its channel:

```properties
# Enable publishing of engine lifecycle events on a separate channel
quarkus.flow.messaging.lifecycle-enabled=true

# Map the lifecycle channel to your broker (Kafka example)
mp.messaging.outgoing.flow-lifecycle-out.connector=smallrye-kafka
mp.messaging.outgoing.flow-lifecycle-out.topic=flow-lifecycle-out
mp.messaging.outgoing.flow-lifecycle-out.key.serializer=org.apache.kafka.common.serialization.StringSerializer
mp.messaging.outgoing.flow-lifecycle-out.value.serializer=org.apache.kafka.common.serialization.ByteArraySerializer
```

Common lifecycle CloudEvent types include:

* `io.serverlessworkflow.task.started.v1`
* `io.serverlessworkflow.task.completed.v1`
* `io.serverlessworkflow.task.suspended.v1`
* `io.serverlessworkflow.task.resumed.v1`
* `io.serverlessworkflow.task.faulted.v1`
* `io.serverlessworkflow.task.cancelled.v1`
* `io.serverlessworkflow.workflow.started.v1`
* `io.serverlessworkflow.workflow.completed.v1`
* `io.serverlessworkflow.workflow.suspended.v1`
* `io.serverlessworkflow.workflow.resumed.v1`
* `io.serverlessworkflow.workflow.faulted.v1`
* `io.serverlessworkflow.workflow.cancelled.v1`

> Reference: **Serverless Workflow DSL – Lifecycle events**: [https://github.com/serverlessworkflow/specification/blob/main/dsl-reference.md#lifecycle-events](https://github.com/serverlessworkflow/specification/blob/main/dsl-reference.md#lifecycle-events)

**Engine-side opt-out** (advanced): the workflow engine can be configured to **not create** lifecycle CloudEvents at all. When exposed via configuration in `quarkus-flow`, prefer engine-side disabling if you don’t need these signals anywhere.

---

## Bring your own messaging (advanced)

You can fully replace the default bridge by providing your own beans:

* Exactly **one** `io.serverlessworkflow.impl.events.EventConsumer` (the listener)
* **Zero or more** `io.serverlessworkflow.impl.events.EventPublisher` (the emitters)

`quarkus-flow` will bind them into the `WorkflowApplication` during runtime init. If **no** consumer is present, the engine falls back to an in-memory broker for testing; if **no** publisher is present, the engine provides a minimal fallback as well.

---

## Testing with Kafka Dev Services

Quarkus can spin up a Kafka broker automatically in tests. Example using **Kafka Companion**:

```java
@QuarkusTest
@QuarkusTestResource(KafkaCompanionResource.class)
class HelloMessagingFlowIT {

  @InjectKafkaCompanion KafkaCompanion companion;
  @Inject HelloMessagingFlow flow;

  @Test
  void roundtrip() {
    // Start a waiting instance
    var inst = flow.instance(Map.of());
    inst.start();

    // Produce a CloudEvent to flow-in (structured JSON)
    byte[] ce = ... // serialize with CloudEvents JsonFormat
    companion.produceWithSerializers(StringSerializer.class, ByteArraySerializer.class)
             .fromRecords(new ProducerRecord<>("flow-in", ce));

    // Consume until our domain event arrives on flow-out
    var task = companion.consumeWithDeserializers(StringDeserializer.class, BytesDeserializer.class)
                        .fromTopics("flow-out", 1)
                        .awaitCompletion(Duration.ofMinutes(2));
    // ...assert on the CloudEvent type & data
  }
}
```

See more details on our [integration-tests](integration-tests) module.

---

## Configuration reference

**Core switch**

```properties
quarkus.flow.messaging.defaults-enabled=true   # register the default bridge bean
```

**Default channels**

```properties
# inbound (structured CE JSON)
mp.messaging.incoming.flow-in.connector=smallrye-kafka
mp.messaging.incoming.flow-in.topic=flow-in
mp.messaging.incoming.flow-in.value.deserializer=org.apache.kafka.common.serialization.ByteArrayDeserializer
mp.messaging.incoming.flow-in.key.deserializer=org.apache.kafka.common.serialization.StringDeserializer

# outbound (structured CE JSON)
mp.messaging.outgoing.flow-out.connector=smallrye-kafka
mp.messaging.outgoing.flow-out.topic=flow-out
mp.messaging.outgoing.flow-out.value.serializer=org.apache.kafka.common.serialization.ByteArraySerializer
mp.messaging.outgoing.flow-out.key.serializer=org.apache.kafka.common.serialization.StringSerializer
```

**Lifecycle channel (optional)**

```properties
quarkus.flow.messaging.lifecycle-enabled=true
mp.messaging.outgoing.flow-lifecycle-out.connector=smallrye-kafka
mp.messaging.outgoing.flow-lifecycle-out.topic=flow-lifecycle-out
mp.messaging.outgoing.flow-lifecycle-out.key.serializer=org.apache.kafka.common.serialization.StringSerializer
mp.messaging.outgoing.flow-lifecycle-out.value.serializer=org.apache.kafka.common.serialization.ByteArraySerializer
```

---

## Notes on serialization

* We use **structured** CloudEvents JSON (per `cloudevents-json-jackson`).
* Event **data** is typically JSON (`application/json`).
* Engine lifecycle payloads contain `OffsetDateTime` timestamps in RFC 3339 format.

---

## License

Apache 2.0
