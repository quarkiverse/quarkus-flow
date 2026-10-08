package io.quarkiverse.flow.opentelemetry.runtime;

public final class SpanConstants {

    public static final String WORKFLOW_CREATE_ACTION = "workflow.create";
    public static final String WORKFLOW_EXECUTE_ACTION = "workflow.execute";
    public static final String TASK_EXECUTE_ACTION = "task.execute";

    public static final String FLOW_WF_APPLICATION_ID_ATTR = "flow.application.id";
    public static final String FLOW_WF_INSTANCE_ID_ATTR = "flow.workflow.instance.id";
    public static final String FLOW_WF_NAME_ATTR = "flow.workflow.name";
    public static final String FLOW_WF_NAMESPACE_ATTR = "flow.workflow.namespace";
    public static final String FLOW_WF_VERSION_ATTR = "flow.workflow.version";
    public static final String FLOW_WF_EXECUTION_IS_RESUMPTION_ATTR = "flow.workflow.execution.is_resumption";
    public static final String FLOW_WF_EXECUTION_END_REASON_ATTR = "flow.workflow.execution.end_reason";

    public static final String FLOW_TASK_ID_ATTR = "flow.task.id";
    public static final String FLOW_TASK_TYPE_ATTR = "flow.task.type";
    public static final String FLOW_TASK_NAME_ATTR = "flow.task.name";
    public static final String FLOW_TASK_ITERATION_ATTR = "flow.task.iteration";
    public static final String FLOW_TASK_RETRYING_ATTR = "flow.task.retrying";
    public static final String FLOW_TASK_RETRY_ATTEMPT_ATTR = "flow.task.retry_attempt";
    public static final String FLOW_TASK_EXECUTION_END_REASON_ATTR = "flow.task.execution.end_reason";

    public static final String END_REASON_COMPLETED = "completed";
    public static final String END_REASON_CANCELLED = "cancelled";
    public static final String END_REASON_FAULTED = "faulted";
    public static final String END_REASON_WORKFLOW_FAULTED = "workflow_faulted";
    public static final String END_REASON_UNKNOWN = "unknown";
    public static final String END_REASON_JVM_SHUTDOWN = "jvm_shutdown";

    public static final String ERROR_TYPE_RUNTIME_JVM_SHUTDOWN_ATTR = "flow.runtime.jvm_shutdown";

    public static final String CALL_HTTP_TASK_REQUEST_METHOD_ATTR = "flow.task.call.http.request.method";
    public static final String CALL_HTTP_TASK_URL_FULL_ATTR = "flow.task.call.http.url.full";

    public static final String RUN_TASK_RUN_KIND_ATTR = "flow.task.run.kind";
    public static final String RUN_TASK_RUN_WORKFLOW_NAMESPACE_ATTR = "flow.task.run.workflow.namespace";
    public static final String RUN_TASK_RUN_WORKFLOW_NAME_ATTR = "flow.task.run.workflow.name";
    public static final String RUN_TASK_RUN_WORKFLOW_VERSION_ATTR = "flow.task.run.workflow.version";
    public static final String RUN_TASK_RUN_CONTAINER_NAME_ATTR = "flow.task.run.container.name";
    public static final String RUN_TASK_RUN_CONTAINER_IMAGE_NAME_ATTR = "flow.task.run.container.image.name";
    public static final String RUN_TASK_RUN_CONTAINER_COMMAND_ATTR = "flow.task.run.container.command";
    public static final String RUN_TASK_RUN_SCRIPT_LANGUAGE_ATTR = "flow.task.run.script.language";
    public static final String RUN_TASK_RUN_SCRIPT_CODE_ATTR = "flow.task.run.script.code";
    public static final String RUN_TASK_RUN_SCRIPT_SOURCE_NAME_ATTR = "flow.task.run.script.source.name";
    public static final String RUN_TASK_RUN_SCRIPT_SOURCE_ENDPOINT_ATTR = "flow.task.run.script.source.url.full";
    public static final String RUN_TASK_RUN_SHELL_COMMAND_ATTR = "flow.task.run.shell.command";

    public static final String CALL_TASK_GRPC_METHOD_ATTR = "flow.task.call.grpc.method";
    public static final String CALL_TASK_GRPC_SERVICE_ATTR = "flow.task.call.grpc.service";
    public static final String CALL_TASK_GRPC_SERVER_ADDRESS_ATTR = "flow.task.call.grpc.server.address";
    public static final String CALL_TASK_GRPC_SERVER_PORT_ATTR = "flow.task.call.grpc.server.port";
    public static final String CALL_TASK_OPENAPI_OPERATION_ID_ATTR = "flow.task.call.openapi.operation_id";
    public static final String CALL_TASK_OPENAPI_DOCUMENT_NAME_ATTR = "flow.task.call.openapi.document.name";
    public static final String CALL_TASK_OPENAPI_DOCUMENT_ENDPOINT_ATTR = "flow.task.call.openapi.document.url.full";

    public static final String WAIT_TASK_DURATION_LITERAL_ATTR = "flow.task.wait.duration.literal";
    public static final String WAIT_TASK_DURATION_EXPRESSION_ATTR = "flow.task.wait.duration.expression";
    public static final String WAIT_TASK_DURATION_DAYS_ATTR = "flow.task.wait.duration.days";
    public static final String WAIT_TASK_DURATION_HOURS_ATTR = "flow.task.wait.duration.hours";
    public static final String WAIT_TASK_DURATION_MINUTES_ATTR = "flow.task.wait.duration.minutes";
    public static final String WAIT_TASK_DURATION_SECONDS_ATTR = "flow.task.wait.duration.seconds";
    public static final String WAIT_TASK_DURATION_MILLISECONDS_ATTR = "flow.task.wait.duration.milliseconds";

    public static final String CALL_TASK_FUNCTION_NAME_ATTR = "flow.task.call.function.name";
    public static final String CALL_A2A_METHOD_ATTR = "flow.task.call.a2a.method";
    public static final String CALL_A2A_SERVER_ATTR = "flow.task.call.a2a.server.url.full";
    public static final String CALL_A2A_AGENT_CARD_NAME_ATTR = "flow.task.call.a2a.agent_card.name";
    public static final String CALL_A2A_AGENT_CARD_ENDPOINT_ATTR = "flow.task.call.a2a.agent_card.url.full";

    public static final String RAISE_TASK_ERROR_REFERENCE_ATTR = "flow.task.raise.error.reference";
    public static final String RAISE_TASK_ERROR_TYPE_EXPRESSION_ATTR = "flow.task.raise.error.type.expression";
    public static final String RAISE_TASK_ERROR_TYPE_URI_ATTR = "flow.task.raise.error.type.uri";
    public static final String RAISE_TASK_ERROR_STATUS_ATTR = "flow.task.raise.error.status";
    public static final String RAISE_TASK_ERROR_INSTANCE_ATTR = "flow.task.raise.error.instance";
    public static final String RAISE_TASK_ERROR_TITLE_ATTR = "flow.task.raise.error.title";
    public static final String RAISE_TASK_ERROR_DETAILS_ATTR = "flow.task.raise.error.details";

    public static final String TASK_EMIT_EVENT_ID_ATTR = "flow.task.emit.event.id";
    public static final String TASK_EMIT_EVENT_TYPE_ATTR = "flow.task.emit.event.type";
    public static final String TASK_EMIT_EVENT_SOURCE_ATTR = "flow.task.emit.event.source";
    public static final String TASK_EMIT_EVENT_SUBJECT_ATTR = "flow.task.emit.event.subject";

    private SpanConstants() {
    };
}
