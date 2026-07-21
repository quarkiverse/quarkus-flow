package io.quarkiverse.flow.dsl;

import static io.quarkiverse.flow.dsl.FlowDSL.after;
import static io.quarkiverse.flow.dsl.FlowDSL.allOfType;
import static io.quarkiverse.flow.dsl.FlowDSL.cron;
import static io.quarkiverse.flow.dsl.FlowDSL.every;
import static io.quarkiverse.flow.dsl.FlowDSL.on;
import static io.quarkiverse.flow.dsl.FlowDSL.one;
import static io.quarkiverse.flow.dsl.FlowDSL.timeoutHours;
import static io.quarkiverse.flow.dsl.FlowDSL.timeoutMinutes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.serverlessworkflow.api.types.Schedule;
import io.serverlessworkflow.api.types.Workflow;

/** Tests for Schedule chaining via FlowWorkflowBuilder. */
class FlowDSLScheduleTest {

    @Test
    @DisplayName("schedule(every(expression)) configures an 'every' schedule string expression")
    void schedule_every_expression_sets_schedule() {
        Workflow wf = FlowWorkflowBuilder.workflow("schedule-every-expr").schedule(every("PT15M")).build();

        Schedule schedule = wf.getSchedule();
        assertNotNull(schedule, "Schedule should be configured");
        assertNotNull(schedule.getEvery(), "Every configuration should be set");
        assertEquals(
                "PT15M", schedule.getEvery().getDurationLiteral(), "Duration expression should match");
        assertNull(schedule.getEvery().getDurationInline(), "Inline duration should be null");
    }

    @Test
    @DisplayName("schedule(every(inline)) configures an 'every' schedule inline duration")
    void schedule_every_inline_sets_schedule() {
        Workflow wf = FlowWorkflowBuilder.workflow("schedule-every-inline")
                .schedule(every(timeoutMinutes(15)))
                .build();

        Schedule schedule = wf.getSchedule();
        assertNotNull(schedule, "Schedule should be configured");
        assertNotNull(schedule.getEvery(), "Every configuration should be set");
        assertNull(schedule.getEvery().getDurationExpression(), "Duration expression should be null");
        assertNotNull(schedule.getEvery().getDurationInline(), "Inline duration should be set");
        assertEquals(15, schedule.getEvery().getDurationInline().getMinutes(), "Minutes should match");
    }

    @Test
    @DisplayName("schedule(cron) configures a 'cron' schedule string")
    void schedule_cron_sets_schedule() {
        Workflow wf = FlowWorkflowBuilder.workflow("schedule-cron").schedule(cron("0 0 * * *")).build();

        Schedule schedule = wf.getSchedule();
        assertNotNull(schedule, "Schedule should be configured");
        assertEquals("0 0 * * *", schedule.getCron(), "Cron expression should match");
        assertNull(schedule.getEvery(), "Every configuration should be null");
    }

    @Test
    @DisplayName("schedule(after(expression)) configures an 'after' schedule string expression")
    void schedule_after_expression_sets_schedule() {
        Workflow wf = FlowWorkflowBuilder.workflow("schedule-after-expr").schedule(after("PT1H")).build();

        Schedule schedule = wf.getSchedule();
        assertNotNull(schedule, "Schedule should be configured");
        assertNotNull(schedule.getAfter(), "After configuration should be set");
        assertEquals(
                "PT1H", schedule.getAfter().getDurationLiteral(), "Duration expression should match");
        assertNull(schedule.getAfter().getDurationInline(), "Inline duration should be null");
    }

    @Test
    @DisplayName("schedule(after(inline)) configures an 'after' schedule inline duration")
    void schedule_after_inline_sets_schedule() {
        Workflow wf = FlowWorkflowBuilder.workflow("schedule-after-inline")
                .schedule(after(timeoutHours(1)))
                .build();

        Schedule schedule = wf.getSchedule();
        assertNotNull(schedule, "Schedule should be configured");
        assertNotNull(schedule.getAfter(), "After configuration should be set");
        assertNull(schedule.getAfter().getDurationExpression(), "Duration expression should be null");
        assertNotNull(schedule.getAfter().getDurationInline(), "Inline duration should be set");
        assertEquals(1, schedule.getAfter().getDurationInline().getHours(), "Hours should match");
    }

    @Test
    @DisplayName("schedule(on(one(event))) configures an 'on' schedule event consumption strategy")
    void schedule_on_event_sets_schedule() {
        Workflow wf = FlowWorkflowBuilder.workflow("schedule-on-event")
                .schedule(on(one("org.acme.startup")))
                .build();

        Schedule schedule = wf.getSchedule();
        assertNotNull(schedule, "Schedule should be configured");
        assertNotNull(schedule.getOn(), "On configuration should be set");
        assertNotNull(
                schedule.getOn().getOneEventConsumptionStrategy(),
                "One consumption strategy should be set");
        assertEquals(
                "org.acme.startup",
                schedule.getOn().getOneEventConsumptionStrategy().getOne().getWith().getType(),
                "Event type should match");
    }

    @Test
    @DisplayName("schedule(on(allOfType(event))) configures an 'on' schedule with 'all' event consumption strategy")
    void schedule_on_allOfType_event_sets_schedule() {
        Workflow wf = FlowWorkflowBuilder.workflow("schedule-on-allOfType-event")
                .schedule(on(allOfType("org.acme.processing")))
                .build();

        Schedule schedule = wf.getSchedule();
        assertNotNull(schedule, "Schedule should be configured");
        assertNotNull(schedule.getOn(), "On configuration should be set");
        assertNotNull(
                schedule.getOn().getAllEventConsumptionStrategy(),
                "All consumption strategy should be set");
        assertEquals(
                "org.acme.processing",
                schedule.getOn().getAllEventConsumptionStrategy().getAll().get(0).getWith().getType(),
                "Event type should match in the filter");
    }
}
