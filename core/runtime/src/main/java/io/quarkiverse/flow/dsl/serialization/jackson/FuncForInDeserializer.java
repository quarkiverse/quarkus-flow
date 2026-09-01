package io.quarkiverse.flow.dsl.serialization.jackson;

import java.io.IOException;
import java.util.ArrayList;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import io.serverlessworkflow.api.types.ForIn;

public class FuncForInDeserializer extends JsonDeserializer<ForIn> {

    @Override
    public ForIn deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = ctxt.readTree(p);
        if (node.isTextual()) {
            return new ForIn().withForInExpression(node.textValue());
        } else if (node.isArray()) {
            try {
                return new ForIn()
                        .withForInInlineArray(SerializationUtils.deserializeCollection(node, ctxt, new ArrayList<>()));
            } catch (ReflectiveOperationException e) {
                throw new IOException(e);
            }
        } else {
            throw new IOException("Unable to deserialize ForIn structure from json " + node);
        }
    }
}
