package io.quarkiverse.flow.messaging.it;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import jakarta.inject.Inject;

import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.kafka.KafkaCompanionResource;

@DisabledOnOs(OS.WINDOWS)
@QuarkusTest
@QuarkusTestResource(KafkaCompanionResource.class)
@TestProfile(FlowMessagingDefaultConfigOverrideTest.CustomTopicProfile.class)
public class FlowMessagingDefaultConfigOverrideTest {

    // Custom topic names that should override the defaults
    private static final String CUSTOM_FLOW_IN_TOPIC = "my-custom-flow-in-topic";
    private static final String CUSTOM_FLOW_OUT_TOPIC = "my-custom-flow-out-topic";
    private static final String CUSTOM_VALUE_DESERIALIZER = "org.apache.kafka.common.serialization.StringDeserializer";

    @Inject
    Config config;

    @Test
    void userDefinedTopicShouldOverrideDefault() {
        // Verify that the user-defined topic overrides the default "flow-in"
        String flowInTopic = config.getValue("mp.messaging.incoming.flow-in.topic", String.class);
        assertEquals(CUSTOM_FLOW_IN_TOPIC, flowInTopic,
                "User-defined flow-in topic should override the default");

        // Verify that the user-defined topic overrides the default "flow-out"
        String flowOutTopic = config.getValue("mp.messaging.outgoing.flow-out.topic", String.class);
        assertEquals(CUSTOM_FLOW_OUT_TOPIC, flowOutTopic,
                "User-defined flow-out topic should override the default");
    }

    @Test
    void userDefinedDeserializerShouldOverrideDefault() {
        // Verify that the user-defined deserializer overrides the default ByteArrayDeserializer
        String valueDeserializer = config.getValue("mp.messaging.incoming.flow-in.value.deserializer", String.class);
        assertEquals(CUSTOM_VALUE_DESERIALIZER, valueDeserializer,
                "User-defined value.deserializer should override the default ByteArrayDeserializer");
    }

    @Test
    void defaultConnectorShouldStillBeApplied() {
        // Verify that the default connector is still applied when not overridden
        String flowInConnector = config.getValue("mp.messaging.incoming.flow-in.connector", String.class);
        assertEquals("smallrye-kafka", flowInConnector,
                "Default connector should be applied when not overridden by user");

        String flowOutConnector = config.getValue("mp.messaging.outgoing.flow-out.connector", String.class);
        assertEquals("smallrye-kafka", flowOutConnector,
                "Default connector should be applied when not overridden by user");
    }

    @Test
    void defaultKeyDeserializerShouldStillBeApplied() {
        // Verify that the default key deserializer is still applied when not overridden
        String keyDeserializer = config.getValue("mp.messaging.incoming.flow-in.key.deserializer", String.class);
        assertEquals("org.apache.kafka.common.serialization.StringDeserializer", keyDeserializer,
                "Default key.deserializer should be applied when not overridden by user");
    }

    public static class CustomTopicProfile implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    // Override topic names
                    "mp.messaging.incoming.flow-in.topic", CUSTOM_FLOW_IN_TOPIC,
                    "mp.messaging.outgoing.flow-out.topic", CUSTOM_FLOW_OUT_TOPIC,
                    // Override value deserializer (default is ByteArrayDeserializer)
                    "mp.messaging.incoming.flow-in.value.deserializer", CUSTOM_VALUE_DESERIALIZER);
        }
    }
}
