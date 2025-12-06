package io.quarkiverse.flow.internal;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.api.types.WorkflowMetadata;
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

    public static Optional<WorkflowInvoker> beanInvokerOf(WorkflowDefinition definition) {
        if (definition == null) {
            return Optional.empty();
        }
        return beanInvokerOf(definition.workflow());
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

}
