package io.quarkiverse.flow.langchain4j.workflow;

public final class FlowAgentFailureResult {
    public final String agentName;
    public final String errorType;
    public final String message;
    public final transient Throwable cause;

    public FlowAgentFailureResult(String agentName, Throwable cause) {
        this.agentName = agentName;
        this.cause = cause;
        this.errorType = cause.getClass().getName();
        this.message = cause.getMessage();
    }
}
