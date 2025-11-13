package io.quarkiverse.flow.recorders;

import java.util.function.Supplier;

import jakarta.enterprise.inject.Any;

import io.quarkiverse.flow.Flow;
import io.quarkus.arc.Arc;
import io.quarkus.runtime.annotations.Recorder;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinition;

/**
 * Registries all Workflow definitions found in the classpath built via the Java DSL.
 */
@Recorder
public class WorkflowDefinitionRecorder {

    public Supplier<WorkflowDefinition> workflowDefinitionSupplier(String flowDescriptorClassName) {
        return () -> {
            try {
                final WorkflowApplication app = Arc.container().instance(WorkflowApplication.class).get();
                final ClassLoader cl = Thread.currentThread().getContextClassLoader();
                final Class<?> flowClass = Class.forName(flowDescriptorClassName, true, cl);

                Flow flow = (Flow) Arc.container().instance(flowClass, Any.Literal.INSTANCE).get();
                final Workflow wf = flow.descriptor();

                return app.workflowDefinition(wf);
            } catch (RuntimeException | ClassNotFoundException e) {
                throw new RuntimeException("Failed to create WorkflowDefinition for " + flowDescriptorClassName, e);
            }
        };
    }

}
