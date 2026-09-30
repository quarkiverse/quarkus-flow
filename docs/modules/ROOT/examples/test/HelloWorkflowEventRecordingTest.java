package test;

// tag::should_record_and_assert_workflow_events[]
import static io.quarkiverse.flow.testing.assertions.FlowAssertions.assertThat;

import jakarta.inject.Inject;

import org.acme.HelloWorkflow;
import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.testing.QuarkusFlowTest;
import io.quarkiverse.flow.testing.events.EventType;
import io.quarkus.test.junit.QuarkusTest;
import io.serverlessworkflow.impl.WorkflowInstance;

@QuarkusTest
@QuarkusFlowTest // <1>
class HelloWorkflowEventRecordingTest {

    @Inject
    HelloWorkflow workflow;

    @Test
    void should_record_and_assert_workflow_events() {
        WorkflowInstance instance = workflow.instance();
        instance.start().join();

        assertThat(instance) // <2>
                .hasEventTypesExactly( // <3>
                        EventType.WORKFLOW_STARTED,
                        EventType.TASK_STARTED,
                        EventType.TASK_COMPLETED,
                        EventType.WORKFLOW_COMPLETED);
    }
}
// end::should_record_and_assert_workflow_events[]
