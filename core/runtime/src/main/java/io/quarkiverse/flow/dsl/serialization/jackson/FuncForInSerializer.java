package io.quarkiverse.flow.dsl.serialization.jackson;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;

import io.quarkiverse.flow.dsl.types.utils.ForTaskFunction;
import io.serverlessworkflow.api.types.ForIn;
import io.serverlessworkflow.api.types.jackson.ForInSerializer;

public class FuncForInSerializer extends ForInSerializer {

    @Override
    public void serialize(ForIn value, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {
        boolean collectionFunction = ForTaskFunction.hasCollectionFunction(value);
        if (collectionFunction) {
            SerializationUtils.serializeCollection(gen, value.getForInInlineArray());
        } else {
            super.serialize(value, gen, serializers);
        }
    }
}
