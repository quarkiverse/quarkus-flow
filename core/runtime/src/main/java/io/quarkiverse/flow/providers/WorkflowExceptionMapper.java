package io.quarkiverse.flow.providers;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import io.serverlessworkflow.impl.WorkflowError;
import io.serverlessworkflow.impl.WorkflowException;

@Provider
@Priority(Priorities.USER)
public class WorkflowExceptionMapper implements ExceptionMapper<WorkflowException> {

    @Override
    public Response toResponse(WorkflowException exception) {
        final WorkflowError error = exception.getWorkflowError();
        return Response.status(error.status())
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(error)
                .build();
    }
}
