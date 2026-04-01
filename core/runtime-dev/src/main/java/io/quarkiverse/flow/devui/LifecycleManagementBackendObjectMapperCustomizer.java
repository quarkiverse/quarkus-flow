package io.quarkiverse.flow.devui;

import jakarta.inject.Singleton;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import io.quarkus.jackson.ObjectMapperCustomizer;

@Singleton
public class LifecycleManagementBackendObjectMapperCustomizer implements ObjectMapperCustomizer {

    @Override
    public void customize(ObjectMapper objectMapper) {
        SimpleModule simpleModule = new SimpleModule();
        simpleModule.addSerializer(FlowInstance.class, new FlowInstanceSerializer());
        simpleModule.addDeserializer(FlowInstance.class, new FlowInstanceDeserializer());
        objectMapper.registerModule(simpleModule);
    }
}
