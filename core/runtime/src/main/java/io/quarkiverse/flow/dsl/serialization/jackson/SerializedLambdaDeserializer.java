package io.quarkiverse.flow.dsl.serialization.jackson;

import java.io.IOException;
import java.lang.invoke.SerializedLambda;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.quarkiverse.flow.dsl.types.utils.ReflectionUtils;

public class SerializedLambdaDeserializer extends JsonDeserializer<SerializedLambda> {

    static final String CAPTURING_CLASS = "capturingClass";
    static final String FUNCTIONAL_CLASS = "functionalInterfaceClass";
    static final String FUNCTIONAL_METHOD_NAME = "functionalInterfaceMethodName";
    static final String FUNCTIONAL_METHOD_SIGNATURE = "functionalInterfaceMethodSignature";
    static final String METHOD_KIND = "implMethodKind";
    static final String METHOD_CLASS = "implClass";
    static final String METHOD_NAME = "implMethodName";
    static final String METHOD_SIGNATURE = "implMethodSignature";
    static final String METHOD_TYPE = "instantiatedMethodType";
    static final String CAPTURED_ARGS = "capturedArgs";

    @Override
    public SerializedLambda deserialize(JsonParser p, DeserializationContext ctxt)
            throws IOException {
        TreeNode tree = p.readValueAsTree();

        if (tree instanceof ObjectNode node) {
            try {
                return new SerializedLambda(
                        ReflectionUtils.loadCapturingClass(node.get(CAPTURING_CLASS).asText()),
                        node.get(FUNCTIONAL_CLASS).asText(),
                        node.get(FUNCTIONAL_METHOD_NAME).asText(),
                        node.get(FUNCTIONAL_METHOD_SIGNATURE).asText(),
                        node.get(METHOD_KIND).asInt(),
                        node.get(METHOD_CLASS).asText(),
                        node.get(METHOD_NAME).asText(),
                        node.get(METHOD_SIGNATURE).asText(),
                        node.get(METHOD_TYPE).asText(),
                        fromArray(ctxt, (ArrayNode) node.get(CAPTURED_ARGS)));
            } catch (ReflectiveOperationException ex) {
                throw new IOException("Error unmarshalling SerializedLambda " + node, ex);
            }
        } else {
            throw new IOException(
                    "Node "
                            + tree
                            + " is not an object and therefore cannot be converted into SerializedLambda");
        }
    }

    private Object[] fromArray(DeserializationContext ctxt, ArrayNode node)
            throws IOException, ReflectiveOperationException {
        if (node == null) {
            return new Object[0];
        } else {
            Object[] result = new Object[node.size()];
            for (int i = 0; i < result.length; i++) {
                result[i] = SerializationUtils.deserializeObjectWithType(ctxt, node.get(i));
            }
            return result;
        }
    }
}
