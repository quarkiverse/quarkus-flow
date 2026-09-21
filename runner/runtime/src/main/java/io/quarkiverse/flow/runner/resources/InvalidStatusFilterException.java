package io.quarkiverse.flow.runner.resources;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

/**
 * Mapped to a {@code 400 Bad Request} response by {@link InvalidStatusFilterExceptionMapper}.
 *
 * <p>
 * Extends {@link WebApplicationException} rather than plain {@link RuntimeException} so that when
 * thrown from {@link WorkflowStatusParamConverterProvider} during conversion of the {@code status}
 * {@code @QueryParam}, it propagates as-is instead of being wrapped into a {@code 404 Not Found} —
 * per the JAX-RS spec, only a {@link WebApplicationException} survives query-param conversion
 * failures unwrapped.
 */
public class InvalidStatusFilterException extends WebApplicationException {

    public InvalidStatusFilterException(String message) {
        super(message, Response.status(Response.Status.BAD_REQUEST).entity(message).build());
    }
}
