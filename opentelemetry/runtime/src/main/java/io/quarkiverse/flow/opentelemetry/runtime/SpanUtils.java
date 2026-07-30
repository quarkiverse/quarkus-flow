package io.quarkiverse.flow.opentelemetry.runtime;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Function;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.serverlessworkflow.api.types.A2AArguments;
import io.serverlessworkflow.api.types.CallA2A;
import io.serverlessworkflow.api.types.CallFunction;
import io.serverlessworkflow.api.types.CallGRPC;
import io.serverlessworkflow.api.types.CallHTTP;
import io.serverlessworkflow.api.types.CallOpenAPI;
import io.serverlessworkflow.api.types.Container;
import io.serverlessworkflow.api.types.DurationInline;
import io.serverlessworkflow.api.types.EmitTask;
import io.serverlessworkflow.api.types.Endpoint;
import io.serverlessworkflow.api.types.Error;
import io.serverlessworkflow.api.types.ErrorDetails;
import io.serverlessworkflow.api.types.ErrorInstance;
import io.serverlessworkflow.api.types.ErrorTitle;
import io.serverlessworkflow.api.types.EventProperties;
import io.serverlessworkflow.api.types.EventSource;
import io.serverlessworkflow.api.types.ExternalResource;
import io.serverlessworkflow.api.types.GRPCArguments;
import io.serverlessworkflow.api.types.HTTPArguments;
import io.serverlessworkflow.api.types.OpenAPIArguments;
import io.serverlessworkflow.api.types.RaiseTask;
import io.serverlessworkflow.api.types.RaiseTaskError;
import io.serverlessworkflow.api.types.RunScript;
import io.serverlessworkflow.api.types.RunShell;
import io.serverlessworkflow.api.types.RunTask;
import io.serverlessworkflow.api.types.Script;
import io.serverlessworkflow.api.types.SubflowConfiguration;
import io.serverlessworkflow.api.types.TaskBase;
import io.serverlessworkflow.api.types.TimeoutAfter;
import io.serverlessworkflow.api.types.UriTemplate;
import io.serverlessworkflow.api.types.WaitTask;
import io.serverlessworkflow.api.types.WithGRPCService;

public class SpanUtils {
    public static final String CALL_A2A_AGENT_CARD_ENDPOINT_ATTRIBUTE = "flow.task.call.a2a.agent_card.url.full";
    private static final String WORKFLOW_EXECUTE_ACTION = "workflow.execute";
    private static final String TASK_EXECUTE_ACTION = "task.execute";

    private static final String CALL_HTTP_TASK_REQUEST_METHOD_ATTRIBUTE = "flow.task.call.http.request.method";
    private static final String CALL_HTTP_TASK_URL_FULL_ATTRIBUTE = "flow.task.call.http.url.full";
    private static final String RUN_TASK_RUN_KIND_ATTRIBUTE = "flow.task.run.kind";
    private static final String RUN_TASK_RUN_WORKFLOW_NAMESPACE_ATTRIBUTE = "flow.task.run.workflow.namespace";
    private static final String RUN_TASK_RUN_WORKFLOW_NAME_ATTRIBUTE = "flow.task.run.workflow.name";
    private static final String RUN_TASK_RUN_WORKFLOW_VERSION_ATTRIBUTE = "flow.task.run.workflow.version";
    private static final String RUN_TASK_RUN_CONTAINER_NAME_ATTRIBUTE = "flow.task.run.container.name";
    private static final String RUN_TASK_RUN_CONTAINER_IMAGE_NAME_ATTRIBUTE = "flow.task.run.container.image.name";
    private static final String RUN_TASK_RUN_CONTAINER_COMMAND_ATTRIBUTE = "flow.task.run.container.command";
    private static final String RUN_TASK_RUN_SCRIPT_LANGUAGE_ATTRIBUTE = "flow.task.run.script.language";
    private static final String RUN_TASK_RUN_SCRIPT_CODE_ATTRIBUTE = "flow.task.run.script.code";
    private static final String RUN_TASK_RUN_SCRIPT_SOURCE_NAME_ATTRIBUTE = "flow.task.run.script.source.name";
    private static final String RUN_TASK_RUN_SCRIPT_SOURCE_ENDPOINT_ATTRIBUTE = "flow.task.run.script.source.url.full";
    private static final String RUN_TASK_RUN_SHELL_COMMAND_ATTRIBUTE = "flow.task.run.shell.command";
    public static final String CALL_TASK_GRPC_METHOD_ATTRIBUTE = "flow.task.call.grpc.method";
    public static final String CALL_TASK_GRPC_SERVICE_ATTRIBUTE = "flow.task.call.grpc.service";
    public static final String CALL_TASK_GRPC_SERVER_ADDRESS_ATTRIBUTE = "flow.task.call.grpc.server.address";
    public static final String CALL_TASK_GRPC_SERVER_PORT_ATTRIBUTE = "flow.task.call.grpc.server.port";
    public static final String CALL_TASK_OPENAPI_OPERATION_ID_ATTRIBUTE = "flow.task.call.openapi.operation_id";
    public static final String CALL_TASK_OPENAPI_DOCUMENT_NAME_ATTRIBUTE = "flow.task.call.openapi.document.name";
    public static final String CALL_TASK_OPENAPI_DOCUMENT_ENDPOINT_ATTRIBUTE = "flow.task.call.openapi.document.url.full";
    public static final String WAIT_TASK_DURATION_LITERAL_ATTRIBUTE = "flow.task.wait.duration.literal";
    public static final String WAIT_TASK_DURATION_EXPRESSION_ATTRIBUTE = "flow.task.wait.duration.expression";
    public static final String WAIT_TASK_DURATION_DAYS_ATTRIBUTE = "flow.task.wait.duration.days";
    public static final String WAIT_TASK_DURATION_HOURS_ATTRIBUTE = "flow.task.wait.duration.hours";
    public static final String WAIT_TASK_DURATION_MINUTES_ATTRIBUTE = "flow.task.wait.duration.minutes";
    public static final String WAIT_TASK_DURATION_SECONDS_ATTRIBUTE = "flow.task.wait.duration.seconds";
    public static final String WAIT_TASK_DURATION_MILLISECONDS_ATTRIBUTE = "flow.task.wait.duration.milliseconds";
    public static final String CALL_TASK_FUNCTION_NAME_ATTRIBUTE = "flow.task.call.function.name";
    public static final String CALL_A2A_METHOD_ATTRIBUTE = "flow.task.call.a2a.method";
    public static final String CALL_A2A_SERVER_ATTRIBUTE = "flow.task.call.a2a.server.url.full";
    public static final String CALL_A2A_AGENT_CARD_NAME_ATTRIBUTE = "flow.task.call.a2a.agent_card.name";
    public static final String RAISE_TASK_ERROR_REFERENCE_ATTRIBUTE = "flow.task.raise.error.reference";
    public static final String RAISE_TASK_ERROR_TYPE_EXPRESSION_ATTRIBUTE = "flow.task.raise.error.type.expression";
    public static final String RAISE_TASK_ERROR_TYPE_URI_ATTRIBUTE = "flow.task.raise.error.type.uri";
    public static final String RAISE_TASK_ERROR_STATUS_ATTRIBUTE = "flow.task.raise.error.status";
    public static final String RAISE_TASK_ERROR_INSTANCE_ATTRIBUTE = "flow.task.raise.error.instance";
    public static final String RAISE_TASK_ERROR_TITLE_ATTRIBUTE = "flow.task.raise.error.title";
    public static final String RAISE_TASK_ERROR_DETAILS_ATTRIBUTE = "flow.task.raise.error.details";
    public static final String TASK_EMIT_EVENT_ID_ATTRIBUTE = "flow.task.emit.event.id";
    public static final String TASK_EMIT_EVENT_TYPE_ATTRIBUTE = "flow.task.emit.event.type";
    public static final String TASK_EMIT_EVENT_SOURCE_ATTRIBUTE = "flow.task.emit.event.source";
    public static final String TASK_EMIT_EVENT_SUBJECT_ATTRIBUTE = "flow.task.emit.event.subject";

    public enum TaskNameStrategy {
        ACTION_AND_TASK_NAME,
        ACTION_AND_TASK_ID,
        DEBUG
    }

    public static void appendWorkflowEvent(Span span, WorkflowEventType eventType) {
        span.addEvent(eventType.id());
    }

    public static void appendTaskEvent(Span span, TaskEventType eventType) {
        span.addEvent(eventType.id());
    }

    public static String generateTaskSpanName(
            TaskNameStrategy nameStrategy,
            String taskInstanceId,
            String taskName,
            int taskInstanceIteration, int retryAttempt) {
        switch (nameStrategy) {
            case ACTION_AND_TASK_NAME:
                return TASK_EXECUTE_ACTION + " " + taskName;
            case ACTION_AND_TASK_ID:
                return TASK_EXECUTE_ACTION + " " + taskInstanceId;
            case DEBUG:
                return taskInstanceId + "-" + " (" + taskName + ") # " + taskInstanceIteration + "(retry: " + retryAttempt
                        + ")";
            default:
                throw new NoSuchElementException("TaskNameStrategy " + nameStrategy + "is not recognized.");
        }
    }

    public static String generateWorkflowSpanName(String workflowName) {
        return WORKFLOW_EXECUTE_ACTION + " " + workflowName;
    }

    public static <T extends TaskBase> TaskSpanEnricher<T> getTaskSpanEnricher(T task) {
        return switch (TaskType.fromTask(task)) {
            case CALL_HTTP -> (span, sourceTask) -> enrichCallHTTP(span, (CallHTTP) sourceTask);
            case CALL_GRPC -> (span, sourceTask) -> enrichCallGRPC(span, (CallGRPC) sourceTask);
            case CALL_OPENAPI -> (span, sourceTask) -> enrichCallOpenAPI(span, (CallOpenAPI) sourceTask);
            case CALL_FUNCTION -> (span, sourceTask) -> enrichCallFunction(span, (CallFunction) sourceTask);
            case CALL_A2A -> (span, sourceTask) -> enrichA2A(span, (CallA2A) sourceTask);
            case RUN -> (span, sourceTask) -> enrichRunTask(span, (RunTask) sourceTask);
            case WAIT -> (span, sourceTask) -> enrichWaitTask(span, (WaitTask) sourceTask);
            case EMIT -> (span, sourceTask) -> enrichEmitTask(span, (EmitTask) sourceTask);
            case RAISE -> (span, sourceTask) -> enrichRaiseTask(span, (RaiseTask) sourceTask);
            default -> (span, sourceTask) -> {
            }; // NOOP fallback
        };
    }

    private static void enrichCallHTTP(SpanBuilder span, CallHTTP task) {
        HTTPArguments httpArguments = task.getWith();
        if (httpArguments != null) {
            setIfNotNull(span, CALL_HTTP_TASK_REQUEST_METHOD_ATTRIBUTE, httpArguments.getMethod(), String::toUpperCase);
            if (httpArguments.getEndpoint() != null) {
                span.setAttribute(CALL_HTTP_TASK_URL_FULL_ATTRIBUTE, extractFullUri(httpArguments.getEndpoint()));
            }
        }
    }

    private static void enrichCallGRPC(SpanBuilder span, CallGRPC task) {
        GRPCArguments grpcArguments = task.getWith();
        if (grpcArguments != null) {
            setIfNotNull(span, CALL_TASK_GRPC_METHOD_ATTRIBUTE, grpcArguments.getMethod());
            if (grpcArguments.getService() != null) {
                WithGRPCService grpcService = grpcArguments.getService();
                setIfNotNull(span, CALL_TASK_GRPC_SERVICE_ATTRIBUTE, grpcService.getName());
                setIfNotNull(span, CALL_TASK_GRPC_SERVER_ADDRESS_ATTRIBUTE, grpcService.getHost());
                setIfNotNull(span, CALL_TASK_GRPC_SERVER_PORT_ATTRIBUTE, Integer.toString(grpcService.getPort()));
            }
        }
    }

    private static void enrichCallOpenAPI(SpanBuilder span, CallOpenAPI task) {
        OpenAPIArguments openAPIArguments = task.getWith();
        if (openAPIArguments != null) {
            setIfNotNull(span, CALL_TASK_OPENAPI_OPERATION_ID_ATTRIBUTE, openAPIArguments.getOperationId());
            ExternalResource externalResource = openAPIArguments.getDocument();
            if (externalResource != null) {
                setIfNotNull(span, CALL_TASK_OPENAPI_DOCUMENT_NAME_ATTRIBUTE, externalResource.getName());
                if (externalResource.getEndpoint() != null) {
                    setIfNotNull(span, CALL_TASK_OPENAPI_DOCUMENT_ENDPOINT_ATTRIBUTE,
                            extractFullUri(externalResource.getEndpoint()));
                }
            }
        }
    }

    private static void enrichCallFunction(SpanBuilder span, CallFunction task) {
        setIfNotNull(span, CALL_TASK_FUNCTION_NAME_ATTRIBUTE, task.getCall());
    }

    private static void enrichA2A(SpanBuilder span, CallA2A task) {
        A2AArguments a2AArguments = task.getWith();
        if (a2AArguments != null) {
            setIfNotNull(span, CALL_A2A_METHOD_ATTRIBUTE, Objects.toString(a2AArguments.getMethod()));
            setIfNotNull(span, CALL_A2A_SERVER_ATTRIBUTE, extractFullUri(a2AArguments.getServer()));
            ExternalResource agentCard = a2AArguments.getAgentCard();
            if (agentCard != null) {
                setIfNotNull(span, CALL_A2A_AGENT_CARD_NAME_ATTRIBUTE, agentCard.getName());
                if (agentCard.getEndpoint() != null) {
                    setIfNotNull(span, CALL_A2A_AGENT_CARD_ENDPOINT_ATTRIBUTE, extractFullUri(agentCard.getEndpoint()));
                }
            }
        }
    }

    private static String extractFullUri(Endpoint endpoint) {
        return endpoint != null ? extractFullUri(endpoint.getUriTemplate()) : null;
    }

    private static String extractFullUri(UriTemplate uriTemplate) {
        if (uriTemplate != null) {
            if (uriTemplate.getLiteralUri() != null) {
                return uriTemplate.getLiteralUri().toString();
            } else {
                return uriTemplate.getLiteralUriTemplate();
            }
        }
        return null;
    }

    private static void enrichRunTask(SpanBuilder span, RunTask task) {
        if (task.getRun().getRunWorkflow() != null) {
            SubflowConfiguration subFlowConfig = task.getRun().getRunWorkflow().getWorkflow();
            if (subFlowConfig != null) {
                span.setAttribute(RUN_TASK_RUN_KIND_ATTRIBUTE, "workflow");
                setIfNotNull(span, RUN_TASK_RUN_WORKFLOW_NAMESPACE_ATTRIBUTE, subFlowConfig.getNamespace());
                setIfNotNull(span, RUN_TASK_RUN_WORKFLOW_NAME_ATTRIBUTE, subFlowConfig.getName());
                setIfNotNull(span, RUN_TASK_RUN_WORKFLOW_VERSION_ATTRIBUTE, subFlowConfig.getVersion());
            }
        } else if (task.getRun().getRunContainer() != null) {
            Container container = task.getRun().getRunContainer().getContainer();
            if (container != null) {
                span.setAttribute(RUN_TASK_RUN_KIND_ATTRIBUTE, "container");
                setIfNotNull(span, RUN_TASK_RUN_CONTAINER_NAME_ATTRIBUTE, container.getName());
                setIfNotNull(span, RUN_TASK_RUN_CONTAINER_IMAGE_NAME_ATTRIBUTE, container.getImage());
                setIfNotNull(span, RUN_TASK_RUN_CONTAINER_COMMAND_ATTRIBUTE, container.getCommand());
            }
        } else if (task.getRun().getRunScript() != null) {
            RunScript runScript = task.getRun().getRunScript();
            Script script = task.getRun().getRunScript().getScript() != null ? task.getRun().getRunScript().getScript().get()
                    : null;
            span.setAttribute(RUN_TASK_RUN_KIND_ATTRIBUTE, "script");
            if (script != null) {
                setIfNotNull(span, RUN_TASK_RUN_SCRIPT_LANGUAGE_ATTRIBUTE, script.getLanguage());
            }
            if (runScript.getScript() != null && runScript.getScript().getInlineScript() != null) {
                setIfNotNull(span, RUN_TASK_RUN_SCRIPT_CODE_ATTRIBUTE, runScript.getScript().getInlineScript().getCode());
            } else if (runScript.getScript() != null && runScript.getScript().getExternalScript() != null) {
                ExternalResource externalResource = runScript.getScript().getExternalScript().getSource();
                if (externalResource != null) {
                    setIfNotNull(span, RUN_TASK_RUN_SCRIPT_SOURCE_NAME_ATTRIBUTE, externalResource.getName());
                    if (externalResource.getEndpoint() != null) {
                        setIfNotNull(span, RUN_TASK_RUN_SCRIPT_SOURCE_ENDPOINT_ATTRIBUTE,
                                extractFullUri(externalResource.getEndpoint()));
                    }
                }
            }
        } else if (task.getRun().getRunShell() != null) {
            span.setAttribute(RUN_TASK_RUN_KIND_ATTRIBUTE, "shell");
            RunShell runShell = task.getRun().getRunShell();
            if (runShell != null && runShell.getShell() != null) {
                setIfNotNull(span, RUN_TASK_RUN_SHELL_COMMAND_ATTRIBUTE, runShell.getShell().getCommand());
            }
        }
    }

    private static void enrichWaitTask(SpanBuilder span, WaitTask task) {
        TimeoutAfter timeoutAfter = task.getWait();
        if (timeoutAfter != null) {
            if (timeoutAfter.getDurationLiteral() != null) {
                setIfNotNull(span, WAIT_TASK_DURATION_LITERAL_ATTRIBUTE, timeoutAfter.getDurationLiteral());
            } else if (timeoutAfter.getDurationExpression() != null) {
                setIfNotNull(span, WAIT_TASK_DURATION_EXPRESSION_ATTRIBUTE, timeoutAfter.getDurationExpression());
            } else if (timeoutAfter.getDurationInline() != null) {
                DurationInline durationInline = timeoutAfter.getDurationInline();
                if (durationInline.getDays() > 0) {
                    span.setAttribute(WAIT_TASK_DURATION_DAYS_ATTRIBUTE, durationInline.getDays());
                }
                if (durationInline.getHours() > 0) {
                    span.setAttribute(WAIT_TASK_DURATION_HOURS_ATTRIBUTE, durationInline.getHours());
                }
                if (durationInline.getMinutes() > 0) {
                    span.setAttribute(WAIT_TASK_DURATION_MINUTES_ATTRIBUTE, durationInline.getMinutes());
                }
                if (durationInline.getSeconds() > 0) {
                    span.setAttribute(WAIT_TASK_DURATION_SECONDS_ATTRIBUTE, durationInline.getSeconds());
                }
                if (durationInline.getMilliseconds() > 0) {
                    span.setAttribute(WAIT_TASK_DURATION_MILLISECONDS_ATTRIBUTE, durationInline.getSeconds());
                }
            }
        }
    }

    private static void enrichRaiseTask(SpanBuilder span, RaiseTask task) {
        RaiseTaskError raiseTaskError = task.getRaise() != null ? task.getRaise().getError() : null;
        if (raiseTaskError != null) {
            if (raiseTaskError.getRaiseErrorReference() != null) {
                span.setAttribute(RAISE_TASK_ERROR_REFERENCE_ATTRIBUTE, raiseTaskError.getRaiseErrorReference());
            } else if (raiseTaskError.getRaiseErrorDefinition() != null) {
                Error error = raiseTaskError.getRaiseErrorDefinition();
                if (error.getType() != null) {
                    if (error.getType().getExpressionErrorType() != null) {
                        span.setAttribute(RAISE_TASK_ERROR_TYPE_EXPRESSION_ATTRIBUTE, error.getType().getExpressionErrorType());
                    } else if (error.getType().getLiteralErrorType() != null) {
                        setIfNotNull(span, RAISE_TASK_ERROR_TYPE_URI_ATTRIBUTE,
                                extractFullUri(error.getType().getLiteralErrorType()));
                    }
                }
                span.setAttribute(RAISE_TASK_ERROR_STATUS_ATTRIBUTE, error.getStatus());
                ErrorInstance errorInstance = error.getInstance();
                if (errorInstance != null) {
                    if (errorInstance.getExpressionErrorInstance() != null) {
                        span.setAttribute(RAISE_TASK_ERROR_INSTANCE_ATTRIBUTE, errorInstance.getExpressionErrorInstance());
                    } else if (errorInstance.getLiteralErrorInstance() != null) {
                        span.setAttribute(RAISE_TASK_ERROR_INSTANCE_ATTRIBUTE, errorInstance.getLiteralErrorInstance());
                    }
                }
                ErrorTitle errorTitle = error.getTitle();
                if (errorTitle != null) {
                    if (errorTitle.getExpressionErrorTitle() != null) {
                        span.setAttribute(RAISE_TASK_ERROR_TITLE_ATTRIBUTE, errorTitle.getExpressionErrorTitle());
                    } else if (errorTitle.getLiteralErrorTitle() != null) {
                        span.setAttribute(RAISE_TASK_ERROR_TITLE_ATTRIBUTE, errorTitle.getLiteralErrorTitle());
                    }
                }
                ErrorDetails errorDetails = error.getDetail();
                if (errorDetails != null) {
                    if (errorDetails.getExpressionErrorDetails() != null) {
                        span.setAttribute(RAISE_TASK_ERROR_DETAILS_ATTRIBUTE, errorDetails.getExpressionErrorDetails());
                    } else if (errorDetails.getLiteralErrorDetails() != null) {
                        span.setAttribute(RAISE_TASK_ERROR_DETAILS_ATTRIBUTE, errorDetails.getLiteralErrorDetails());
                    }
                }
            }
        }
    }

    private static void enrichEmitTask(SpanBuilder span, EmitTask task) {
        if (task.getEmit() != null && task.getEmit().getEvent() != null && task.getEmit().getEvent().getWith() != null) {
            EventProperties eventProperties = task.getEmit().getEvent().getWith();
            setIfNotNull(span, TASK_EMIT_EVENT_ID_ATTRIBUTE, eventProperties.getId());
            setIfNotNull(span, TASK_EMIT_EVENT_TYPE_ATTRIBUTE, eventProperties.getType());
            if (eventProperties.getSource() != null) {
                EventSource source = eventProperties.getSource();
                if (source.getRuntimeExpression() != null) {
                    span.setAttribute(TASK_EMIT_EVENT_SOURCE_ATTRIBUTE, source.getRuntimeExpression());
                } else if (source.getUriTemplate() != null) {
                    setIfNotNull(span, TASK_EMIT_EVENT_SOURCE_ATTRIBUTE, extractFullUri(source.getUriTemplate()));
                }
            }
            setIfNotNull(span, TASK_EMIT_EVENT_SUBJECT_ATTRIBUTE, eventProperties.getSubject());
        }
    }

    private static void setIfNotNull(SpanBuilder span, String attribute, String value) {
        setIfNotNull(span, attribute, value, s -> s);
    }

    private static void setIfNotNull(SpanBuilder span, String attribute, String value, Function<String, String> transform) {
        if (value != null) {
            span.setAttribute(attribute, transform.apply(value));
        }
    }
}
