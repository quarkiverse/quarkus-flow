package io.quarkiverse.flow.dsl.serialization.jackson;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;

import io.serverlessworkflow.api.types.OutputAs;
import io.serverlessworkflow.api.types.jackson.OutputAsSerializer;

public class FuncOutputAsSerializer extends OutputAsSerializer {

    public void serialize(OutputAs value, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {
        if (SerializationUtils.isFilterSerializable(value.getObject())) {
            SerializationUtils.serializeObjectWithType(gen, value.getObject());
        } else {
            super.serialize(value, gen, serializers);
        }
    }
}
