package io.quarkiverse.flow.runner.resources;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import jakarta.ws.rs.ext.ParamConverter;
import jakarta.ws.rs.ext.ParamConverterProvider;
import jakarta.ws.rs.ext.Provider;

import io.serverlessworkflow.impl.WorkflowStatus;

@Provider
public class WorkflowStatusParamConverterProvider implements ParamConverterProvider {

    @Override
    @SuppressWarnings("unchecked")
    public <T> ParamConverter<T> getConverter(Class<T> rawType, Type genericType, Annotation[] annotations) {
        if (rawType == WorkflowStatus.class) {
            for (Annotation annotation : annotations) {
                if (annotation.annotationType().equals(ActiveStatus.class)) {
                    return (ParamConverter<T>) new ActiveStatusConverter();
                }
            }
            return (ParamConverter<T>) new StatusConverter();
        }
        return null;
    }
}
