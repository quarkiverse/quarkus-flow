package io.quarkiverse.flow.customizer;

import jakarta.enterprise.context.ApplicationScoped;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.cloudevents.jackson.JsonFormat;
import io.quarkus.arc.Unremovable;
import io.quarkus.jackson.ObjectMapperCustomizer;

@ApplicationScoped
@Unremovable
public class ObjectMapperCloudEventCustomizer implements ObjectMapperCustomizer {

    @Override
    public void customize(ObjectMapper objectMapper) {
        objectMapper.registerModule(JsonFormat.getCloudEventJacksonModule());
    }
}
