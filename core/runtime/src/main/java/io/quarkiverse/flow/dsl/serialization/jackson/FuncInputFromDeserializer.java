package io.quarkiverse.flow.dsl.serialization.jackson;

import java.io.IOException;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;

import io.serverlessworkflow.api.types.InputFrom;
import io.serverlessworkflow.api.types.jackson.InputFromDeserializer;

public class FuncInputFromDeserializer extends InputFromDeserializer {

    @Override
    public InputFrom deserialize(JsonParser p, DeserializationContext ctxt)
            throws IOException, JacksonException {
        return SerializationUtils.deserializeFilterClass(
                p, ctxt, f -> new InputFrom().withObject(f), InputFrom.class);
    }
}
