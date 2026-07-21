package io.quarkiverse.flow.dsl.serialization.jackson;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import io.serverlessworkflow.api.types.FunctionArguments;

public class FunctionArgumentsDeserializer extends JsonDeserializer<FunctionArguments> {

    @Override
    public FunctionArguments deserialize(JsonParser p, DeserializationContext ctxt)
            throws IOException {
        try {
            FunctionArguments result = new FunctionArguments();
            SerializationUtils.deserializeMap(p, ctxt, result.getAdditionalProperties());
            return result;
        } catch (ReflectiveOperationException e) {
            throw new IOException(e);
        }
    }
}
