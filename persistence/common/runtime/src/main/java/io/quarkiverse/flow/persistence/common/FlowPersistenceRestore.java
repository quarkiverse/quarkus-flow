package io.quarkiverse.flow.persistence.common;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.runtime.Startup;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowInstance;
import io.serverlessworkflow.impl.persistence.PersistenceInstanceHandlers;

@Startup
@IfBuildProperty(name = "quarkus.flow.persistence.autoRestore", enableIfMissing = true, stringValue = "true")
public class FlowPersistenceRestore {
    @Inject
    PersistenceInstanceHandlers handlers;
    @Inject
    WorkflowApplication application;

    @PostConstruct
    void restoreInstances() {
        application.workflowDefinitions().values()
                .forEach(def -> handlers.reader().scanAll(def).forEach(WorkflowInstance::start));
    }
}
