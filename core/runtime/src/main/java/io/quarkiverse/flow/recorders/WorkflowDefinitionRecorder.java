package io.quarkiverse.flow.recorders;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Function;

import jakarta.enterprise.inject.Any;

import io.quarkiverse.flow.Flowable;
import io.quarkiverse.flow.internal.WorkflowRegistry;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.annotations.Recorder;
import io.serverlessworkflow.api.WorkflowFormat;
import io.serverlessworkflow.api.WorkflowReader;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowDefinition;

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
                final WorkflowRegistry registry = context.getInjectedReference(WorkflowRegistry.class);
                return registry.register(flow);
            } catch (RuntimeException | ClassNotFoundException e) {
                throw new RuntimeException("Failed to create WorkflowDefinition for " + flowDescriptorClassName, e);
            }
        };
    }

    public Function<SyntheticCreationalContext<WorkflowDefinition>, WorkflowDefinition> workflowDefinitionFromFileCreator(
            String filename, byte[] content, WorkflowFormat workflowFormat) {
        return context -> {
            final WorkflowRegistry registry = context.getInjectedReference(WorkflowRegistry.class);
            try {
                Workflow workflow = WorkflowReader.readWorkflow(content, workflowFormat);
                return registry.register(workflow);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to create WorkflowDefinition for workflow at " + filename, e);
            }
        };
    }

}
