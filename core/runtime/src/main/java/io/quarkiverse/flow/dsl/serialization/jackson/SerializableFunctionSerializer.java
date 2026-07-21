package io.quarkiverse.flow.dsl.serialization.jackson;

import java.io.IOException;
import java.lang.invoke.SerializedLambda;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import io.quarkiverse.flow.dsl.types.utils.ReflectionUtils;

public class SerializableFunctionSerializer extends JsonSerializer<Object> {

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {
        Optional<SerializedLambda> serializedLambda = ReflectionUtils.serializedFromFunction(value);
        if (serializedLambda.isPresent()) {
            gen.writeObject(serializedLambda.orElseThrow());
        } else {
            gen.writeString(value.toString());
        }
    }
}
