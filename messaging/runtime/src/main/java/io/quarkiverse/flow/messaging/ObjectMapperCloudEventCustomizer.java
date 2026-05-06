package io.quarkiverse.flow.messaging;

import jakarta.enterprise.context.ApplicationScoped;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.cloudevents.jackson.JsonFormat;
import io.quarkus.jackson.ObjectMapperCustomizer;

@ApplicationScoped
public class ObjectMapperCloudEventCustomizer implements ObjectMapperCustomizer {

    @Override
    public void customize(ObjectMapper objectMapper) {
        objectMapper.registerModule(JsonFormat.getCloudEventJacksonModule());
    }
}
