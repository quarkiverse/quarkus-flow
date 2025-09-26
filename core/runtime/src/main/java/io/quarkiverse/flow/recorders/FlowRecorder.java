package io.quarkiverse.flow.recorders;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.Supplier;

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

    public Supplier<WorkflowDefinition> workflowDefinitionSupplier(DiscoveredFlow d) {

        return () -> {
            try {
                final WorkflowApplication app = Arc.container().instance(WorkflowApplication.class).get();
                final ClassLoader cl = Thread.currentThread().getContextClassLoader();
                final Class<?> owner = Class.forName(d.className, true, cl);

                final Object target = d.isStatic ? null : Arc.container().instance(owner).get();

                final MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                final MethodType mt = MethodType.methodType(Workflow.class);
                final MethodHandle mh = d.isStatic ? lookup.findStatic(owner, d.methodName, mt)
                        : lookup.findVirtual(owner, d.methodName, mt);

                Workflow wf = d.isStatic ? (Workflow) mh.invokeExact() : (Workflow) mh.bindTo(target).invokeExact();

                // We always enforce the qualifier value to the workflow name
                wf.getDocument().setName(d.workflowName);

                return app.workflowDefinition(wf);
            } catch (RuntimeException re) {
                throw re;
            } catch (Throwable e) {
                throw new RuntimeException("Failed to create WorkflowDefinition for "
                        + d.className + "#" + d.methodName, e);
            }
        };

    }
}
