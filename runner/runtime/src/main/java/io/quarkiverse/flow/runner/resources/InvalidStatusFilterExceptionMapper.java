package io.quarkiverse.flow.runner.resources;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class InvalidStatusFilterExceptionMapper implements ExceptionMapper<InvalidStatusFilterException> {

    @Override
    public Response toResponse(InvalidStatusFilterException exception) {
        return Response.status(Response.Status.BAD_REQUEST).entity(exception.getMessage()).build();
    }
}
