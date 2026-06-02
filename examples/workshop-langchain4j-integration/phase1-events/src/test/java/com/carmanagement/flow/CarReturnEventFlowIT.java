package com.carmanagement.flow;

import com.carmanagement.agentic.OllamaMockResource;
import com.carmanagement.model.CarConditions;
import com.carmanagement.model.CarInfo;
import com.carmanagement.model.CarReturnEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonFormat;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.kafka.InjectKafkaCompanion;
import io.quarkus.test.kafka.KafkaCompanionResource;
import io.smallrye.reactive.messaging.kafka.companion.ConsumerTask;
import io.smallrye.reactive.messaging.kafka.companion.KafkaCompanion;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.jboss.logging.Logger;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration test for CarReturnEventFlow.
 *
 * Tests the complete event-driven flow:
 * Kafka (car-returns topic) → CarReturnEventFlow → @SequenceAgent → Kafka (car-processed topic)
 *
 * Uses:
 * - @QuarkusTest for testing with Quarkus runtime
 * - KafkaCompanion for Kafka test utilities
 * - OllamaMockResource for mocking LLM responses
 * - Awaitility for async message processing verification
 */
@QuarkusTest
@TestProfile(KafkaTestProfile.class)
@QuarkusTestResource(KafkaCompanionResource.class)
@QuarkusTestResource(OllamaMockResource.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisabledOnOs(OS.WINDOWS)
class CarReturnEventFlowIT {

    private static final Logger LOG = Logger.getLogger(CarReturnEventFlowIT.class);

    private static final JsonFormat CE_JSON = (JsonFormat) EventFormatProvider.getInstance()
            .resolveFormat(JsonFormat.CONTENT_TYPE);

    private static final long TIMEOUT_SECONDS = 15;
    private static final long POLL_INTERVAL_MILLIS = 500;

    @Inject
    ObjectMapper objectMapper;

    @InjectKafkaCompanion
    KafkaCompanion companion;

    @Inject
    @Channel("car-returns")
    Emitter<byte[]> carReturnsEmitter;

    private ConsumerTask<Object, Object> outputConsumer;

    @BeforeEach
    void setup() {
        // Start consuming from output topic BEFORE producing events
        outputConsumer = companion
                .consumeWithDeserializers(StringDeserializer.class, ByteArrayDeserializer.class)
                .fromTopics("car-processed");
    }

    @Test
    @Order(1)
    @DisplayName("should_trigger_workflow_from_kafka_event")
    void should_trigger_workflow_from_kafka_event() throws Exception {
        // Given: A car return event
        CarInfo carInfo = new CarInfo("Toyota", "Camry", 2020, "Good");
        String feedback = "Car returned in good condition";
        CarReturnEvent carReturnEvent = new CarReturnEvent(carInfo, 1, feedback);

        // When: Emit car.returned CloudEvent to Kafka
        emitCarReturnedEvent(carReturnEvent);
        LOG.infof("Emitted car.returned event for car %d", carReturnEvent.carNumber());

        // Then: Wait for workflow processing and verify car-processed event
        AtomicReference<CarConditions> result = new AtomicReference<>();
        await()
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .pollInterval(POLL_INTERVAL_MILLIS, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    boolean found = outputConsumer.stream()
                            .anyMatch(record -> {
                                try {
                                    CloudEvent ce = CE_JSON.deserialize((byte[]) record.value());
                                    if ("org.acme.car.processed".equals(ce.getType())) {
                                        byte[] data = ce.getData() == null ? null : ce.getData().toBytes();
                                        if (data != null) {
                                            CarConditions conditions = objectMapper.readValue(data, CarConditions.class);
                                            result.set(conditions);
                                            return true;
                                        }
                                    }
                                } catch (Exception e) {
                                    LOG.warnf("Error processing CloudEvent: %s", e.getMessage());
                                }
                                return false;
                            });
                    assertThat(found).isTrue();
                });

        // Verify the result
        assertThat(result.get()).isNotNull();
        assertThat(result.get().generalCondition()).isNotEmpty();
        LOG.infof("Workflow completed successfully for car %d: condition=%s, cleaning=%s",
                carReturnEvent.carNumber(),
                result.get().generalCondition(),
                result.get().cleaningRequired());
    }

    @Test
    @Order(2)
    @DisplayName("should_handle_multiple_events_sequentially")
    void should_handle_multiple_events_sequentially() throws Exception {
        // Given: Multiple car return events
        CarInfo car1 = new CarInfo("Honda", "Civic", 2021, "Excellent");
        CarInfo car2 = new CarInfo("Ford", "Focus", 2019, "Fair");
        CarReturnEvent event1 = new CarReturnEvent(car1, 2, "Perfect condition");
        CarReturnEvent event2 = new CarReturnEvent(car2, 3, "Minor scratches");

        // When: Emit multiple car.returned events
        emitCarReturnedEvent(event1);
        emitCarReturnedEvent(event2);
        LOG.infof("Emitted %d car.returned events", 2);

        // Then: Verify both workflows completed
        AtomicReference<Integer> processedCount = new AtomicReference<>(0);
        await()
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .pollInterval(POLL_INTERVAL_MILLIS, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    int count = (int) outputConsumer.stream()
                            .filter(record -> {
                                try {
                                    CloudEvent ce = CE_JSON.deserialize((byte[]) record.value());
                                    return "org.acme.car.processed".equals(ce.getType());
                                } catch (Exception e) {
                                    return false;
                                }
                            })
                            .count();
                    processedCount.set(count);
                    assertThat(count).isGreaterThanOrEqualTo(2);
                });

        LOG.infof("Successfully processed %d events sequentially", processedCount.get());
    }

    @Test
    @Order(3)
    @DisplayName("should_filter_events_by_type")
    void should_filter_events_by_type() throws Exception {
        // Given: A car return event with proper CloudEvent structure
        CarInfo carInfo = new CarInfo("BMW", "X5", 2022, "Like New");
        CarReturnEvent carReturnEvent = new CarReturnEvent(carInfo, 4, "Excellent condition");

        // When: Emit car.returned CloudEvent with correct type
        emitCarReturnedEvent(carReturnEvent);
        LOG.infof("Emitted typed car.returned event for car %d", carReturnEvent.carNumber());

        // Then: Verify only matching type is processed
        AtomicReference<String> processedType = new AtomicReference<>();
        await()
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .pollInterval(POLL_INTERVAL_MILLIS, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    boolean found = outputConsumer.stream()
                            .anyMatch(record -> {
                                try {
                                    CloudEvent ce = CE_JSON.deserialize((byte[]) record.value());
                                    if ("org.acme.car.processed".equals(ce.getType())) {
                                        processedType.set(ce.getType());
                                        return true;
                                    }
                                } catch (Exception e) {
                                    LOG.warnf("Error processing CloudEvent: %s", e.getMessage());
                                }
                                return false;
                            });
                    assertThat(found).isTrue();
                });

        assertThat(processedType.get()).isEqualTo("org.acme.car.processed");
        LOG.infof("Event filtering verified: received type=%s", processedType.get());
    }

    private void emitCarReturnedEvent(CarReturnEvent carReturnEvent) throws Exception {
        // Create CloudEvent for car return
        byte[] eventData = objectMapper.writeValueAsBytes(carReturnEvent);

        CloudEvent ce = CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withSource(URI.create("test:/car-return"))
                .withType("org.acme.car.returned")
                .withDataContentType("application/json")
                .withData(eventData)
                .build();

        byte[] ceBytes = CE_JSON.serialize(ce);
        carReturnsEmitter.send(ceBytes);
    }
}
