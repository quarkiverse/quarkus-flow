package io.quarkiverse.flow.recorders;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Function;

import jakarta.enterprise.inject.Any;

import io.quarkiverse.flow.FlowMetadata;
import io.quarkiverse.flow.Flowable;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.annotations.Recorder;
import io.serverlessworkflow.api.WorkflowReader;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.api.types.WorkflowMetadata;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.smallrye.common.annotation.Identifier;

/**
 * Registries all Workflow definitions found in the classpath built via the Java DSL.
 */
@Recorder
public class WorkflowDefinitionRecorder {

    public Function<SyntheticCreationalContext<WorkflowDefinition>, WorkflowDefinition> workflowDefinitionCreator(
            String flowDescriptorClassName) {
        return context -> {
            try {
                final ClassLoader cl = Thread.currentThread().getContextClassLoader();
                final Class<?> flowClass = Class.forName(flowDescriptorClassName, true, cl);

                final Flowable flow = (Flowable) context.getInjectedReference(flowClass, Any.Literal.INSTANCE);
                final WorkflowApplication app = context.getInjectedReference(WorkflowApplication.class);

                Workflow workflow = addFlowableMetadata(flow);
                return app.workflowDefinition(workflow);
            } catch (RuntimeException | ClassNotFoundException e) {
                throw new RuntimeException("Failed to create WorkflowDefinition for " + flowDescriptorClassName, e);
            }
        };
    }

    /**
     * Creates a WorkflowDefinition bean supplier that loads workflow content from classpath resources at runtime.
     * <p>
     * This method records only the resource path (not the full content) in bytecode, significantly reducing
     * the bytecode size and avoiding MethodTooLargeException when registering many workflows.
     *
     * @param resourcePath the classpath resource path (e.g., "flow/order-workflow.yaml")
     * @return a function that creates WorkflowDefinition instances by loading from classpath
     */
    public Function<SyntheticCreationalContext<WorkflowDefinition>, WorkflowDefinition> workflowDefinitionFromResourceCreator(
            String resourcePath) {
        return context -> {
            final WorkflowApplication app = context.getInjectedReference(WorkflowApplication.class);
            try {
                Workflow workflow = WorkflowReader.readWorkflowFromClasspath(resourcePath);
                return app.workflowDefinition(workflow);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to load workflow from resource: " + resourcePath, e);
            }
        };
    }

    public Function<SyntheticCreationalContext<WorkflowDefinition>, WorkflowDefinition> workflowDefinitionVersionlessDelegateCreator(
            String versionedIdentifier) {
        return context -> context.getInjectedReference(WorkflowDefinition.class,
                Identifier.Literal.of(versionedIdentifier));
    }

    /**
     * Adds Flow-specific metadata to the workflow descriptor.
     * This metadata is used to link workflows back to their Flow class.
     */
    private static Workflow addFlowableMetadata(Flowable flowable) {
        final Workflow workflow = flowable.descriptor();
        if (workflow.getDocument().getMetadata() == null) {
            workflow.getDocument().setMetadata(new WorkflowMetadata());
        }
        workflow.getDocument().getMetadata().setAdditionalProperty(FlowMetadata.FLOW_IDENTIFIER_CLASS,
                flowable.identifier());
        return workflow;
    }
}
