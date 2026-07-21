package io.quarkiverse.flow.dsl.serialization.jackson;

import java.io.IOException;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;

import io.serverlessworkflow.api.types.ExportAs;
import io.serverlessworkflow.api.types.jackson.ExportAsDeserializer;

public class FuncExportAsDeserializer extends ExportAsDeserializer {

    @Override
    public ExportAs deserialize(JsonParser p, DeserializationContext ctxt)
            throws IOException, JacksonException {
        return SerializationUtils.deserializeFilterClass(
                p, ctxt, f -> new ExportAs().withObject(f), ExportAs.class);
    }
}
