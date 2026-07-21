package io.quarkiverse.flow.dsl.serialization.jackson;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;

import io.serverlessworkflow.api.types.InputFrom;
import io.serverlessworkflow.api.types.jackson.InputFromSerializer;

public class FuncInputFromSerializer extends InputFromSerializer {

    public void serialize(InputFrom value, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {
        if (SerializationUtils.isFilterSerializable(value.getObject())) {
            SerializationUtils.serializeObjectWithType(gen, value.getObject());
        } else {
            super.serialize(value, gen, serializers);
        }
    }
}
