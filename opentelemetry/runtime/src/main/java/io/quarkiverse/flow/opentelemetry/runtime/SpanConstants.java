package io.quarkiverse.flow.opentelemetry.runtime;

public interface SpanConstants {

    String WORKFLOW_CREATE_ACTION = "workflow.create";
    String WORKFLOW_EXECUTE_ACTION = "workflow.execute";
    String TASK_EXECUTE_ACTION = "task.execute";

    String FLOW_WF_APPLICATION_ID_ATTR = "flow.application.id";
    String FLOW_WF_INSTANCE_ID_ATTR = "flow.workflow.instance.id";
    String FLOW_WF_NAME_ATTR = "flow.workflow.name";
    String FLOW_WF_NAMESPACE_ATTR = "flow.workflow.namespace";
    String FLOW_WF_VERSION_ATTR = "flow.workflow.version";
    String FLOW_WF_EXECUTION_IS_RESUMPTION_ATTR = "flow.workflow.execution.is_resumption";
    String FLOW_WF_EXECUTION_END_REASON_ATTR = "flow.workflow.execution.end_reason";

    String FLOW_TASK_ID_ATTR = "flow.task.id";
    String FLOW_TASK_TYPE_ATTR = "flow.task.type";
    String FLOW_TASK_NAME_ATTR = "flow.task.name";
    String FLOW_TASK_ITERATION_ATTR = "flow.task.iteration";
    String FLOW_TASK_RETRYING_ATTR = "flow.task.retrying";
    String FLOW_TASK_RETRY_ATTEMPT_ATTR = "flow.task.retry_attempt";
    String FLOW_TASK_EXECUTION_END_REASON_ATTR = "flow.task.execution.end_reason";

    String END_REASON_COMPLETED = "completed";
    String END_REASON_CANCELLED = "cancelled";
    String END_REASON_FAULTED = "faulted";
    String END_REASON_UNKNOWN = "unknown";
    String END_REASON_JVM_SHUTDOWN = "jvm_shutdown";

    String ERROR_TYPE_RUNTIME_JVM_SHUTDOWN_ATTR = "flow.runtime.jvm_shutdown";

    String CALL_HTTP_TASK_REQUEST_METHOD_ATTRIBUTE = "flow.task.call.http.request.method";
    String CALL_HTTP_TASK_URL_FULL_ATTRIBUTE = "flow.task.call.http.url.full";

    String RUN_TASK_RUN_KIND_ATTR = "flow.task.run.kind";
    String RUN_TASK_RUN_WORKFLOW_NAMESPACE_ATTR = "flow.task.run.workflow.namespace";
    String RUN_TASK_RUN_WORKFLOW_NAME_ATTR = "flow.task.run.workflow.name";
    String RUN_TASK_RUN_WORKFLOW_VERSION_ATTR = "flow.task.run.workflow.version";
    String RUN_TASK_RUN_CONTAINER_NAME_ATTR = "flow.task.run.container.name";
    String RUN_TASK_RUN_CONTAINER_IMAGE_NAME_ATTR = "flow.task.run.container.image.name";
    String RUN_TASK_RUN_CONTAINER_COMMAND_ATTR = "flow.task.run.container.command";
    String RUN_TASK_RUN_SCRIPT_LANGUAGE_ATTR = "flow.task.run.script.language";
    String RUN_TASK_RUN_SCRIPT_CODE_ATTR = "flow.task.run.script.code";
    String RUN_TASK_RUN_SCRIPT_SOURCE_NAME_ATTR = "flow.task.run.script.source.name";
    String RUN_TASK_RUN_SCRIPT_SOURCE_ENDPOINT_ATTR = "flow.task.run.script.source.url.full";
    String RUN_TASK_RUN_SHELL_COMMAND_ATTR = "flow.task.run.shell.command";

    String CALL_TASK_GRPC_METHOD_ATTR = "flow.task.call.grpc.method";
    String CALL_TASK_GRPC_SERVICE_ATTR = "flow.task.call.grpc.service";
    String CALL_TASK_GRPC_SERVER_ADDRESS_ATTR = "flow.task.call.grpc.server.address";
    String CALL_TASK_GRPC_SERVER_PORT_ATTR = "flow.task.call.grpc.server.port";
    String CALL_TASK_OPENAPI_OPERATION_ID_ATTR = "flow.task.call.openapi.operation_id";
    String CALL_TASK_OPENAPI_DOCUMENT_NAME_ATTR = "flow.task.call.openapi.document.name";
    String CALL_TASK_OPENAPI_DOCUMENT_ENDPOINT_ATTR = "flow.task.call.openapi.document.url.full";

    String WAIT_TASK_DURATION_LITERAL_ATTR = "flow.task.wait.duration.literal";
    String WAIT_TASK_DURATION_EXPRESSION_ATTR = "flow.task.wait.duration.expression";
    String WAIT_TASK_DURATION_DAYS_ATTR = "flow.task.wait.duration.days";
    String WAIT_TASK_DURATION_HOURS_ATTR = "flow.task.wait.duration.hours";
    String WAIT_TASK_DURATION_MINUTES_ATTR = "flow.task.wait.duration.minutes";
    String WAIT_TASK_DURATION_SECONDS_ATTR = "flow.task.wait.duration.seconds";
    String WAIT_TASK_DURATION_MILLISECONDS_ATTR = "flow.task.wait.duration.milliseconds";

    String CALL_TASK_FUNCTION_NAME_ATTR = "flow.task.call.function.name";
    String CALL_A2A_METHOD_ATTR = "flow.task.call.a2a.method";
    String CALL_A2A_SERVER_ATTR = "flow.task.call.a2a.server.url.full";
    String CALL_A2A_AGENT_CARD_NAME_ATTR = "flow.task.call.a2a.agent_card.name";
    String CALL_A2A_AGENT_CARD_ENDPOINT_ATTR = "flow.task.call.a2a.agent_card.url.full";

    String RAISE_TASK_ERROR_REFERENCE_ATTR = "flow.task.raise.error.reference";
    String RAISE_TASK_ERROR_TYPE_EXPRESSION_ATTR = "flow.task.raise.error.type.expression";
    String RAISE_TASK_ERROR_TYPE_URI_ATTR = "flow.task.raise.error.type.uri";
    String RAISE_TASK_ERROR_STATUS_ATTR = "flow.task.raise.error.status";
    String RAISE_TASK_ERROR_INSTANCE_ATTR = "flow.task.raise.error.instance";
    String RAISE_TASK_ERROR_TITLE_ATTR = "flow.task.raise.error.title";
    String RAISE_TASK_ERROR_DETAILS_ATTR = "flow.task.raise.error.details";

    String TASK_EMIT_EVENT_ID_ATTR = "flow.task.emit.event.id";
    String TASK_EMIT_EVENT_TYPE_ATTR = "flow.task.emit.event.type";
    String TASK_EMIT_EVENT_SOURCE_ATTR = "flow.task.emit.event.source";
    String TASK_EMIT_EVENT_SUBJECT_ATTR = "flow.task.emit.event.subject";
}
