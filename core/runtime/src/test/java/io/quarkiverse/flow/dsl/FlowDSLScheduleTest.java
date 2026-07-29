package io.quarkiverse.flow.dsl;

import static io.quarkiverse.flow.dsl.FlowDSL.after;
import static io.quarkiverse.flow.dsl.FlowDSL.allOfType;
import static io.quarkiverse.flow.dsl.FlowDSL.cron;
import static io.quarkiverse.flow.dsl.FlowDSL.every;
import static io.quarkiverse.flow.dsl.FlowDSL.function;
import static io.quarkiverse.flow.dsl.FlowDSL.on;
import static io.quarkiverse.flow.dsl.FlowDSL.one;
import static io.quarkiverse.flow.dsl.FlowDSL.timeoutHours;
import static io.quarkiverse.flow.dsl.FlowDSL.timeoutMinutes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.serverlessworkflow.api.types.InputFrom;
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
    @DisplayName("schedule(on(one(event).first())) sets schedule and inputFrom to extract CE data")
    void schedule_on_one_first_sets_schedule_and_inputFrom() {
        Workflow wf = FlowWorkflowBuilder.workflow("schedule-first")
                .schedule(on(one("com.example.booking.confirmed").first()))
                .tasks(function("process", String::trim, String.class))
                .build();

        Schedule schedule = wf.getSchedule();
        assertNotNull(schedule, "Schedule should be configured");
        assertNotNull(schedule.getOn(), "On configuration should be set");
        assertNotNull(
                schedule.getOn().getOneEventConsumptionStrategy(),
                "One consumption strategy should be set");
        assertEquals(
                "com.example.booking.confirmed",
                schedule.getOn().getOneEventConsumptionStrategy().getOne().getWith().getType(),
                "Event type should match");

        assertNotNull(wf.getInput(), "Input should be set (from first)");
        assertNotNull(wf.getInput().getFrom(), "Input.from should be set");
        InputFrom from = wf.getInput().getFrom();
        assertNotNull(from.getObject(), "Input.from should contain a Java function");
        assertNull(from.getString(), "Input.from should not be a JQ expression");
    }

    @Test
    @DisplayName("schedule(on(one(event).envelope().first())) sets schedule and inputFrom for full CE")
    void schedule_on_one_envelope_first_sets_schedule_and_inputFrom() {
        Workflow wf = FlowWorkflowBuilder.workflow("schedule-envelope-first")
                .schedule(on(one("com.example.booking.confirmed").envelope().first()))
                .tasks(function("process", String::trim, String.class))
                .build();

        Schedule schedule = wf.getSchedule();
        assertNotNull(schedule, "Schedule should be configured");
        assertNotNull(
                schedule.getOn().getOneEventConsumptionStrategy(),
                "One consumption strategy should be set");

        assertNotNull(wf.getInput(), "Input should be set (from envelope().first())");
        assertNotNull(wf.getInput().getFrom(), "Input.from should be set");
        InputFrom from = wf.getInput().getFrom();
        assertNotNull(from.getObject(), "Input.from should contain a Java function");
    }

    @Test
    @DisplayName("schedule(on(one(event))) without first does NOT set inputFrom")
    void schedule_on_one_without_first_does_not_set_inputFrom() {
        Workflow wf = FlowWorkflowBuilder.workflow("schedule-no-first")
                .schedule(on(one("org.acme.event")))
                .tasks(function("process", String::trim, String.class))
                .build();

        Schedule schedule = wf.getSchedule();
        assertNotNull(schedule, "Schedule should be configured");
        assertNull(wf.getInput(), "Input should NOT be set when first is not called");
    }

    @Test
    @DisplayName("first inputFrom can be overridden by explicit inputFrom")
    void first_inputFrom_can_be_overridden() {
        Workflow wf = FlowWorkflowBuilder.workflow("schedule-override")
                .schedule(on(one("com.example.event").first()))
                .inputFrom(".custom_expression")
                .tasks(function("process", String::trim, String.class))
                .build();

        assertNotNull(wf.getInput(), "Input should be set");
        assertNotNull(wf.getInput().getFrom(), "Input.from should be set");
        assertEquals(
                ".custom_expression",
                wf.getInput().getFrom().getString(),
                "Explicit inputFrom should override first's inputFrom");
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
