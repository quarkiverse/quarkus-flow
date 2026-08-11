
package io.quarkiverse.flow.dsl.serialization.jackson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import jakarta.validation.ConstraintViolationException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;

import io.serverlessworkflow.api.types.ForIn;
import io.serverlessworkflow.api.types.In;

/*
 * This class should be removed when https://github.com/open-workflow-specification/sdk-java/issues/1613 is fixed
 */
public class InDeserializerHack
        extends JsonDeserializer<In> {
    @Override
    public In deserialize(JsonParser parser, DeserializationContext dctx)
            throws IOException {
        In in = new In();
        JsonNode node = (JsonNode) parser.readValueAsTree();
        Collection<Exception> exceptions = new ArrayList<>();
        try {
            in.setForInExpression(parser.getCodec().treeToValue(node, String.class));
        } catch (IOException | ConstraintViolationException ex) {
            exceptions.add(ex);
        }
        try {
            in.setForInInlineArray(parser.getCodec().treeAsTokens(node).readValueAs(new TypeReference<List<ForIn>>() {
            }));
        } catch (IOException | ConstraintViolationException ex) {
            exceptions.add(ex);
        }

        if (exceptions.size() == 2) {
            JsonMappingException ex = new JsonMappingException(
                    parser,
                    "Error deserializing class In");
            exceptions.forEach(ex::addSuppressed);
            throw ex;
        }
        return in;
    }

}
