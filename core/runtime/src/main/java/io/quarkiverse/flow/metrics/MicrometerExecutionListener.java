package io.quarkiverse.flow.metrics;

import static io.quarkiverse.flow.config.FlowMetricsConfig.DEFAULT_PREFIX;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.DoublePredicate;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkiverse.flow.config.FlowMetricsConfig;
import io.serverlessworkflow.api.types.Document;
import io.serverlessworkflow.impl.WorkflowStatus;
import io.serverlessworkflow.impl.lifecycle.TaskCompletedEvent;
import io.serverlessworkflow.impl.lifecycle.TaskEvent;
import io.serverlessworkflow.impl.lifecycle.TaskFailedEvent;
import io.serverlessworkflow.impl.lifecycle.TaskRetriedEvent;
import io.serverlessworkflow.impl.lifecycle.TaskStartedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowCancelledEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowCompletedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowExecutionListener;
import io.serverlessworkflow.impl.lifecycle.WorkflowFailedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowStartedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowStatusEvent;

public class MicrometerExecutionListener implements WorkflowExecutionListener {

    private final MeterRegistry meterRegistry;
    private final String prefix;
    private final boolean enableDurations;
    private final double[] percentiles;
    private final ConcurrentHashMap<WorkflowMetadata, WorkflowInstanceCounters> countersForGauge = new ConcurrentHashMap<>();

    public MicrometerExecutionListener(MeterRegistry meterRegistry, FlowMetricsConfig flowMetricsConfig) {
        this.meterRegistry = meterRegistry;
        this.prefix = flowMetricsConfig.prefix().orElse(DEFAULT_PREFIX);
        this.enableDurations = flowMetricsConfig.durations().enabled();
        this.percentiles = parsePercentiles(flowMetricsConfig.durations().percentiles().orElse(List.of()));
    }

    @Override
    public void onWorkflowStarted(WorkflowStartedEvent event) {
        String workflowName = workflowName(event);
        Counter.builder(FlowMetrics.WORKFLOW_STARTED_TOTAL.prefixedWith(prefix))
                .description("Workflow Started Total")
                .tag("workflow", workflowName)
                .register(meterRegistry)
                .increment();
    }

    @Override
    public void onWorkflowCompleted(WorkflowCompletedEvent event) {
        String workflowName = workflowName(event);

        // counter
        Counter.builder(FlowMetrics.WORKFLOW_COMPLETED_TOTAL.prefixedWith(prefix))
                .description("Workflow Completed Total: The workflow/task ran to completion.")
                .tag("workflow", workflowName)
                .register(meterRegistry)
                .increment();

        // timer
        Duration totalDurationInSeconds = identifyTotalDurationInSeconds(event);
        Timer.Builder builder = Timer.builder(FlowMetrics.WORKFLOW_DURATION.prefixedWith(prefix))
                .description("Workflow Duration Total In Seconds")
                .tag("workflow", workflowName);

        configurePercentiles(builder);

        builder
                .register(meterRegistry)
                .record(totalDurationInSeconds);
    }

    @Override
    public void onWorkflowFailed(WorkflowFailedEvent event) {
        String workflowName = workflowName(event);
        Counter.builder(FlowMetrics.WORKFLOW_FAULTED_TOTAL.prefixedWith(prefix))
                .description("Workflow Faulted Total: The workflow/task execution has encountered an error.")
                .tag("workflow", workflowName)
                .tag("errorType", event.workflowContext().instanceData().status().name())
                .register(meterRegistry)
                .increment();
    }

    @Override
    public void onWorkflowCancelled(WorkflowCancelledEvent event) {
        String workflowName = workflowName(event);
        Counter.builder(FlowMetrics.WORKFLOW_CANCELLED_TOTAL.prefixedWith(prefix))
                .description("Workflow Cancelled Total: The workflow/task execution has been terminated before completion.")
                .tag("workflow", workflowName)
                .register(meterRegistry)
                .increment();
    }

    @Override
    public void onWorkflowStatusChanged(WorkflowStatusEvent event) {
        WorkflowMetadata metadata = workflowMetadata(event);
        WorkflowInstanceCounters counters = countersFor(metadata);

        WorkflowStatus previous = event.previousStatus();
        WorkflowStatus current = event.status();

        decrementChangeableState(counters, previous);
        incrementChangeableState(counters, current);
    }

    @Override
    public void onTaskStarted(TaskStartedEvent event) {
        String workflowName = workflowName(event);
        Counter.builder(FlowMetrics.TASK_STARTED_TOTAL.prefixedWith(prefix))
                .description("Task Started Total")
                .tag("task", event.taskContext().taskName())
                .tag("workflow", workflowName)
                .register(meterRegistry)
                .increment();
    }

    @Override
    public void onTaskCompleted(TaskCompletedEvent event) {

        String workflowName = workflowName(event);

        // counter
        Counter.builder(FlowMetrics.TASK_COMPLETED_TOTAL.prefixedWith(prefix))
                .description("Task Execution Total")
                .tag("workflow", workflowName)
                .tag("task", event.taskContext().taskName())
                .register(meterRegistry)
                .increment();

        // timer
        Duration totalDurationInSeconds = identifyTotalDurationInSeconds(event);

        Timer.Builder builder = Timer.builder(FlowMetrics.TASK_DURATION.prefixedWith(prefix))
                .description("Task Duration In Seconds")
                .tag("workflow", workflowName)
                .tag("task", event.taskContext().taskName());

        configurePercentiles(builder);

        builder
                .register(meterRegistry)
                .record(totalDurationInSeconds);
    }

    @Override
    public void onTaskFailed(TaskFailedEvent event) {
        String workflowName = workflowName(event);
        Counter.builder(FlowMetrics.TASK_FAILED_TOTAL.prefixedWith(prefix))
                .description("Task Failed Total")
                .tag("workflow", workflowName)
                .tag("task", event.taskContext().taskName())
                .register(meterRegistry)
                .increment();
    }

    @Override
    public void onTaskRetried(TaskRetriedEvent event) {
        String workflowName = workflowName(event);
        Counter.builder(FlowMetrics.TASK_RETRIES_TOTAL.prefixedWith(prefix))
                .description("Task Retries Total")
                .tag("workflow", workflowName)
                .tag("task", event.taskContext().taskName())
                .register(meterRegistry)
                .increment();
    }

    private void incrementChangeableState(WorkflowInstanceCounters counters, WorkflowStatus status) {
        switch (status) {
            case RUNNING -> counters.running.incrementAndGet();
            case WAITING -> counters.waiting.incrementAndGet();
            case SUSPENDED -> counters.suspended.incrementAndGet();
        }
    }

    private void decrementChangeableState(WorkflowInstanceCounters counters, WorkflowStatus status) {
        switch (status) {
            case RUNNING -> safeDecrement(counters.running);
            case WAITING -> safeDecrement(counters.waiting);
            case SUSPENDED -> safeDecrement(counters.suspended);
        }
    }

    private WorkflowMetadata workflowMetadata(WorkflowEvent event) {
        Document document = event.workflowContext().definition().workflow().getDocument();
        String namespace = document.getNamespace();
        String name = document.getName();
        String version = document.getVersion();
        return new WorkflowMetadata(namespace, name, version);
    }

    private String workflowName(WorkflowEvent event) {
        return event.workflowContext().definition().workflow().getDocument().getName();
    }

    private Duration identifyTotalDurationInSeconds(TaskEvent taskEvent) {
        Instant startedAt = taskEvent.taskContext().startedAt();
        Instant completedAt = taskEvent.taskContext().completedAt();
        return Duration.between(startedAt, completedAt);
    }

    private Duration identifyTotalDurationInSeconds(WorkflowEvent workflowEvent) {
        Instant startedAt = workflowEvent.workflowContext().instanceData().startedAt();
        Instant completedAt = workflowEvent.workflowContext().instanceData().completedAt();
        return Duration.between(startedAt, completedAt);
    }

    private WorkflowInstanceCounters countersFor(WorkflowMetadata workflowMetadata) {
        return countersForGauge.computeIfAbsent(workflowMetadata, workflowName -> {
            WorkflowInstanceCounters counters = new WorkflowInstanceCounters();

            Gauge.builder(FlowMetrics.INSTANCES_RUNNING.prefixedWith(prefix), counters.running, AtomicLong::get)
                    .description("Workflow Instances Currently Running: The workflow/task is currently in progress.")
                    .tag("workflow", workflowMetadata.name())
                    .register(meterRegistry);

            Gauge.builder(FlowMetrics.INSTANCES_WAITING.prefixedWith(prefix), counters.waiting, AtomicLong::get)
                    .description("Workflow Instances Currently Waiting: The workflow/task execution is temporarily paused, " +
                            "awaiting either inbound event(s) or a specified time interval as defined by a wait task.")
                    .tag("workflow", workflowMetadata.name())
                    .register(meterRegistry);

            Gauge.builder(FlowMetrics.INSTANCES_SUSPENDED.prefixedWith(prefix), counters.suspended, AtomicLong::get)
                    .description(
                            "Workflow Instances Currently Suspended: The workflow/task execution has been manually paused " +
                                    "by a user and will remain halted until explicitly resumed.")
                    .tag("workflow", workflowName.name())
                    .register(meterRegistry);

            return counters;
        });
    }

    private void safeDecrement(AtomicLong counter) {
        counter.updateAndGet(v -> v > 0 ? v - 1 : 0);
    }

    private void configurePercentiles(Timer.Builder builder) {
        if (enableDurations) {
            if (percentiles.length > 0) {
                builder.publishPercentiles(percentiles);
            }
            builder.publishPercentileHistogram();
        }
    }

    private double[] parsePercentiles(List<String> percentiles) {
        if (percentiles == null || percentiles.isEmpty()) {
            return new double[0];
        }
        return percentiles.stream()
                .map(String::trim)
                .mapToDouble(Double::parseDouble)
                .filter(onlyValidPercentile())
                .toArray();
    }

    private DoublePredicate onlyValidPercentile() {
        return p -> p > 0.0 && p < 1.0;
    }

    private static class WorkflowInstanceCounters {
        private final AtomicLong running = new AtomicLong();
        private final AtomicLong waiting = new AtomicLong();
        private final AtomicLong suspended = new AtomicLong();
    }

    private record WorkflowMetadata(String namespace, String name, String version) {
    }
}
