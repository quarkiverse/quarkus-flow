package io.quarkiverse.flow.persistence.common;

import static io.quarkiverse.flow.persistence.common.FlowPersistenceUtils.excludedIds;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Stream;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.flow.internal.WorkflowApplicationReadyEvent;
import io.quarkus.arc.Unremovable;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduler;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowDefinitionId;
import io.serverlessworkflow.impl.WorkflowInstance;
import io.serverlessworkflow.impl.persistence.PersistenceInstanceHandlers;

@ApplicationScoped
@Unremovable
public class FlowPersistenceRestore {

    static final String JOB_IDENTITY = "flow-persistence-restore-scan";

    private static final Logger LOG = LoggerFactory.getLogger(FlowPersistenceRestore.class);
    @Inject
    PersistenceInstanceHandlers handlers;
    @Inject
    WorkflowApplication application;
    @Inject
    FlowPersistenceConfig config;
    @Inject
    Scheduler scheduler;

    private volatile boolean scanScheduled = false;

    void restoreInstances(@Observes WorkflowApplicationReadyEvent event) {
        // Check runtime config to see if auto-restore is enabled
        if (!config.autoRestore()) {
            LOG.debug("Auto-restore is disabled, skipping workflow instance restoration");
            return;
        }

        scanAndRestore();

        config.scanInterval().ifPresent(interval -> {
            LOG.info("Scheduling periodic workflow instance recovery scan every {}", interval);
            scheduler.newJob(JOB_IDENTITY)
                    .setInterval(interval.toString())
                    .setConcurrentExecution(Scheduled.ConcurrentExecution.SKIP)
                    .setTask(executionContext -> scanAndRestore())
                    .schedule();
            scanScheduled = true;
        });
    }

    @PreDestroy
    void stop() {
        if (scanScheduled) {
            scheduler.unscheduleJob(JOB_IDENTITY);
        }
    }

    void scanAndRestore() {
        Map<WorkflowDefinitionId, WorkflowDefinition> definitions = application.workflowDefinitions();

        Collection<WorkflowDefinitionId> excludedIds = excludedIds(config.excludeWorkflows());
        LOG.debug("Scanning for workflow instances to restore, found {} workflow definitions", definitions.size());

        for (WorkflowDefinition def : definitions.values()) {
            if (excludedIds.contains(def.id())) {
                LOG.debug("Skipping restoration for excluded workflow: {}", def.id());
                continue;
            }

            try (Stream<WorkflowInstance> stream = handlers.reader().scanAll(def)) {
                stream.forEach(instance -> {
                    if (def.activeInstance(instance.id()).isEmpty()) {
                        LOG.debug("Restoring workflow instance: {} with WorkflowInstance.status(): {}", instance.id(),
                                instance.status());
                        instance.start();
                    } else {
                        LOG.debug("Workflow instance: {} is already active in memory, skipping", instance.id());
                    }
                });
            }
        }
    }
}
