package io.quarkiverse.flow.dsl.serialization.jackson;

import java.io.IOException;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;

import io.serverlessworkflow.api.types.OutputAs;
import io.serverlessworkflow.api.types.jackson.OutputAsDeserializer;

public class FuncOutputAsDeserializer extends OutputAsDeserializer {

    @Override
    public OutputAs deserialize(JsonParser p, DeserializationContext ctxt)
            throws IOException, JacksonException {
        return SerializationUtils.deserializeFilterClass(
                p, ctxt, f -> new OutputAs().withObject(f), OutputAs.class);
    }
}
