package io.quarkiverse.flow.dsl.serialization.jackson;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import io.serverlessworkflow.api.types.ForIn;

public class ForInSerializer extends JsonSerializer<ForIn> {

    @Override
    public void serialize(ForIn value, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {
        SerializationUtils.serializeMap(gen, value.getAdditionalProperties());
    }
}
