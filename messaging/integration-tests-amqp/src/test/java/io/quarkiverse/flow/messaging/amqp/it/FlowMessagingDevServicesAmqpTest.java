package io.quarkiverse.flow.messaging.amqp.it;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@DisabledOnOs(OS.WINDOWS)
@QuarkusTest
@TestProfile(FlowMessagingDevServicesAmqpTest.DevServicesMessagingProfile.class)
public class FlowMessagingDevServicesAmqpTest {

    @Inject
    Config config;

    @Test
    void devservicesMessaging_injectsDefaultMessagingBeans() {
        assertThat(config.getOptionalValue("quarkus.flow.messaging.defaults-enabled", Boolean.class))
                .isPresent()
                .hasValue(Boolean.TRUE);

        assertThat(config.getOptionalValue("quarkus.flow.messaging.lifecycle-enabled", Boolean.class))
                .isPresent()
                .hasValue(Boolean.TRUE);
    }

    @Test
    void devservicesMessaging_injectsFlowInChannelConfig() {
        assertThat(config.getOptionalValue("mp.messaging.incoming.flow-in.connector", String.class))
                .isPresent()
                .hasValue("smallrye-amqp");

        assertThat(config.getOptionalValue("mp.messaging.incoming.flow-in.address", String.class))
                .isPresent()
                .hasValue("flow-in");
    }

    @Test
    void devservicesMessaging_injectsFlowOutChannelConfig() {
        assertThat(config.getOptionalValue("mp.messaging.outgoing.flow-out.connector", String.class))
                .isPresent()
                .hasValue("smallrye-amqp");

        assertThat(config.getOptionalValue("mp.messaging.outgoing.flow-out.address", String.class))
                .isPresent()
                .hasValue("flow-out");
    }

    @Test
    void devservicesMessaging_injectsFlowLifecycleOutChannelConfig() {
        assertThat(config.getOptionalValue("mp.messaging.outgoing.flow-lifecycle-out.connector", String.class))
                .isPresent()
                .hasValue("smallrye-amqp");

        assertThat(config.getOptionalValue("mp.messaging.outgoing.flow-lifecycle-out.address", String.class))
                .isPresent()
                .hasValue("flow-lifecycle-out");
    }

    public static class DevServicesMessagingProfile implements io.quarkus.test.junit.QuarkusTestProfile {

        @Override
        public java.util.Map<String, String> getConfigOverrides() {
            return java.util.Map.of(
                    "quarkus.flow.messaging.devservices-messaging-enabled", "true");
        }
    }
}
