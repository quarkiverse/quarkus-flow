package io.quarkiverse.flow.devui;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.flow.internal.WorkflowInvocationMetadata;
import io.quarkiverse.flow.internal.WorkflowInvoker;
import io.quarkiverse.flow.internal.WorkflowRegistry;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.logging.Log;
import io.quarkus.runtime.annotations.JsonRpcDescription;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowModel;
import io.serverlessworkflow.mermaid.Mermaid;
import io.smallrye.common.annotation.Blocking;

@ApplicationScoped
public class WorkflowRPCService {

    private static Logger LOG = LoggerFactory.getLogger(WorkflowRPCService.class);
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
                        w.workflow().getDocument().getName(),
                        w.workflow().getDocument().getSummary()))
                .toList();
    }

    @JsonRpcDescription("Generate a mermaid diagram from the workflow's definition")
    public MermaidDefinition generateMermaidDiagram(
            @JsonRpcDescription("Workflow's name") String workflowName) {
        if (Log.isDebugEnabled()) {
            Log.debug("Generating diagram for workflow: " + workflowName);
        }
        WorkflowDefinition def = findWorkflowDefinitionByName(workflowName);
        return new MermaidDefinition(new Mermaid().from(def.workflow()));
    }

    @Blocking
    @JsonRpcDescription("Execute a workflow given an input")
    public WorkflowOutput executeWorkflow(
            @JsonRpcDescription("Workflow's name") String workflowName,
            @JsonRpcDescription("Workflow's input") String input) {

        WorkflowDefinition def = findWorkflowDefinitionByName(workflowName);
        Object parsedInput = parseStringIfNeeded(input);

        Object result = executeWithInvokerIfPresent(def, parsedInput);

        return new WorkflowOutput(
                (result instanceof String) ? "text/plain" : "application/json",
                result);
    }

    /**
     * If the workflow has an attached bean invoker (e.g. LC4J agent interface),
     * use that as the entrypoint; otherwise call the workflow engine directly.
     */
    private Object executeWithInvokerIfPresent(WorkflowDefinition def, Object parsedInput) {
        var workflow = def.workflow();
        var beanInvokerOpt = WorkflowInvocationMetadata.beanInvokerOf(workflow);

        if (beanInvokerOpt.isEmpty()) {
            WorkflowModel wm = def.instance(parsedInput)
                    .start()
                    .join();
            return wm.asJavaObject();
        }

        WorkflowInvoker invoker = beanInvokerOpt.get();
        return invokeBeanEntryPoint(invoker, parsedInput);
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

            return method.invoke(bean, args);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to invoke workflow via bean invoker: " + beanClassName + "#" + methodName, e);
        }
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
     * Finds {@link WorkflowDefinition} by the given workflow document name.
     */
    private WorkflowDefinition findWorkflowDefinitionByName(final String workflowName) {
        return registry.lookupByDocumentName(workflowName)
                .orElseThrow(() -> new IllegalStateException("Workflow with name '" + workflowName + "' not found"));
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

    public record WorkflowInfo(String name, String description) {
    }

    public record WorkflowOutput(String mimetype, Object data) {
    }
}
