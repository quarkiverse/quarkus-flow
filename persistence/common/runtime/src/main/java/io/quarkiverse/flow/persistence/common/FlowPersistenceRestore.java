package io.quarkiverse.flow.persistence.common;

import static io.quarkiverse.flow.persistence.common.FlowPersistenceUtils.excludedIds;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Stream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.flow.internal.WorkflowApplicationReady;
import io.quarkus.arc.Unremovable;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowDefinitionId;
import io.serverlessworkflow.impl.WorkflowInstance;
import io.serverlessworkflow.impl.persistence.PersistenceInstanceHandlers;

@ApplicationScoped
@Unremovable
public class FlowPersistenceRestore {

    private static final Logger LOG = LoggerFactory.getLogger(FlowPersistenceRestore.class);
    @Inject
    PersistenceInstanceHandlers handlers;
    @Inject
    WorkflowApplication application;
    @Inject
    FlowPersistenceConfig config;

    void restoreInstances(@Observes WorkflowApplicationReady event) {
        // Check runtime config to see if auto-restore is enabled
        if (!config.autoRestore()) {
            LOG.debug("Auto-restore is disabled, skipping workflow instance restoration");
            return;
        }
        Map<WorkflowDefinitionId, WorkflowDefinition> definitions = application.workflowDefinitions();

        Collection<WorkflowDefinitionId> excludedIds = excludedIds(config.excludeWorkflows());
        LOG.debug("Restoring workflow instances from persistence, found {} workflow definitions", definitions.size());

        for (WorkflowDefinition def : definitions.values()) {
            if (excludedIds.contains(def.id())) {
                LOG.debug("Skipping restoration for excluded workflow: {}", def.id());
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
