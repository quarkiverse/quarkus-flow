package io.quarkiverse.flow.runner.resources;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import jakarta.ws.rs.ext.ParamConverter;
import jakarta.ws.rs.ext.ParamConverterProvider;
import jakarta.ws.rs.ext.Provider;

import io.serverlessworkflow.impl.WorkflowStatus;

/**
 * Converts the {@code status} query parameter into a {@link WorkflowStatus}, case-insensitively,
 * rejecting unknown values with a {@link InvalidStatusFilterException} (mapped to {@code 400 Bad Request}
 * by {@link InvalidStatusFilterExceptionMapper}) instead of the default JAX-RS enum conversion, which
 * would 404 on an unrecognized value.
 */
@Provider
public class WorkflowStatusParamConverterProvider implements ParamConverterProvider {

    @Override
    @SuppressWarnings("unchecked")
    public <T> ParamConverter<T> getConverter(Class<T> rawType, Type genericType, Annotation[] annotations) {
        if (rawType != WorkflowStatus.class) {
            return null;
        }
        return (ParamConverter<T>) new ParamConverter<WorkflowStatus>() {
            @Override
            public WorkflowStatus fromString(String value) {
                if (value == null || value.isBlank()) {
                    return null;
                }
                try {
                    return WorkflowStatus.valueOf(value.toUpperCase());
                } catch (IllegalArgumentException e) {
                    throw new InvalidStatusFilterException("Unknown status value: '" + value
                            + "'. Valid non-terminal values are: PENDING, RUNNING, WAITING, SUSPENDED");
                }
            }

            @Override
            public String toString(WorkflowStatus value) {
                return value == null ? null : value.name();
            }
        };
    }
}
