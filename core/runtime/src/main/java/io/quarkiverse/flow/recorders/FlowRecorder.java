package io.quarkiverse.flow.recorders;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Supplier;

import io.quarkiverse.flow.FlowKey;
import io.quarkiverse.flow.FlowRegistry;
import io.quarkus.arc.runtime.BeanContainer;
import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.annotations.Recorder;
import io.serverlessworkflow.api.types.Document;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowApplication;

// TODO: registry workflows in the YAML format within the current classpath

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

    public void registerWorkflows(BeanContainer container, List<DiscoveredFlow> items) {
        final FlowRegistry registry = container.beanInstance(FlowRegistry.class);

        // registry idempotent for hot-reload
        registry.clear();

        ClassLoader appCl = Thread.currentThread().getContextClassLoader();

        for (DiscoveredFlow item : items) {
            try {
                Class<?> clazz = Class.forName(item.className(), true, appCl);
                Method method = clazz.getDeclaredMethod(item.methodName());
                method.setAccessible(true);

                Object instance = item.isStatic() ? null : container.beanInstance(clazz);
                Object ret = method.invoke(instance);

                if (!(ret instanceof Workflow wf)) {
                    throw new IllegalStateException("@WorkflowDefinition method must return Workflow: "
                            + item.className() + "#" + item.methodName());
                }

                final Document doc = wf.getDocument();
                String id = item.methodName();
                if (doc != null && doc.getName() != null && !doc.getName().isBlank()) {
                    id = doc.getName();
                }
                registry.register(id, wf, new FlowKey(item.className(), item.methodName()));
            } catch (Exception e) {
                throw new RuntimeException("Failed to instantiate @WorkflowDefinition: "
                        + item.className() + "#" + item.methodName(), e);
            }
        }
    }
}
