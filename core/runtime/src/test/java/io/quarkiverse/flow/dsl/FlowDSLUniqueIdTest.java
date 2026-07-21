package io.quarkiverse.flow.dsl;

import static io.quarkiverse.flow.dsl.FlowDSL.agent;
import static io.quarkiverse.flow.dsl.FlowDSL.withUniqueId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.dsl.types.CallJava;
import io.quarkiverse.flow.dsl.types.FilterFunction;
import io.quarkiverse.flow.dsl.types.UniqueIdBiFunction;
import io.serverlessworkflow.api.types.CallFunction;
import io.serverlessworkflow.api.types.Task;
import io.serverlessworkflow.api.types.TaskItem;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.TaskContextData;
import io.serverlessworkflow.impl.WorkflowContextData;
import io.serverlessworkflow.impl.WorkflowInstanceData;
import io.serverlessworkflow.impl.WorkflowPosition;

/**
 * Verifies that withUniqueId/agent wrap the user's function so that, at runtime, the first argument
 * is a "unique id" composed as instanceId + "-" + jsonPointer (e.g., inst-123-/do/0/task).
 */
class FlowDSLUniqueIdTest {

    @SuppressWarnings("unchecked")
    private static FilterFunction<Object, Object> extractFilterFunction(CallFunction callJava) {
        if (callJava.getWith().getAdditionalProperties().get(CallJava.FUNCTION_NAME_KEY) instanceof FilterFunction f) {
            return f;
        }
        fail("CallTask is not a CallJavaFilterFunction; DSL contract may have changed.");
        return null; // unreachable
    }

    @Test
    @DisplayName("withUniqueId(name, fn, in) composes uniqueId = instanceId-jsonPointer and passes it")
    void withUniqueId_uses_json_pointer_for_unique_id() {
        AtomicReference<String> receivedUniqueId = new AtomicReference<>();
        AtomicReference<String> receivedPayload = new AtomicReference<>();

        UniqueIdBiFunction<String, String> fn = (uniqueId, payload) -> {
            receivedUniqueId.set(uniqueId);
            receivedPayload.set(payload);
            return payload.toUpperCase();
        };

        Workflow wf = FlowWorkflowBuilder.workflow("wf-unique-named")
                .tasks(withUniqueId("notify", fn, String.class))
                .build();

        List<TaskItem> items = wf.getDo();
        assertEquals(1, items.size(), "one task expected");
        Task t = items.get(0).getTask();
        assertNotNull(t.getCallTask(), "CallTask expected");

        CallFunction cj = (CallFunction) t.getCallTask().get();
        var jff = extractFilterFunction(cj);
        assertNotNull(jff, "JavaFilterFunction must be present for withUniqueId");

        // Mockito stubs for runtime contexts
        WorkflowInstanceData inst = mock(WorkflowInstanceData.class);
        when(inst.id()).thenReturn("inst-123");

        WorkflowContextData wctx = mock(WorkflowContextData.class);
        when(wctx.instanceData()).thenReturn(inst);

        // Use JSON Pointer for the unique component instead of task name
        final String pointer = "/do/0/task";
        WorkflowPosition pos = mock(WorkflowPosition.class);
        when(pos.jsonPointer()).thenReturn(pointer);

        TaskContextData tctx = mock(TaskContextData.class);
        when(tctx.position()).thenReturn(pos);

        Object result = jff.apply("hello", wctx, tctx);

        assertEquals(
                "inst-123-" + pointer, receivedUniqueId.get(), "uniqueId must be instanceId-jsonPointer");
        assertEquals(
                "hello", receivedPayload.get(), "payload should be forwarded to the user function");
        assertEquals("HELLO", result, "wrapped function result should be returned");
    }

    @Test
    @DisplayName("agent(fn, in) composes uniqueId = instanceId-jsonPointer and passes it")
    void agent_uses_json_pointer_for_unique_id() {
        AtomicReference<String> receivedUniqueId = new AtomicReference<>();
        AtomicReference<Integer> receivedPayload = new AtomicReference<>();

        UniqueIdBiFunction<Integer, Integer> fn = (uniqueId, payload) -> {
            receivedUniqueId.set(uniqueId);
            receivedPayload.set(payload);
            return payload + 1;
        };

        Workflow wf = FlowWorkflowBuilder.workflow("wf-agent").tasks(agent(fn, Integer.class)).build();

        List<TaskItem> items = wf.getDo();
        assertEquals(1, items.size(), "one task expected");
        Task t = items.get(0).getTask();
        assertNotNull(t.getCallTask(), "CallTask expected");

        CallFunction cj = (CallFunction) t.getCallTask().get();
        var jff = extractFilterFunction(cj);
        assertNotNull(jff, "JavaFilterFunction must be present for agent/withUniqueId");

        WorkflowInstanceData inst = mock(WorkflowInstanceData.class);
        when(inst.id()).thenReturn("wf-999");

        WorkflowContextData wctx = mock(WorkflowContextData.class);
        when(wctx.instanceData()).thenReturn(inst);

        final String pointer = "/do/0/task";
        WorkflowPosition pos = mock(WorkflowPosition.class);
        when(pos.jsonPointer()).thenReturn(pointer);

        TaskContextData tctx = mock(TaskContextData.class);
        when(tctx.position()).thenReturn(pos);

        Object result = jff.apply(41, wctx, tctx);

        assertEquals(
                "wf-999-" + pointer, receivedUniqueId.get(), "uniqueId must be instanceId-jsonPointer");
        assertEquals(41, receivedPayload.get(), "payload should be forwarded to the user function");
        assertEquals(42, result, "wrapped function result should be returned");
    }
}
