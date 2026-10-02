package io.quarkiverse.flow.lifecycle.ce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.cloudevents.CloudEvent;
import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.quarkiverse.flow.lifecycle.WorkflowApplicationIds;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.events.EventPublisher;

class FlowLifeCycleCloudEventFactoryTest {

    private static final String APP_ID = "flow-lifecycle-test-app";
    private static final String LIFECYCLE_PREFIX = "io.serverlessworkflow.";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("test_workflow_and_task_lifecycle_events_include_workflow_application_id")
    void test_workflow_and_task_lifecycle_events_include_workflow_application_id() {
        CapturingPublisher publisher = new CapturingPublisher();
        Workflow workflow = FlowWorkflowBuilder.workflow("lifecycle-app-id")
                .tasks(t -> t.set("setValue", s -> s.expr(Map.of("done", true))))
                .build();

        try (WorkflowApplication app = newApplication(publisher)) {
            app.workflowDefinition(workflow).instance(Map.of()).start().join();

            List<String> expectedTypes = List.of(
                    "io.serverlessworkflow.workflow.started.v1",
                    "io.serverlessworkflow.task.started.v1",
                    "io.serverlessworkflow.task.completed.v1",
                    "io.serverlessworkflow.workflow.completed.v1");
            await().atMost(Duration.ofSeconds(5))
                    .untilAsserted(() -> assertThat(publisher.types()).containsAll(expectedTypes));

            assertAllLifecycleEventsCarryApplicationId(publisher, app.id());
        }
    }

    @Test
    @DisplayName("test_faulted_lifecycle_events_include_workflow_application_id")
    void test_faulted_lifecycle_events_include_workflow_application_id() {
        CapturingPublisher publisher = new CapturingPublisher();
        Workflow workflow = FlowWorkflowBuilder.workflow("lifecycle-app-id-faulted")
                .tasks(t -> t.raise("raiseError", r -> r.error(
                        e -> e.type(URI.create("http://example.com/error")).status(500))))
                .build();

        try (WorkflowApplication app = newApplication(publisher)) {
            assertThatThrownBy(() -> app.workflowDefinition(workflow).instance(Map.of()).start().join());

            List<String> expectedTypes = List.of(
                    "io.serverlessworkflow.workflow.faulted.v1",
                    "io.serverlessworkflow.task.faulted.v1");
            await().atMost(Duration.ofSeconds(5))
                    .untilAsserted(() -> assertThat(publisher.types()).containsAll(expectedTypes));

            assertAllLifecycleEventsCarryApplicationId(publisher, app.id());
        }
    }

    @Test
    @DisplayName("test_lifecycle_payload_keeps_engine_fields")
    void test_lifecycle_payload_keeps_engine_fields() {
        CapturingPublisher publisher = new CapturingPublisher();
        Workflow workflow = FlowWorkflowBuilder.workflow("lifecycle-app-id-fields")
                .tasks(t -> t.set("setValue", s -> s.expr(Map.of("done", true))))
                .build();

        try (WorkflowApplication app = newApplication(publisher)) {
            app.workflowDefinition(workflow).instance(Map.of()).start().join();

            await().atMost(Duration.ofSeconds(5)).untilAsserted(
                    () -> assertThat(publisher.types()).contains("io.serverlessworkflow.task.started.v1"));

            Map<String, Object> workflowStarted = publisher.data("io.serverlessworkflow.workflow.started.v1");
            assertThat(workflowStarted)
                    .containsEntry(WorkflowApplicationIds.FIELD_NAME, APP_ID)
                    .containsKeys("name", "definition", "startedAt");

            Map<String, Object> taskStarted = publisher.data("io.serverlessworkflow.task.started.v1");
            assertThat(taskStarted)
                    .containsEntry(WorkflowApplicationIds.FIELD_NAME, APP_ID)
                    .containsKeys("workflow", "task", "definition", "startedAt");
        }
    }

    private static WorkflowApplication newApplication(EventPublisher publisher) {
        return WorkflowApplication.builder()
                .withId(APP_ID)
                .withLifeCycleCloudEventFactory(new FlowLifeCycleCloudEventFactory())
                .withEventPublisher(publisher)
                .build();
    }

    private static void assertAllLifecycleEventsCarryApplicationId(CapturingPublisher publisher, String appId) {
        assertThat(appId).isEqualTo(APP_ID);
        assertThat(publisher.events)
                .allSatisfy(ce -> assertThat(CapturingPublisher.toMap(ce))
                        .as("data of %s", ce.getType())
                        .containsEntry(WorkflowApplicationIds.FIELD_NAME, appId));
    }

    private static final class CapturingPublisher implements EventPublisher {

        private final List<CloudEvent> events = new CopyOnWriteArrayList<>();

        @Override
        public CompletableFuture<Void> publish(CloudEvent event) {
            if (event.getType() != null && event.getType().startsWith(LIFECYCLE_PREFIX)) {
                events.add(event);
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void close() {
        }

        List<String> types() {
            return events.stream().map(CloudEvent::getType).toList();
        }

        Map<String, Object> data(String type) {
            return events.stream().filter(ce -> type.equals(ce.getType())).findFirst()
                    .map(CapturingPublisher::toMap)
                    .orElseThrow(() -> new AssertionError("No event of type " + type));
        }

        static Map<String, Object> toMap(CloudEvent ce) {
            try {
                return MAPPER.readValue(ce.getData().toBytes(), new TypeReference<>() {
                });
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
