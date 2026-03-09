package io.quarkiverse.flow.deployment.test.devui;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import io.quarkiverse.flow.internal.WorkflowRegistry;
import io.quarkus.runtime.StartupEvent;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.spec.WorkflowBuilder;

@ApplicationScoped
public class CachedDevUIDescriptorObserver {

    void onStart(@Observes StartupEvent event, WorkflowRegistry registry) {
        Workflow descriptor = WorkflowBuilder.workflow("cached-devui-workflow")
                .tasks(t -> t.set("cachedTask", """
                        { "message": "cached" }
                        """))
                .build();

        registry.cacheDescriptor(descriptor);
    }
}
