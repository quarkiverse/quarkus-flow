package io.quarkiverse.flow.internal;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.api.types.WorkflowMetadata;
import io.serverlessworkflow.fluent.spec.DocumentBuilder;
import io.serverlessworkflow.impl.WorkflowDefinition;

/**
 * Helper for storing and reading "invocation" metadata on a workflow document.
 * <p>
 * Metadata contract:
 * <ul>
 * <li>{@code META_INVOKER_BEAN} – FQCN of the CDI bean to invoke</li>
 * <li>{@code META_INVOKER_METHOD} – method name on that bean</li>
 * <li>{@code META_INVOKER_KIND} – free-form string (e.g. "langchain4j")</li>
 * </ul>
 */
public final class WorkflowInvocationMetadata {

    private static final Logger LOG = LoggerFactory.getLogger(WorkflowInvocationMetadata.class);

    /**
     * FQCN of the CDI bean that should be used as an invoker.
     */
    public static final String META_INVOKER_BEAN = "io.quarkiverse.flow.invoker.bean";

    /**
     * Method name on the invoker bean to call.
     */
    public static final String META_INVOKER_METHOD = "io.quarkiverse.flow.invoker.method";

    /**
     * Method parameter types.
     */
    public static final String META_INVOKER_METHOD_PARAMS = "io.quarkiverse.flow.invoker.method.params";

    /**
     * Optional "kind" of invoker, e.g. "langchain4j-sequence", "langchain4j-parallel", etc.
     */
    public static final String META_INVOKER_KIND = "io.quarkiverse.flow.invoker.kind";

    private WorkflowInvocationMetadata() {
    }

    /**
     * Attach bean-invoker metadata to a {@link Workflow} instance.
     * <p>
     * This does not create a WorkflowDefinition; it only mutates the
     * document metadata of the given workflow.
     */
    public static void setBeanInvoker(Workflow workflow,
            Class<?> beanClass,
            Method method,
            String kind) {
        Objects.requireNonNull(beanClass, "class must not be null");
        Objects.requireNonNull(method, "method must not be null");
        Objects.requireNonNull(workflow, "workflow must not be null");
        Objects.requireNonNull(workflow.getDocument(), "Workflow document must not be null");

        WorkflowMetadata metadata = workflow.getDocument().getMetadata();
        if (metadata == null) {
            metadata = new WorkflowMetadata();
            workflow.getDocument().setMetadata(metadata);
        }

        metadata.withAdditionalProperty(META_INVOKER_BEAN, beanClass.getName());
        metadata.withAdditionalProperty(META_INVOKER_METHOD, method.getName());
        metadata.withAdditionalProperty(META_INVOKER_METHOD_PARAMS,
                Arrays.stream(method.getParameterTypes()).map(Class::getName).toArray(String[]::new));

        if (kind != null && !kind.isBlank()) {
            metadata.withAdditionalProperty(META_INVOKER_KIND, kind);
        }
    }

    public static Optional<WorkflowInvoker> beanInvokerOf(Workflow workflow) {
        if (workflow == null || workflow.getDocument() == null) {
            return Optional.empty();
        }
        WorkflowMetadata metadata = workflow.getDocument().getMetadata();
        if (metadata == null ||
                metadata.getAdditionalProperties() == null ||
                metadata.getAdditionalProperties().isEmpty()) {
            return Optional.empty();
        }

        Object bean = metadata.getAdditionalProperties().get(META_INVOKER_BEAN);
        Object method = metadata.getAdditionalProperties().get(META_INVOKER_METHOD);
        Object kind = metadata.getAdditionalProperties().get(META_INVOKER_KIND);
        Object params = metadata.getAdditionalProperties().get(META_INVOKER_METHOD_PARAMS);

        if (!(bean instanceof String beanClass) || beanClass.isBlank()) {
            return Optional.empty();
        }
        if (!(method instanceof String methodName) || methodName.isBlank()) {
            return Optional.empty();
        }

        String kindStr = (kind instanceof String k && !k.isBlank()) ? k : null;
        return Optional.of(new WorkflowInvoker(beanClass, methodName, (String[]) params, kindStr));
    }

    /**
     * Creates a Consumer that sets bean invoker metadata on a workflow.
     * Uses thread context classloader to resolve the bean class at runtime.
     * <p>
     * This method is intended for use in generated AgenticFlow classes where
     * the bean class may not be available at the time the Consumer is created
     * (e.g., nested agent classes created by LangChain4j at runtime).
     *
     * @param beanClassName FQCN of the bean class
     * @param methodName method name to invoke
     * @param paramTypeNames parameter type names (FQCNs), or null if no parameters
     * @param kind invoker kind (e.g., "langchain4j")
     * @return Consumer that sets the metadata, or null if class/method resolution fails
     */
    public static Consumer<DocumentBuilder.WorkflowMetadataBuilder> beanInvokerMetadata(
            String beanClassName,
            String methodName,
            String[] paramTypeNames,
            String kind) {
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            Class<?> beanClass = Class.forName(beanClassName, true, cl);
            Class<?>[] paramTypes = resolveParamTypes(paramTypeNames, cl);
            Method method = beanClass.getMethod(methodName, paramTypes);
            return beanInvokerMetadata(beanClass, method, kind);
        } catch (Exception e) {
            LOG.debug("Failed to resolve bean invoker: {}.{}({})",
                    beanClassName, methodName,
                    paramTypeNames != null ? String.join(", ", paramTypeNames) : "", e);
            return null;
        }
    }

    /**
     * Creates a Consumer that sets bean invoker metadata on a workflow.
     *
     * @param beanClass bean class
     * @param method method to invoke
     * @param kind invoker kind (e.g., "langchain4j")
     * @return Consumer that sets the metadata
     */
    public static Consumer<DocumentBuilder.WorkflowMetadataBuilder> beanInvokerMetadata(
            Class<?> beanClass,
            Method method,
            String kind) {
        Objects.requireNonNull(beanClass, "beanClass must not be null");
        Objects.requireNonNull(method, "method must not be null");

        return m -> {
            m.metadata(META_INVOKER_BEAN, beanClass.getName());
            m.metadata(META_INVOKER_METHOD, method.getName());
            m.metadata(META_INVOKER_METHOD_PARAMS,
                    Arrays.stream(method.getParameterTypes()).map(Class::getName).toArray(String[]::new));
            if (kind != null && !kind.isBlank()) {
                m.metadata(META_INVOKER_KIND, kind);
            }
        };
    }

    /**
     * Resolves parameter types from their FQCN strings using the given classloader.
     *
     * @param paramTypeNames parameter type names, or null
     * @param cl classloader to use
     * @return resolved parameter types array
     * @throws ClassNotFoundException if any type cannot be resolved
     */
    private static Class<?>[] resolveParamTypes(String[] paramTypeNames, ClassLoader cl)
            throws ClassNotFoundException {
        if (paramTypeNames == null || paramTypeNames.length == 0) {
            return new Class<?>[0];
        }
        Class<?>[] paramTypes = new Class<?>[paramTypeNames.length];
        for (int i = 0; i < paramTypeNames.length; i++) {
            paramTypes[i] = Class.forName(paramTypeNames[i], true, cl);
        }
        return paramTypes;
    }

}
