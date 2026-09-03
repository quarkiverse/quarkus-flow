package test;

// tag::should_assert_using_injected_event_store[]
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.inject.Inject;

import org.acme.HelloWorkflow;
import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.testing.QuarkusFlowTest;
import io.quarkiverse.flow.testing.WorkflowEventStore;
import io.quarkiverse.flow.testing.events.EventType;
import io.quarkiverse.flow.testing.events.RecordedEvent;
import io.quarkus.test.junit.QuarkusTest;
import io.serverlessworkflow.impl.WorkflowInstance;

@QuarkusTest
@QuarkusFlowTest
class HelloWorkflowEventStoreInjectionTest {

    @Inject
    HelloWorkflow workflow;

    @Inject
    WorkflowEventStore eventStore; // <1>

    @Test
    void should_assert_using_injected_event_store() {
        WorkflowInstance instance = workflow.instance();
        instance.start().join();

        List<RecordedEvent> events = eventStore.getByInstanceId(instance.id()); // <2>

        assertThat(events) // <3>
                .extracting(RecordedEvent::getType)
                .containsExactly(
                        EventType.WORKFLOW_STARTED,
                        EventType.TASK_STARTED,
                        EventType.TASK_COMPLETED,
                        EventType.WORKFLOW_COMPLETED);
    }
}
// end::should_assert_using_injected_event_store[]
