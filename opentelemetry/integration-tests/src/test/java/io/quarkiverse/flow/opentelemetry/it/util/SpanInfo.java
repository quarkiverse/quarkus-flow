package io.quarkiverse.flow.opentelemetry.it.util;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SpanInfo {
    String traceId;
    String spanId;
    String parentSpanId;
    String name;
    Map<String, Object> attributes;

    public String getTraceId() {
        return traceId;
    }

    public String getSpanId() {
        return spanId;
    }

    public String getParentSpanId() {
        return parentSpanId;
    }

    public String getName() {
        return name;
    }

    public String getWorkflowId() {
        return (String) attributes.get("flow.workflow.id");
    }

    public String getWorkflowName() {
        return (String) attributes.get("flow.workflow.name");
    }

    public String getWorkflowVersion() {
        return (String) attributes.get("flow.workflow.version");
    }

    public String getTaskId() {
        return (String) attributes.get("flow.task.id");
    }

    public String getTaskName() {
        return (String) attributes.get("flow.task.name");
    }

    public String getTaskType() {
        return (String) attributes.get("flow.task.type");
    }

    public Integer getTaskIteration() {
        return (Integer) attributes.get("flow.task.iteration");
    }

    public Integer getTaskRetryAttempt() {
        return (Integer) attributes.get("flow.task.retry_attempt");
    }

    public Boolean getTaskRetrying() {
        return (Boolean) attributes.get("flow.task.retrying");
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }
}
