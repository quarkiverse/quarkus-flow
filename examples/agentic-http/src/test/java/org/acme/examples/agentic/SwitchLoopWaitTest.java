package org.acme.examples.agentic;

import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.testing.FluentEventAssertions;
import io.quarkiverse.flow.testing.WorkflowEventRecorder;
import io.quarkus.test.junit.QuarkusTest;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;
import io.serverlessworkflow.fluent.func.dsl.FuncDSL;
import io.serverlessworkflow.impl.WorkflowInstance;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SwitchLoopWaitTest {

    @Inject
    WorkflowEventRecorder events;

    @Inject
    TestWorkflow workflow;

    @Test
    void should_execute_all_tasks() {

        WorkflowInstance instance = workflow.instance(10);

        instance.start().join();

        FluentEventAssertions.assertThat(events.getEvents())
                .taskCompleted("inc")
                .allEventsForInstance(instance.id())
                .workflowCompleted(instance)
                .assertAll();
    }

    @ApplicationScoped
    static class TestWorkflow extends Flow {

        @Override
        public Workflow descriptor() {
            return FuncWorkflowBuilder.workflow()
                    .tasks(
                            FuncDSL.function("inc", (i) -> i + 1, Long.class))
                    .build();
        }
    }

}