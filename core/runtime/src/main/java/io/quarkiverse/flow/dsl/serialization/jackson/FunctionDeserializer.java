package io.quarkiverse.flow.dsl.serialization.jackson;

import java.io.IOException;
import java.lang.invoke.SerializedLambda;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import io.quarkiverse.flow.dsl.types.utils.ReflectionUtils;

public class FunctionDeserializer<T> extends JsonDeserializer<T> {

    private final Class<T> objectClass;

    public FunctionDeserializer(Class<T> objectClass) {
        this.objectClass = objectClass;
    }

    @Override
    public T deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        try {
            return objectClass.cast(
                    ReflectionUtils.functionFromSerialized(p.readValueAs(SerializedLambda.class)));
        } catch (ReflectiveOperationException e) {
            throw new IOException(e);
        }
    }
}
