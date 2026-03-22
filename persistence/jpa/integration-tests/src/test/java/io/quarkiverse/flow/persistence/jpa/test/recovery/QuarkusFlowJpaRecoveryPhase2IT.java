package io.quarkiverse.flow.persistence.jpa.test.recovery;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.serverlessworkflow.impl.events.EventPublisher;

@QuarkusTest
@TestProfile(RecoveryPhase2Profile.class)
@Order(2)
@DisabledOnOs(OS.WINDOWS)
public class QuarkusFlowJpaRecoveryPhase2IT {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Inject
    EventPublisher publisher;

    @Test
    void shouldResumeFromThirdTaskAfterRestart() {
        publishResumeEventsUntilComplete();
        RecoveryTestState.awaitWorkflowCompleted("phase2", TIMEOUT);

        List<String> started = RecoveryTestState.startedTasks("phase2");
        Assertions.assertFalse(started.contains("task1"), "task1 should not be re-executed after restart");
        Assertions.assertFalse(started.contains("task2"), "task2 should not be re-executed after restart");
        Assertions.assertTrue(started.contains("task4"), "task4 should execute after resume");
        Assertions.assertTrue(started.contains("task5"), "task5 should execute after resume");
    }

    private void publishResumeEventsUntilComplete() {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (RecoveryTestState.isWorkflowCompleted("phase2")) {
                return;
            }
            publisher.publish(buildResumeEvent()).join();
            sleep(200);
        }
    }

    private CloudEvent buildResumeEvent() {
        return CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withType(RecoveryTestConstants.RESUME_EVENT_TYPE)
                .withSource(URI.create(RecoveryTestConstants.RESUME_EVENT_SOURCE))
                .build();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
