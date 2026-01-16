package io.quarkiverse.flow.devui;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.quarkiverse.flow.internal.WorkflowInvocationMetadata;
import io.quarkiverse.flow.internal.WorkflowInvoker;
import io.quarkiverse.flow.internal.WorkflowRegistry;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.logging.Log;
import io.quarkus.runtime.annotations.JsonRpcDescription;
import io.serverlessworkflow.api.types.Input;
import io.serverlessworkflow.api.types.SchemaInline;
import io.serverlessworkflow.api.types.SchemaUnion;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowDefinitionId;
import io.serverlessworkflow.impl.WorkflowModel;
import io.serverlessworkflow.mermaid.Mermaid;
import io.smallrye.common.annotation.Blocking;

@ApplicationScoped
public class WorkflowRPCService {

    private static final Logger LOG = LoggerFactory.getLogger(WorkflowRPCService.class);
    private static final String RESULT_WITH_AGENTIC_SCOPE_CLASS = "dev.langchain4j.agentic.scope.ResultWithAgenticScope";

    @Inject
    ObjectMapper objectMapper;
    @Inject
    WorkflowRegistry registry;

    public WorkflowRPCService() {
    }

    @JsonRpcDescription("Get numbers of workflows available in the application")
    public int getNumbersOfWorkflows() {
        return registry.count();
    }

    @JsonRpcDescription("Get info about workflows")
    public List<WorkflowInfo> getWorkflows() {
        return registry.all().stream()
                .map(w -> new WorkflowInfo(
                        WorkflowDefinitionId.of(w),
                        w.getDocument().getSummary()))
                .toList();
    }

    @JsonRpcDescription("Generate a mermaid diagram from the workflow's definition")
    public MermaidDefinition generateMermaidDiagram(
            @JsonRpcDescription("Workflow's id") WorkflowDefinitionId id) {
        LOG.info("Generating a mermaid diagram from the workflow's definition '{}'", id.name());
        final MermaidDefinition mermaidDefinition = new MermaidDefinition(new Mermaid().from(registry.lookupDescriptor(id)
                .orElseThrow(() -> new IllegalStateException("Workflow with id '" + id + "' not found"))));
        if (mermaidDefinition.mermaid.isEmpty()) {
            LOG.warn("Workflow with id '{}' has no diagram available or failed to generate it", id.name());
        }
        return mermaidDefinition;
    }

    @Blocking
    @JsonRpcDescription("Execute a workflow given an input")
    public WorkflowOutput executeWorkflow(
            @JsonRpcDescription("Workflow's id") WorkflowDefinitionId id,
            @JsonRpcDescription("Workflow's input") String input) {

        Workflow workflow = registry.lookupDescriptor(id)
                .orElseThrow(() -> new IllegalStateException("Workflow with id '" + id + "' not found"));
        Optional<WorkflowInvoker> invoker = WorkflowInvocationMetadata.beanInvokerOf(workflow);
        Object parsedInput = parseStringIfNeeded(input);
        Object result;

        if (invoker.isPresent()) {
            result = invokeBeanEntryPoint(invoker.get(), parsedInput);
        } else {
            Optional<WorkflowDefinition> def = registry.lookup(id);
            WorkflowModel wm = def
                    .orElseThrow(() -> new IllegalStateException("WorkflowDefinition not found for workflow " + id))
                    .instance(parsedInput)
                    .start()
                    .join();
            result = wm.asJavaObject();
        }

        return new WorkflowOutput(
                (result instanceof String) ? MediaType.TEXT_PLAIN : MediaType.APPLICATION_JSON,
                result);
    }

    @JsonRpcDescription("Get the JSON Schema for the workflow input, if present")
    public Map<String, Object> getInputSchema(
            @JsonRpcDescription("Workflow's id") WorkflowDefinitionId id) {

        Workflow wf = registry.lookupDescriptor(id)
                .orElseGet(() -> registry.lookup(id)
                        .map(WorkflowDefinition::workflow)
                        .orElseThrow(() -> new IllegalStateException("Workflow with id '" + id + "' not found")));

        Input input = wf.getInput();
        if (input == null) {
            return null;
        }

        SchemaUnion schemaUnion = input.getSchema();
        if (schemaUnion == null) {
            return null;
        }

        SchemaInline inline = schemaUnion.getSchemaInline();
        if (inline == null) {
            return null;
        }

        Object document = inline.getDocument();
        if (document == null) {
            return null;
        }

        if (document instanceof ObjectNode node) {
            // Convert the JSON tree into a plain Map so the browser sees the real schema
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.convertValue(node, Map.class);
            return map;
        }

        if (document instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) map;
            return result;
        }

        // Fallback: try to convert any other type to a Map
        @SuppressWarnings("unchecked")
        Map<String, Object> map = objectMapper.convertValue(document, Map.class);
        return map;
    }

    /**
     * Use Arc to obtain the bean and invoke the configured method.
     */
    private Object invokeBeanEntryPoint(WorkflowInvoker invoker, Object parsedInput) {
        String beanClassName = invoker.beanClassName();
        String methodName = invoker.methodName();

        if (Log.isDebugEnabled()) {
            LOG.debug("Invoking workflow via bean: {}#{}", beanClassName, methodName);
        }

        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            Class<?> beanClass = Class.forName(beanClassName, true, cl);

            InstanceHandle<?> handle = Arc.container().instance(beanClass);
            if (!handle.isAvailable()) {
                throw new IllegalStateException(
                        "Bean invoker class '" + beanClassName + "' not available in CDI");
            }

            Object bean = handle.get();
            Method method = resolveInvokerMethod(beanClass, invoker);
            Object[] args = resolveArgumentsForMethod(method, parsedInput);

            return sanitizeResultForDevUI(method.invoke(bean, args));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to invoke workflow via bean invoker: " + beanClassName + "#" + methodName, e);
        }
    }

    private Object sanitizeResultForDevUI(Object result) {
        if (result == null) {
            return null;
        }

        try {
            Class<?> clazz = result.getClass();

            // Handle ResultWithAgenticScope<R> reflectively
            if (RESULT_WITH_AGENTIC_SCOPE_CLASS.equals(clazz.getName())) {
                // Prefer record-style accessor `result()`
                try {
                    var m = clazz.getMethod("result");
                    return m.invoke(result);
                } catch (NoSuchMethodException e) {
                    // Fallback: try JavaBean-style `getResult()`
                    var m = clazz.getMethod("getResult");
                    return m.invoke(result);
                }
            }

        } catch (Exception e) {
            LOG.debug("Unable to unwrap LC4J ResultWithAgenticScope for Dev UI, returning raw result", e);
        }

        return result;
    }

    private Method resolveInvokerMethod(Class<?> beanClass, WorkflowInvoker invoker) {
        String methodName = invoker.methodName();
        String[] expectedTypes = invoker.parameterTypeNames();

        return Arrays.stream(beanClass.getMethods())
                .filter(m -> m.getName().equals(methodName))
                .filter(m -> m.getParameterTypes().length == expectedTypes.length)
                .filter(m -> Arrays.equals(Arrays.stream(m.getParameterTypes()).map(Class::getName).toArray(), expectedTypes))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No suitable method '" + methodName + "(" + String.join(",", expectedTypes) + ")' found on "
                                + beanClass.getName()));
    }

    /**
     * Simple argument resolution strategy:
     * <p>
     * - 0 params: ignore input
     * - 1 param: map the whole parsed input into that param type
     * - N params: expect JSON object; map by parameter name
     */
    private Object[] resolveArgumentsForMethod(Method method, Object parsedInput) {
        int paramCount = method.getParameterCount();
        if (paramCount == 0) {
            return new Object[0];
        }

        if (paramCount == 1) {
            Class<?> paramType = method.getParameterTypes()[0];
            return new Object[] { objectMapper.convertValue(parsedInput, paramType) };
        }

        if (!(parsedInput instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException(
                    "Expected JSON object for multi-argument method '" + method + "', but got: " + parsedInput);
        }

        Object[] args = new Object[paramCount];
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < paramCount; i++) {
            Parameter p = parameters[i];
            Object rawValue = map.get(p.getName());
            args[i] = objectMapper.convertValue(rawValue, p.getType());
        }
        return args;
    }

    /**
     * Parses the input string as JSON if possible, otherwise returns the original string.
     */
    private Object parseStringIfNeeded(String str) {
        try {
            return objectMapper.readValue(str, Map.class);
        } catch (Exception e) {
            // Not a JSON string, return as-is
            return str;
        }
    }

    public record MermaidDefinition(String mermaid) {
    }

    public record WorkflowInfo(WorkflowDefinitionId id, String description) {
    }

    public record WorkflowOutput(String mimetype, Object data) {
    }
}
