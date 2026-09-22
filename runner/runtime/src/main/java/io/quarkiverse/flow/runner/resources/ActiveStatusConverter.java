package io.quarkiverse.flow.runner.resources;

import java.util.EnumSet;
import java.util.Set;

import io.serverlessworkflow.impl.WorkflowStatus;

class ActiveStatusConverter extends StatusConverter {

    static final Set<WorkflowStatus> ACTIVE_STATUSES = EnumSet.of(
            WorkflowStatus.SUSPENDED, WorkflowStatus.RUNNING, WorkflowStatus.WAITING, WorkflowStatus.PENDING);

    @Override
    protected Set<WorkflowStatus> validStatuses() {
        return ACTIVE_STATUSES;
    }
}
