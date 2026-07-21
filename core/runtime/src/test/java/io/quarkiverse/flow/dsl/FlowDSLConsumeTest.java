package io.quarkiverse.flow.dsl;

import static io.quarkiverse.flow.dsl.FlowDSL.consume;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.serverlessworkflow.api.types.Task;
import io.serverlessworkflow.api.types.TaskItem;
import io.serverlessworkflow.api.types.Workflow;

class FlowDSLConsumeTest {

    @Test
    @DisplayName("consume(name, Consumer, Class) produces CallTask and leaves output unchanged by contract")
    void consume_produces_callTask() {
        AtomicReference<String> sink = new AtomicReference<>();

        Workflow wf = FlowWorkflowBuilder.workflow("consumeStep")
                .tasks(
                        consume(
                                "sendNewsletter",
                                (String reviewed) -> sink.set("CALLED:" + reviewed),
                                String.class))
                .build();

        List<TaskItem> items = wf.getDo();
        assertEquals(1, items.size());
        Task t = items.get(0).getTask();
        assertNotNull(t.getCallTask(), "CallTask should be present for consume step");
    }
}
