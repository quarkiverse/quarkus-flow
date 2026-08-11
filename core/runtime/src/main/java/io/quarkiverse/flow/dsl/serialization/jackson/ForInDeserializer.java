package io.quarkiverse.flow.dsl.serialization.jackson;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import io.serverlessworkflow.api.types.ForIn;

public class ForInDeserializer extends JsonDeserializer<ForIn> {

    @Override
    public ForIn deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        try {
            ForIn result = new ForIn();
            SerializationUtils.deserializeMap(p, ctxt, result.getAdditionalProperties());
            return result;
        } catch (ReflectiveOperationException e) {
            throw new IOException(e);
        }
    }
}
