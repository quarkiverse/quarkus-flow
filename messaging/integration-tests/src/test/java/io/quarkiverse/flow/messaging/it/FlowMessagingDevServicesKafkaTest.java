package io.quarkiverse.flow.messaging.it;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.kafka.KafkaCompanionResource;

@DisabledOnOs(OS.WINDOWS)
@QuarkusTest
@QuarkusTestResource(KafkaCompanionResource.class)
@TestProfile(FlowMessagingDevServicesKafkaTest.DevServicesKafkaProfile.class)
public class FlowMessagingDevServicesKafkaTest {

    @Inject
    Config config;

    @Test
    void devservicesKafka_injectsDefaultMessagingBeans() {
        assertThat(config.getOptionalValue("quarkus.flow.messaging.defaults-enabled", Boolean.class))
                .isPresent()
                .hasValue(Boolean.TRUE);

        assertThat(config.getOptionalValue("quarkus.flow.messaging.lifecycle-enabled", Boolean.class))
                .isPresent()
                .hasValue(Boolean.TRUE);
    }

    @Test
    void devservicesKafka_injectsFlowInChannelConfig() {
        assertThat(config.getOptionalValue("mp.messaging.incoming.flow-in.connector", String.class))
                .isPresent()
                .hasValue("smallrye-kafka");

        assertThat(config.getOptionalValue("mp.messaging.incoming.flow-in.topic", String.class))
                .isPresent()
                .hasValue("flow-in");

        assertThat(config.getOptionalValue("mp.messaging.incoming.flow-in.key.deserializer", String.class))
                .isPresent()
                .hasValue("org.apache.kafka.common.serialization.StringDeserializer");

        assertThat(config.getOptionalValue("mp.messaging.incoming.flow-in.value.deserializer", String.class))
                .isPresent()
                .hasValue("org.apache.kafka.common.serialization.ByteArrayDeserializer");
    }

    @Test
    void devservicesKafka_injectsFlowOutChannelConfig() {
        assertThat(config.getOptionalValue("mp.messaging.outgoing.flow-out.connector", String.class))
                .isPresent()
                .hasValue("smallrye-kafka");

        assertThat(config.getOptionalValue("mp.messaging.outgoing.flow-out.topic", String.class))
                .isPresent()
                .hasValue("flow-out");

        assertThat(config.getOptionalValue("mp.messaging.outgoing.flow-out.key.serializer", String.class))
                .isPresent()
                .hasValue("org.apache.kafka.common.serialization.StringSerializer");

        assertThat(config.getOptionalValue("mp.messaging.outgoing.flow-out.value.serializer", String.class))
                .isPresent()
                .hasValue("org.apache.kafka.common.serialization.ByteArraySerializer");
    }

    @Test
    void devservicesKafka_injectsFlowLifecycleOutChannelConfig() {
        assertThat(config.getOptionalValue("mp.messaging.outgoing.flow-lifecycle-out.connector", String.class))
                .isPresent()
                .hasValue("smallrye-kafka");

        assertThat(config.getOptionalValue("mp.messaging.outgoing.flow-lifecycle-out.topic", String.class))
                .isPresent()
                .hasValue("flow-lifecycle-out");

        assertThat(config.getOptionalValue("mp.messaging.outgoing.flow-lifecycle-out.key.serializer", String.class))
                .isPresent()
                .hasValue("org.apache.kafka.common.serialization.StringSerializer");

        assertThat(config.getOptionalValue("mp.messaging.outgoing.flow-lifecycle-out.value.serializer", String.class))
                .isPresent()
                .hasValue("org.apache.kafka.common.serialization.ByteArraySerializer");
    }

    @Test
    void devservicesKafka_allowsUserOverride() {
        String flowInTopic = config.getValue("mp.messaging.incoming.flow-in.topic", String.class);
        assertThat(flowInTopic).isEqualTo("flow-in");
    }

    public static class DevServicesKafkaProfile implements io.quarkus.test.junit.QuarkusTestProfile {

        @Override
        public java.util.Map<String, String> getConfigOverrides() {
            return java.util.Map.of(
                    "quarkus.flow.messaging.devservices-kafka-enabled", "true");
        }
    }
}
