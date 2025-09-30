package io.quarkiverse.flow.recorders;

import java.util.function.Supplier;

import jakarta.enterprise.inject.Any;

import io.quarkiverse.flow.Flow;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.annotations.Recorder;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.expressions.ExpressionFactory;

// TODO: produce definitions from workflows in the YAML format within the current classpath

/**
 * Registries all Workflow definitions found in the classpath built via the Java DSL.
 */
@Recorder
public class FlowRecorder {

    // TODO: expose WorkflowApplication build via configuration
    // TODO: add injected providers from user
    // TODO: wire events/persistence/REST/RESTClient/etc infrastructure to the WorkflowApplication

    public Supplier<WorkflowApplication> workflowAppSupplier(ShutdownContext shutdownContext) {
        return () -> {
            final ArcContainer container = Arc.container();
            final WorkflowApplication.Builder builder = WorkflowApplication.builder();
            builder.withExpressionFactory(container.instance(ExpressionFactory.class).get());
            final WorkflowApplication app = builder.build();

            shutdownContext.addShutdownTask(app::close);
            return app;
        };
    }

    public Supplier<WorkflowDefinition> workflowDefinitionSupplier(String flowDescriptorClassName) {

        return () -> {
            try {
                final WorkflowApplication app = Arc.container().instance(WorkflowApplication.class).get();
                final ClassLoader cl = Thread.currentThread().getContextClassLoader();
                final Class<?> flowClass = Class.forName(flowDescriptorClassName, true, cl);

                Flow flow = (Flow) Arc.container().instance(flowClass, Any.Literal.INSTANCE).get();
                final Workflow wf = flow.descriptor();

                return app.workflowDefinition(wf);
            } catch (RuntimeException re) {
                throw re;
            } catch (Throwable e) {
                throw new RuntimeException("Failed to create WorkflowDefinition for " + flowDescriptorClassName, e);
            }
        };

    }
}
