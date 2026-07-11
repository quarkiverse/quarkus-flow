package io.quarkiverse.flow.internal;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.arc.Unremovable;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowDefinitionId;

@ApplicationScoped
@Unremovable
public class WorkflowRegistrarService {

    private final Logger LOGGER = LoggerFactory.getLogger(WorkflowRegistrarService.class.getName());

    @Inject
    WorkflowApplication application;

    @Inject
    Event<WorkflowDescriptorRegisteredEvent> event;

    public WorkflowDefinition register(Workflow workflow) {
        LOGGER.debug("Registering workflow {}", WorkflowDefinitionId.of(workflow));
        final WorkflowDefinition definition = application.workflowDefinition(workflow);
        event.fire(new WorkflowDescriptorRegisteredEvent(workflow));
        return definition;
    }

}
