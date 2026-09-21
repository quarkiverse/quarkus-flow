package io.quarkiverse.flow.runner.resources;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ext.ParamConverter;
import jakarta.ws.rs.ext.ParamConverterProvider;
import jakarta.ws.rs.ext.Provider;

import io.serverlessworkflow.impl.WorkflowStatus;

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
                    throw new BadRequestException("Unknown status value: '" + value
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
