package io.quarkiverse.flow.recorders;

import java.lang.reflect.Method;
import java.util.function.Supplier;

import io.quarkus.arc.Arc;
import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.annotations.Recorder;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinition;

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
            WorkflowApplication app = WorkflowApplication.builder().build();
            shutdownContext.addShutdownTask(app::close);
            return app;
        };
    }

    public Supplier<WorkflowDefinition> workflowDefinitionSupplier(DiscoveredFlow d) {

        return () -> {
            try {
                WorkflowApplication app = Arc.container().instance(WorkflowApplication.class).get();
                ClassLoader cl = Thread.currentThread().getContextClassLoader();
                Class<?> owner = Class.forName(d.className, true, cl);
                Method m = owner.getDeclaredMethod(d.methodName);
                m.setAccessible(true);

                Object target = d.isStatic ? null : Arc.container().instance(owner).get();
                Workflow wf = (Workflow) m.invoke(target);

                // We always enforce the qualifier value to the workflow name
                wf.getDocument().setName(d.workflowName);

                return app.workflowDefinition(wf);
            } catch (RuntimeException re) {
                throw re;
            } catch (Exception e) {
                throw new RuntimeException("Failed to create WorkflowDefinition for "
                        + d.className + "#" + d.methodName, e);
            }
        };

    }
}
