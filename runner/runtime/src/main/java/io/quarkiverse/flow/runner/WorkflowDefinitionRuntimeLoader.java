package io.quarkiverse.flow.runner;

import io.quarkiverse.flow.internal.WorkflowApplicationReady;
import io.quarkus.arc.Unremovable;
import io.serverlessworkflow.impl.WorkflowApplication;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
@Unremovable
public class WorkflowDefinitionRuntimeLoader {

    @Inject
    WorkflowApplication application;

    @Inject
    FlowRunnerConfig config;

    void onStart(@Observes WorkflowApplicationReady ev) {

    }

    private void loadWorkflowDefinitions() {
        if (FlowRunnerConfig.Source.Type.CLASSPATH.equals(config.source().type())) {
           final ClassLoader cl = Thread.currentThread().getContextClassLoader();
           //cl.getResources()
        }
    }

}
