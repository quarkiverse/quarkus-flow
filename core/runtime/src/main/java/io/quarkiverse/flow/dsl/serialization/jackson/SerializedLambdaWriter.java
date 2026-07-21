package io.quarkiverse.flow.dsl.serialization.jackson;

import java.io.IOException;
import java.lang.invoke.SerializedLambda;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;

public class SerializedLambdaWriter extends BeanPropertyWriter {

    private static final long serialVersionUID = 1L;

    public SerializedLambdaWriter(BeanPropertyWriter base) {
        super(base);
    }

    @Override
    public void serializeAsField(Object bean, JsonGenerator gen, SerializerProvider prov)
            throws IOException {
        SerializedLambda sl = (SerializedLambda) bean;
        int size = sl.getCapturedArgCount();
        if (size > 0) {
            gen.writeArrayFieldStart(SerializedLambdaDeserializer.CAPTURED_ARGS);
            for (int i = 0; i < size; i++) {
                SerializationUtils.serializeObjectWithType(gen, sl.getCapturedArg(i));
            }
            gen.writeEndArray();
        }
    }
}
