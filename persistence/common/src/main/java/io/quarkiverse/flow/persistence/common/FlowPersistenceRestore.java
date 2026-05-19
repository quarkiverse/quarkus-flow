package io.quarkiverse.flow.persistence.common;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.flow.internal.WorkflowApplicationReady;
import io.quarkus.arc.properties.IfBuildProperty;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowDefinitionId;
import io.serverlessworkflow.impl.WorkflowInstance;
import io.serverlessworkflow.impl.persistence.PersistenceInstanceHandlers;

@ApplicationScoped
@IfBuildProperty(name = FlowPersistenceConfig.PREFIX + ".autoRestore", enableIfMissing = true, stringValue = "true")
public class FlowPersistenceRestore {

    private static final Logger LOG = LoggerFactory.getLogger(FlowPersistenceRestore.class);
    @Inject
    PersistenceInstanceHandlers handlers;
    @Inject
    WorkflowApplication application;
    @Inject
    FlowPersistenceConfig config;

    void restoreInstances(@Observes WorkflowApplicationReady event) {
        Map<WorkflowDefinitionId, WorkflowDefinition> definitions = application.workflowDefinitions();
        Set<String> excludedWorkflows = config.excludeWorkflows()
                .filter(excluded -> !excluded.isEmpty())
                .<Set<String>> map(HashSet::new)
                .orElse(Collections.emptySet());

        LOG.debug("Restoring workflow instances from persistence, found {} workflow definitions", definitions.size());

        for (WorkflowDefinition def : definitions.values()) {
            String workflowId = WorkflowDefinitionId.of(def.workflow()).toString(":");
            if (excludedWorkflows.contains(workflowId)) {
                LOG.debug("Skipping restoration for excluded workflow: {}", workflowId);
                continue;
            }

            try (Stream<WorkflowInstance> stream = handlers.reader().scanAll(def)) {
                stream.forEach(instance -> {
                    LOG.debug("Restoring workflow instance: {} with WorkflowInstance.status(): {}", instance.id(),
                            instance.status());
                    instance.start();
                });
            }
        }
    }
}