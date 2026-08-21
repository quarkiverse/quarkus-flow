package org.acme;

import static io.quarkiverse.flow.testing.assertions.FlowAssertions.assertThat;

import io.quarkiverse.flow.testing.QuarkusFlowTest;
import io.quarkiverse.flow.testing.events.EventType;
import io.quarkus.test.junit.QuarkusTest;
import io.serverlessworkflow.impl.WorkflowInstance;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusFlowTest(scope = QuarkusFlowTest.Scope.PER_CLASS)
public class SimpleWorkflowTest {

    @Inject
    SimpleWorkflow workflow;

    @Test
    void workflow_should_finish_with_success() {
        // given
        WorkflowInstance instance = workflow.instance();

        // when
        instance.start().join();

        // then
        assertThat(instance)
                .hasEventType(EventType.WORKFLOW_COMPLETED)
                .filteredOn(recordedEvent -> recordedEvent.getType() == EventType.WORKFLOW_COMPLETED);
    }
}
