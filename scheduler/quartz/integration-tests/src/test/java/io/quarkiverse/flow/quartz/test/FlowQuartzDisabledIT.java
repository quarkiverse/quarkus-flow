package io.quarkiverse.flow.quartz.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.smallrye.common.annotation.Identifier;

@QuarkusTest
@TestProfile(FlowQuartzDisabledIT.DisabledSchedulerProfile.class)
public class FlowQuartzDisabledIT {

    public static class DisabledSchedulerProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.scheduler.enabled", "false");
        }
    }

    @Inject
    @Identifier("test:every-driven-schedule:0.1.0")
    WorkflowDefinition everyDefinition;

    @Inject
    @Identifier("test:cron-driven-schedule:0.1.0")
    WorkflowDefinition cronDefinition;

    @Test
    void should_create_workflow_definitions_without_scheduling() {
        assertThat(everyDefinition).isNotNull();
        assertThat(cronDefinition).isNotNull();
        assertThat(everyDefinition.scheduledInstances()).isEmpty();
        assertThat(cronDefinition.scheduledInstances()).isEmpty();
    }
}
