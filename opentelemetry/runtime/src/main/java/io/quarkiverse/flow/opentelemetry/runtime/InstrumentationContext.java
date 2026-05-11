package io.quarkiverse.flow.opentelemetry.runtime;

import java.time.Instant;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;

public class InstrumentationContext {
    private final int iteration;
    private final int retryAttempt;
    private final boolean retrying;
    private final String jsonPosition;
    private final String containerPosition;
    private final TaskType taskType;
    private final Context parentContext;
    private final Instant startTime;
    private final Span startSpan;

    private InstrumentationContext(String jsonPosition,
            int iteration,
            boolean retrying,
            int retryAttempt,
            TaskType taskType,
            String containerPosition,
            Context parentContext, Span startSpan, Instant startTime) {
        this.jsonPosition = jsonPosition;
        this.iteration = iteration;
        this.retrying = retrying;
        this.retryAttempt = retryAttempt;
        this.taskType = taskType;
        this.containerPosition = containerPosition;
        this.parentContext = parentContext;
        this.startSpan = startSpan;
        this.startTime = startTime;
    }

    public String getJsonPosition() {
        return jsonPosition;
    }

    public int getIteration() {
        return iteration;
    }

    public int getRetryAttempt() {
        return retryAttempt;
    }

    public boolean isRetrying() {
        return retrying;
    }

    public TaskType getTaskType() {
        return taskType;
    }

    public boolean isContainerContext() {
        return containerPosition != null;
    }

    public String getContainerPosition() {
        return containerPosition;
    }

    public Context getParentContext() {
        return parentContext;
    }

    public Span getStartSpan() {
        return startSpan;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder {

        private String jsonPosition;

        private int iteration;

        private boolean retrying;

        private int retryAttempt;

        private TaskType taskType;

        private String containerPosition;

        private Span startSpan;

        private Instant startTime;

        private Context parentContext;

        private Builder() {

        }

        public Builder withJsonPosition(String jsonPosition) {
            this.jsonPosition = jsonPosition;
            return this;
        }

        public Builder withIteration(int iteration) {
            this.iteration = iteration;
            return this;
        }

        public Builder withRetrying(boolean retrying) {
            this.retrying = retrying;
            return this;
        }

        public Builder withRetryAttempt(int retryAttempt) {
            this.retryAttempt = retryAttempt;
            return this;
        }

        public Builder withTaskType(TaskType taskType) {
            this.taskType = taskType;
            return this;
        }

        public Builder withContainerPosition(String containerPosition) {
            this.containerPosition = containerPosition;
            return this;
        }

        public Builder withStartSpan(Span startSpan) {
            this.startSpan = startSpan;
            return this;
        }

        public Builder withStartTime(Instant startTime) {
            this.startTime = startTime;
            return this;
        }

        public Builder parentContext(Context startContext) {
            this.parentContext = startContext;
            return this;
        }

        public InstrumentationContext build() {
            return new InstrumentationContext(jsonPosition, iteration, retrying, retryAttempt,
                    taskType, containerPosition, parentContext, startSpan, startTime);
        }
    }
}
