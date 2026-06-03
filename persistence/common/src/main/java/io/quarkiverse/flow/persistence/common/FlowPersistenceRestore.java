package io.quarkiverse.flow.persistence.common;

import static io.quarkiverse.flow.persistence.common.FlowPersistenceUtils.NO_PERSISTENCE_WARN_MSG;
import static io.quarkiverse.flow.persistence.common.FlowPersistenceUtils.excludedIds;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Stream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
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
    Instance<PersistenceInstanceHandlers> handlers;

    @Inject
    WorkflowApplication application;

    @Inject
    FlowPersistenceConfig config;

    void restoreInstances(@Observes WorkflowApplicationReady event) {
        if (handlers.isUnsatisfied()) {
            LOG.warn(NO_PERSISTENCE_WARN_MSG);
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

            try (Stream<WorkflowInstance> stream = handlers.get().reader().scanAll(def)) {
                stream.forEach(instance -> {
                    LOG.debug("Restoring workflow instance: {} with WorkflowInstance.status(): {}", instance.id(),
                            instance.status());
                    instance.start();
                });
            }
        }
    }
}
