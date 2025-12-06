package io.quarkiverse.flow.recorders;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Paths;
import java.util.function.Supplier;

import jakarta.enterprise.inject.Any;

import io.quarkiverse.flow.Flowable;
import io.quarkiverse.flow.internal.WorkflowRegistry;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.runtime.annotations.Recorder;
import io.serverlessworkflow.api.WorkflowReader;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowDefinition;

/**
 * Registries all Workflow definitions found in the classpath built via the Java DSL.
 */
@Recorder
public class WorkflowDefinitionRecorder {

    public Supplier<WorkflowDefinition> workflowDefinitionSupplier(String flowDescriptorClassName) {
        return () -> {
            try {
                final ClassLoader cl = Thread.currentThread().getContextClassLoader();
                final Class<?> flowClass = Class.forName(flowDescriptorClassName, true, cl);

                final InstanceHandle<?> handle = Arc.container().instance(flowClass, Any.Literal.INSTANCE);
                if (!handle.isAvailable()) {
                    throw new IllegalStateException(
                            "Flow class '" + flowDescriptorClassName
                                    + "' was discovered as Flowable but is not a CDI bean. "
                                    + "Annotate it with @ApplicationScoped (or another CDI scope).");
                }

                final WorkflowRegistry registry = Arc.container().instance(WorkflowRegistry.class).get();
                return registry.register((Flowable) handle.get());
            } catch (RuntimeException | ClassNotFoundException e) {
                throw new RuntimeException("Failed to create WorkflowDefinition for " + flowDescriptorClassName, e);
            }
        };
    }

    public Supplier<WorkflowDefinition> workflowDefinitionFromFileSupplier(String location) {
        return () -> {
            final WorkflowRegistry registry = Arc.container().instance(WorkflowRegistry.class).get();
            try {
                Workflow workflow = WorkflowReader.readWorkflow(Paths.get(location));
                return registry.register(workflow);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to create WorkflowDefinition for workflow at " + location, e);
            }
        };
    }

}
