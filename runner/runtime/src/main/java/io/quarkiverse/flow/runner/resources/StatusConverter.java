package io.quarkiverse.flow.runner.resources;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ext.ParamConverter;

import io.serverlessworkflow.impl.WorkflowStatus;

class StatusConverter implements ParamConverter<WorkflowStatus> {
    @Override
    public WorkflowStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            WorkflowStatus status = WorkflowStatus.valueOf(value.toUpperCase(Locale.ROOT));
            Set<WorkflowStatus> validStatuses = validStatuses();
            if (!validStatuses.isEmpty() && !validStatuses.contains(status)) {
                throw new BadRequestException("Invalid status value: '" + value
                        + "'. Valid values are : " + validStatuses);
            }
            return status;
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid status value: '" + value
                    + "'. Valid values are : " + Arrays.toString(WorkflowStatus.values()));
        }
    }

    @Override
    public String toString(WorkflowStatus value) {
        return value == null ? null : value.name();
    }

    protected Set<WorkflowStatus> validStatuses() {
        return Set.of();
    }

}
