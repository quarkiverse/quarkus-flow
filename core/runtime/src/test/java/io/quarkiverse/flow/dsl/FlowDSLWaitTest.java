package io.quarkiverse.flow.dsl;

import static io.quarkiverse.flow.dsl.FlowDSL.tasks;
import static io.serverlessworkflow.fluent.spec.dsl.DSL.timeoutSeconds;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.serverlessworkflow.api.types.Workflow;

public class FlowDSLWaitTest {

    @Test
    public void when_wait_with_string_expression() {
        Workflow wf = FlowWorkflowBuilder.workflow("waitFlow").tasks(tasks(FlowDSL.wait("PT5S"))).build();

        assertEquals(1, wf.getDo().size());
        var waitTask = wf.getDo().get(0).getTask().getWaitTask();
        assertNotNull(waitTask);
        assertEquals("PT5S", waitTask.getWait().get());
    }

    @Test
    public void when_wait_with_timeout_builder() {
        Workflow wf = FlowWorkflowBuilder.workflow("waitFlow")
                .tasks(tasks(FlowDSL.wait(timeoutSeconds(10))))
                .build();

        assertEquals(1, wf.getDo().size());
        var waitTask = wf.getDo().get(0).getTask().getWaitTask();
        assertNotNull(waitTask);
        assertEquals(10, waitTask.getWait().getDurationInline().getSeconds());
    }

    @Test
    public void when_wait_named_with_string() {
        Workflow wf = FlowWorkflowBuilder.workflow("waitFlow")
                .tasks(tasks(FlowDSL.wait("pause", "PT15S")))
                .build();

        assertEquals("pause", wf.getDo().get(0).getName());
        assertEquals("PT15S", wf.getDo().get(0).getTask().getWaitTask().getWait().get());
    }

    @Test
    public void when_wait_seconds_unnamed() {
        Workflow wf = FlowWorkflowBuilder.workflow("waitFlow").tasks(tasks(FlowDSL.waitSeconds(30))).build();

        var waitTask = wf.getDo().get(0).getTask().getWaitTask();
        var inline = waitTask.getWait().getDurationInline();
        assertNotNull(inline);
        assertEquals(30, inline.getSeconds());
        assertEquals(0, inline.getMinutes());
    }

    @Test
    public void when_wait_seconds_named() {
        Workflow wf = FlowWorkflowBuilder.workflow("waitFlow")
                .tasks(tasks(FlowDSL.waitSeconds("pause", 45)))
                .build();

        assertEquals("pause", wf.getDo().get(0).getName());
        assertEquals(
                45, wf.getDo().get(0).getTask().getWaitTask().getWait().getDurationInline().getSeconds());
    }

    @Test
    public void when_wait_minutes_unnamed() {
        Workflow wf = FlowWorkflowBuilder.workflow("waitFlow").tasks(tasks(FlowDSL.waitMinutes(10))).build();

        var inline = wf.getDo().get(0).getTask().getWaitTask().getWait().getDurationInline();
        assertEquals(10, inline.getMinutes());
        assertEquals(0, inline.getSeconds());
    }

    @Test
    public void when_wait_minutes_named() {
        Workflow wf = FlowWorkflowBuilder.workflow("waitFlow")
                .tasks(tasks(FlowDSL.waitMinutes("delay", 15)))
                .build();

        assertEquals("delay", wf.getDo().get(0).getName());
        assertEquals(
                15, wf.getDo().get(0).getTask().getWaitTask().getWait().getDurationInline().getMinutes());
    }

    @Test
    public void when_wait_hours_unnamed() {
        Workflow wf = FlowWorkflowBuilder.workflow("waitFlow").tasks(tasks(FlowDSL.waitHours(2))).build();

        var inline = wf.getDo().get(0).getTask().getWaitTask().getWait().getDurationInline();
        assertEquals(2, inline.getHours());
    }

    @Test
    public void when_wait_hours_named() {
        Workflow wf = FlowWorkflowBuilder.workflow("waitFlow")
                .tasks(tasks(FlowDSL.waitHours("longPause", 3)))
                .build();

        assertEquals("longPause", wf.getDo().get(0).getName());
        assertEquals(
                3, wf.getDo().get(0).getTask().getWaitTask().getWait().getDurationInline().getHours());
    }

    @Test
    public void when_wait_days_unnamed() {
        Workflow wf = FlowWorkflowBuilder.workflow("waitFlow").tasks(tasks(FlowDSL.waitDays(1))).build();

        assertEquals(
                1, wf.getDo().get(0).getTask().getWaitTask().getWait().getDurationInline().getDays());
    }

    @Test
    public void when_wait_days_named() {
        Workflow wf = FlowWorkflowBuilder.workflow("waitFlow")
                .tasks(tasks(FlowDSL.waitDays("dailyDelay", 5)))
                .build();

        assertEquals("dailyDelay", wf.getDo().get(0).getName());
        assertEquals(
                5, wf.getDo().get(0).getTask().getWaitTask().getWait().getDurationInline().getDays());
    }

    @Test
    public void when_wait_millis_unnamed() {
        Workflow wf = FlowWorkflowBuilder.workflow("waitFlow").tasks(tasks(FlowDSL.waitMillis(500))).build();

        var inline = wf.getDo().get(0).getTask().getWaitTask().getWait().getDurationInline();
        assertEquals(500, inline.getMilliseconds());
    }

    @Test
    public void when_wait_millis_named() {
        Workflow wf = FlowWorkflowBuilder.workflow("waitFlow")
                .tasks(tasks(FlowDSL.waitMillis("shortPause", 250)))
                .build();

        assertEquals("shortPause", wf.getDo().get(0).getName());
        assertEquals(
                250,
                wf.getDo().get(0).getTask().getWaitTask().getWait().getDurationInline().getMilliseconds());
    }

    @Test
    public void when_wait_with_duration() {
        Workflow wf = FlowWorkflowBuilder.workflow("waitFlow")
                .tasks(tasks(FlowDSL.wait(Duration.ofMinutes(5).plusSeconds(30))))
                .build();

        var inline = wf.getDo().get(0).getTask().getWaitTask().getWait().getDurationInline();
        assertEquals(5, inline.getMinutes());
        assertEquals(30, inline.getSeconds());
    }

    @Test
    public void when_wait_with_duration_named() {
        Workflow wf = FlowWorkflowBuilder.workflow("waitFlow")
                .tasks(tasks(FlowDSL.wait("custom", Duration.ofHours(1))))
                .build();

        assertEquals("custom", wf.getDo().get(0).getName());
        assertEquals(
                1, wf.getDo().get(0).getTask().getWaitTask().getWait().getDurationInline().getHours());
    }
}
