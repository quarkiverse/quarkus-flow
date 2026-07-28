package io.quarkiverse.flow.messaging.amqp.it;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonFormat;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.smallrye.reactive.messaging.ce.OutgoingCloudEventMetadata;
import io.vertx.amqp.AmqpClient;
import io.vertx.amqp.AmqpClientOptions;
import io.vertx.amqp.AmqpMessage;
import io.vertx.amqp.AmqpReceiver;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;

@DisabledOnOs(OS.WINDOWS)
@QuarkusTest
@TestProfile(HelloMessagingFlowAmqpTest.AmqpConfigProfile.class)
public class HelloMessagingFlowAmqpTest {

    private static final JsonFormat CE_JSON = (JsonFormat) EventFormatProvider.getInstance()
            .resolveFormat(JsonFormat.CONTENT_TYPE);

    @Inject
    HelloMessagingFlow workflow;

    @Inject
    @Channel("flow-in-outgoing")
    Emitter<byte[]> flowIn;

    @ConfigProperty(name = "amqp-host")
    String amqpHost;

    @ConfigProperty(name = "amqp-port")
    int amqpPort;

    private Vertx vertx;
    private AmqpClient client;

    @BeforeEach
    void setUp() {
        workflow.instance(Map.of()).start();
        vertx = Vertx.vertx();
        client = AmqpClient.create(vertx, new AmqpClientOptions()
                .setHost(amqpHost)
                .setPort(amqpPort));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (client != null) {
            CompletableFuture<Void> close = new CompletableFuture<>();
            client.close().onComplete(ar -> close.complete(null));
            close.get(5, TimeUnit.SECONDS);
        }
        if (vertx != null) {
            CompletableFuture<Void> close = new CompletableFuture<>();
            vertx.close().onComplete(ar -> close.complete(null));
            close.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("greet_roundtrip_with_structured_cloud_event_via_amqp")
    void greet_roundtrip_structured_amqp() throws Exception {
        CompletableFuture<AmqpMessage> responseFuture = listenOnFlowOut();

        CloudEvent greet = CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withSource(URI.create("test:/it"))
                .withType("io.quarkiverse.flow.messaging.hello.request")
                .withDataContentType("application/json")
                .withData("{\"name\":\"Elisa\"}".getBytes())
                .build();

        byte[] ceBytes = CE_JSON.serialize(greet);
        sendToFlowIn(AmqpMessage.create()
                .withBufferAsBody(Buffer.buffer(ceBytes))
                .contentType("application/cloudevents+json")
                .build());

        AmqpMessage response = responseFuture.get(15, TimeUnit.SECONDS);
        assertResponseMessage(response, "Elisa");
    }

    @Test
    @DisplayName("greet_roundtrip_with_binary_cloud_event_via_amqp")
    void greet_roundtrip_binary_amqp() throws Exception {
        CompletableFuture<AmqpMessage> responseFuture = listenOnFlowOut();

        byte[] data = "{\"name\":\"Elisa\"}".getBytes();
        OutgoingCloudEventMetadata<?> ceMeta = OutgoingCloudEventMetadata.builder()
                .withId(UUID.randomUUID().toString())
                .withSource(URI.create("test:/it"))
                .withType("io.quarkiverse.flow.messaging.hello.request")
                .withDataContentType("application/json")
                .build();

        flowIn.send(Message.of(data).addMetadata(ceMeta));

        AmqpMessage response = responseFuture.get(15, TimeUnit.SECONDS);
        assertResponseMessage(response, "Elisa");
    }

    private CompletableFuture<AmqpMessage> listenOnFlowOut() {
        CompletableFuture<AmqpMessage> future = new CompletableFuture<>();
        String expectedType = "io.quarkiverse.flow.messaging.hello.response";

        CompletableFuture<Void> receiverReady = new CompletableFuture<>();

        client.createReceiver("flow-out").onComplete(ar -> {
            if (ar.failed()) {
                future.completeExceptionally(ar.cause());
                receiverReady.completeExceptionally(ar.cause());
                return;
            }
            AmqpReceiver receiver = ar.result();
            receiver.handler(msg -> {
                // Binary mode: check cloudEvents:type in application properties
                JsonObject appProps = msg.applicationProperties();
                if (appProps != null) {
                    String type = appProps.getString("cloudEvents:type");
                    if (expectedType.equals(type)) {
                        future.complete(msg);
                        return;
                    }
                }
                // Structured mode: check body for expected type
                try {
                    String body = msg.bodyAsBinary().toString(StandardCharsets.UTF_8);
                    if (body.contains(expectedType)) {
                        future.complete(msg);
                    }
                } catch (Exception ignored) {
                }
            });
            receiverReady.complete(null);
        });

        await().atMost(ofSeconds(5)).untilAsserted(() -> assertThat(receiverReady).isDone());
        return future;
    }

    private void sendToFlowIn(AmqpMessage message) {
        CompletableFuture<Void> sent = new CompletableFuture<>();
        client.createSender("flow-in").onComplete(ar -> {
            if (ar.failed()) {
                sent.completeExceptionally(ar.cause());
                return;
            }
            ar.result().send(message);
            sent.complete(null);
        });
        await().atMost(ofSeconds(5)).untilAsserted(() -> assertThat(sent).isDone());
    }

    private void assertResponseMessage(AmqpMessage msg, String name) {
        assertThat(msg).as("Response message was not received").isNotNull();
        String body = msg.bodyAsBinary().toString(StandardCharsets.UTF_8);
        assertThat(body).contains("\"Hello " + name + "!\"");
    }

    public static class AmqpConfigProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.flow.messaging.metadata.instance-id.key", "custominstanceid",
                    "quarkus.flow.messaging.metadata.task-id.key", "customtaskid",
                    "mp.messaging.outgoing.flow-in-outgoing.connector", "smallrye-amqp",
                    "mp.messaging.outgoing.flow-in-outgoing.address", "flow-in");
        }
    }
}
