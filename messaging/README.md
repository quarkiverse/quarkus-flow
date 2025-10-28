# Quarkus Flow :: Messaging

CloudEvents bridge for **Quarkus Flow** using **MicroProfile Reactive Messaging** (SmallRye).  
It lets workflows **listen to** and **emit** events via your chosen connector (Kafka, AMQP, JMS, …).

> 📚 Docs page: https://docs.quarkiverse.io/quarkus-flow/dev/messaging.html

## Activation

Nothing extra to add here—**the bridge activates automatically** when any `quarkus-messaging-*` connector is on the classpath.

Example (Kafka):

```xml
<dependency>
  <groupId>io.quarkus</groupId>
  <artifactId>quarkus-messaging-kafka</artifactId>
</dependency>
```

Enable the default bridge bean:

```properties
quarkus.flow.messaging.defaults-enabled=true
```

## Default channels

- **Inbound**: `flow-in`
- **Outbound**: `flow-out`
- (Optional) **Lifecycle**: `flow-lifecycle-out`

Kafka mapping:

```properties
# inbound (structured CloudEvents JSON)
mp.messaging.incoming.flow-in.connector=smallrye-kafka
mp.messaging.incoming.flow-in.topic=flow-in
mp.messaging.incoming.flow-in.value.deserializer=org.apache.kafka.common.serialization.ByteArrayDeserializer
mp.messaging.incoming.flow-in.key.deserializer=org.apache.kafka.common.serialization.StringDeserializer

# outbound (structured CloudEvents JSON)
mp.messaging.outgoing.flow-out.connector=smallrye-kafka
mp.messaging.outgoing.flow-out.topic=flow-out
mp.messaging.outgoing.flow-out.value.serializer=org.apache.kafka.common.serialization.ByteArraySerializer
mp.messaging.outgoing.flow-out.key.serializer=org.apache.kafka.common.serialization.StringSerializer
```

Lifecycle stream (optional):

```properties
quarkus.flow.messaging.lifecycle-enabled=true
mp.messaging.outgoing.flow-lifecycle-out.connector=smallrye-kafka
mp.messaging.outgoing.flow-lifecycle-out.topic=flow-lifecycle-out
mp.messaging.outgoing.flow-lifecycle-out.key.serializer=org.apache.kafka.common.serialization.StringSerializer
mp.messaging.outgoing.flow-lifecycle-out.value.serializer=org.apache.kafka.common.serialization.ByteArraySerializer
```

## Mini example

```java
@ApplicationScoped
public class HelloMessagingFlow extends Flow {
  @Override
  public Workflow descriptor() {
    return FuncWorkflowBuilder.workflow("hello-messaging").tasks(
      listen("waitHello", to().one(event("org.acme.hello.request")))
        .outputAs((java.util.Collection<Object> c) -> c.iterator().next()),
      set("{ message: \"Hello \" + .name }"),
      emitJson("org.acme.hello.response", java.util.Map.class)
    ).build();
  }
}
```

## Notes

- Uses **structured** CloudEvents JSON end-to-end.
- Bring your own `EventConsumer`/`EventPublisher` beans if you need custom wiring.
- For a deeper walkthrough, see the **Messaging** doc page linked above.
