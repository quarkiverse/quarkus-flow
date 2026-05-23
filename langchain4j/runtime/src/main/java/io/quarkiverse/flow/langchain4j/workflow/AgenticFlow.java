package io.quarkiverse.flow.langchain4j.workflow;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Consumer;

import jakarta.inject.Inject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.internal.WorkflowInvocationMetadata;
import io.quarkiverse.flow.internal.WorkflowNameUtils;
import io.serverlessworkflow.fluent.spec.DocumentBuilder;
import io.serverlessworkflow.fluent.spec.InputBuilder;
import io.serverlessworkflow.impl.WorkflowDefinitionId;
import io.serverlessworkflow.impl.WorkflowModel;

/**
 * Marker interface for Gizmo generated agentic {@link io.quarkiverse.flow.Flow}s.
 * Flows are generated in build-time based on Langchain4j Agentic annotations such as
 * <ul>
 * <li>{@link dev.langchain4j.agentic.declarative.SequenceAgent}</li>
 * <li>{@link dev.langchain4j.agentic.declarative.ConditionalAgent}</li>
 * <li>{@link dev.langchain4j.agentic.declarative.LoopAgent}</li>
 * <li>{@link dev.langchain4j.agentic.declarative.ParallelAgent}</li>
 * </ul>
 */
public abstract class AgenticFlow extends Flow {

    protected static final String INVOKER_KIND = "langchain4j";

    @Inject
    ObjectMapper objectMapper;

    public AgenticFlow() {
    }

    public AgenticFlow(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Helper for the very common “AgenticScope passthrough” output mapping.
     * Reuse this in all Flow-*AgentService implementations that
     * want to keep AgenticScope as the thread of data between tasks.
     */
    static Object agenticScopePassthrough(WorkflowModel rawInput) {
        Object raw = rawInput.asJavaObject();
        if (raw instanceof AgenticScope scope) {
            return scope;
        }
        throw new IllegalStateException("Expected AgenticScope but got " + raw);
    }

    static Method findMethodBySignature(Class<?> clazz, String methodName, List<String> paramTypeNames)
            throws Exception {
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                Class<?>[] paramTypes = method.getParameterTypes();
                if (paramTypes.length == paramTypeNames.size()) {
                    boolean matches = true;
                    for (int i = 0; i < paramTypes.length; i++) {
                        if (!paramTypes[i].getName().equals(paramTypeNames.get(i))) {
                            matches = false;
                            break;
                        }
                    }
                    if (matches) {
                        return method;
                    }
                }
            }
        }
        throw new NoSuchMethodException("Method not found: " + methodName + "(" +
                String.join(", ", paramTypeNames) + ")");
    }

    protected Consumer<DocumentBuilder> buildDocument() {
        WorkflowDefinitionId id = WorkflowNameUtils.newId(agentClassName());
        Consumer<DocumentBuilder.WorkflowMetadataBuilder> metadata = invocationMetadata();
        return d -> {
            d.name(id.name())
                    .namespace(id.namespace())
                    .version(id.version())
                    .summary(description());
            if (metadata != null) {
                d.metadata(metadata);
            }
        };
    }

    protected Object executeAgent(DefaultAgenticScope scope, int subAgentIndex) {
        FlowPlanner planner = scope.executionContextAs(FlowPlanner.class);
        return planner.executeAgent(subAgentIndex).join();
    }

    public abstract String agentClassName();

    /**
     * Returns the base task names (method names) for subagents.
     * Used for workflow task naming and visualization.
     * Generated implementations return the method names of subagent interfaces.
     */
    protected abstract List<String> subAgentTaskNames();

    public String description() {
        return "";
    }

    /**
     * Returns the JSON schema string for the workflow input.
     * Overridden by generated implementations to return the build-time generated schema.
     *
     * @return the input schema as a JSON string
     */
    protected abstract String getInputSchemaJson();

    public Consumer<InputBuilder> inputSchema() {
        String schemaJson = getInputSchemaJson();
        if (schemaJson == null || schemaJson.isEmpty()) {
            // No schema to apply
            return null;
        }
        try {
            JsonNode schemaNode = objectMapper.readTree(schemaJson);
            return builder -> {
                builder.schema(schemaNode);
                // Clear the InputFrom that InputBuilder initializes by default
                // to avoid SDK errors about "Both object and str are null"
                builder.build().setFrom(null);
            };
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse input schema JSON", e);
        }
    }

    /**
     * Returns the agent interface method name for DevUI invocation.
     * Default implementation returns null (prod mode).
     * Overridden by Gizmo in dev mode only.
     *
     * @return method name or null
     */
    protected String invokerMethodName() {
        return null;
    }

    /**
     * Returns the agent interface method parameter type names for DevUI invocation.
     * Default implementation returns null (prod mode).
     * Overridden by Gizmo in dev mode only.
     *
     * @return parameter type names array or null
     */
    protected String[] invokerMethodParams() {
        return null;
    }

    /**
     * Returns the invocation metadata consumer for DevUI integration.
     * Returns null in prod mode (invokerMethodName() returns null).
     * In dev mode, Gizmo overrides invokerMethodName() and invokerMethodParams(),
     * allowing DevUI to invoke the agent interface method directly.
     *
     * @return Consumer for workflow metadata builder, or null if not in dev mode
     */
    protected Consumer<DocumentBuilder.WorkflowMetadataBuilder> invocationMetadata() {
        String methodName = invokerMethodName();
        if (methodName == null) {
            return null; // Not overridden - prod mode
        }
        return WorkflowInvocationMetadata.beanInvokerMetadata(
                agentClassName(),
                methodName,
                invokerMethodParams(),
                INVOKER_KIND);
    }
}
