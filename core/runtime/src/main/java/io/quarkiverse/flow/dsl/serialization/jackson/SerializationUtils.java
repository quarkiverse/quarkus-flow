package io.quarkiverse.flow.dsl.serialization.jackson;

import java.io.IOException;
import java.lang.invoke.SerializedLambda;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Function;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BigIntegerNode;
import com.fasterxml.jackson.databind.node.BinaryNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.FloatNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ShortNode;
import com.fasterxml.jackson.databind.node.TextNode;

import io.quarkiverse.flow.dsl.types.FilterSerializable;
import io.quarkiverse.flow.dsl.types.FunctionObject;
import io.quarkiverse.flow.dsl.types.utils.ReflectionUtils;
import io.serverlessworkflow.serialization.DeserializeHelper;

class SerializationUtils {

    private SerializationUtils() {
    }

    private static final String TYPE = "type";
    private static final String VALUE = "value";

    private static final List<String> ALLOWED_PACKAGE_PREFIXES = List.of(
            "io.quarkiverse.flow.",
            "io.serverlessworkflow.",
            "io.quarkus.",
            "java.util.");

    static boolean isAllowedType(String className) {
        for (String prefix : ALLOWED_PACKAGE_PREFIXES) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    public static void serializeObjectWithType(JsonGenerator gen, Object value) throws IOException {
        if (value == null) {
            gen.writeNull();
        } else if (value instanceof Boolean bool) {
            gen.writeBoolean(bool);
        } else if (value instanceof String s) {
            gen.writeString(s);
        } else if (value instanceof Short num) {
            gen.writeNumber(num);
        } else if (value instanceof Integer num) {
            gen.writeNumber(num);
        } else if (value instanceof Long num) {
            gen.writeNumber(num);
        } else if (value instanceof Float num) {
            gen.writeNumber(num);
        } else if (value instanceof Double num) {
            gen.writeNumber(num);
        } else if (value instanceof BigDecimal num) {
            gen.writeNumber(num);
        } else if (value instanceof BigInteger num) {
            gen.writeNumber(num);
        } else if (value instanceof Byte num) {
            gen.writeNumber(num);
        } else if (value instanceof byte[] bytes) {
            gen.writeBinary(bytes);
        } else if (value instanceof Character c) {
            gen.writeRaw(c);
        } else {
            gen.writeStartObject();
            if (value instanceof FunctionObject) {
                gen.writeStringField(TYPE, SerializedLambda.class.getName());
                try {
                    gen.writeObjectField(VALUE, ReflectionUtils.serializedLambda(value));
                } catch (ReflectiveOperationException e) {
                    throw new IOException(e);
                }
            } else {
                gen.writeStringField(TYPE, value.getClass().getName());
                if (value instanceof Optional optional) {
                    writeOptionalWithType(gen, optional);
                } else if (value instanceof Map map) {
                    serializeMap(gen, map);
                } else if (value instanceof Collection col) {
                    serializeCollection(gen, col);
                } else {
                    gen.writeObjectField(VALUE, value);
                }
            }
            gen.writeEndObject();
        }
    }

    public static Object deserializeObjectWithType(DeserializationContext ctxt, JsonNode jsonNode)
            throws IOException, ReflectiveOperationException {
        if (jsonNode instanceof NullNode) {
            return null;
        } else if (jsonNode instanceof ObjectNode objectNode) {
            if (!objectNode.has(TYPE)) {
                return ctxt.readTreeAsValue(objectNode, Object.class);
            }
            final String className = objectNode.get(TYPE).asText();
            if ("null".equals(className)) {
                return null;
            }
            if (Optional.class.getName().equals(className)) {
                return readOptionalWithType(ctxt, objectNode.has(VALUE) ? objectNode.get(VALUE) : null);
            }

            if (!objectNode.has(VALUE)) {
                return ctxt.readTreeAsValue(objectNode, Object.class);
            }
            if (SerializedLambda.class.getName().equals(className)) {
                return ReflectionUtils
                        .functionFromSerialized(ctxt.readTreeAsValue(objectNode.get(VALUE), SerializedLambda.class));
            }
            if (Class.class.getName().equals(className)) {
                return ReflectionUtils.loadClass(objectNode.get(VALUE).asText());
            }
            if (isAllowedType(className)) {
                Class<?> clazz = ReflectionUtils.loadClass(className);
                if (Map.class.isAssignableFrom(clazz)) {
                    return deserializeMap(objectNode.get(VALUE), ctxt,
                            clazz.asSubclass(Map.class).getConstructor().newInstance());
                } else if (Collection.class.isAssignableFrom(clazz)) {
                    return deserializeCollection(objectNode.get(VALUE), ctxt,
                            clazz.asSubclass(Collection.class).getConstructor().newInstance());
                } else {
                    return ctxt.readTreeAsValue(objectNode.get(VALUE), clazz);
                }
            }
            return ctxt.readTreeAsValue(objectNode.get(VALUE), Object.class);
        } else if (jsonNode instanceof TextNode text) {
            return text.textValue();
        } else if (jsonNode instanceof BooleanNode bool) {
            return bool.booleanValue();
        } else if (jsonNode instanceof IntNode num) {
            return num.intValue();
        } else if (jsonNode instanceof LongNode num) {
            return num.longValue();
        } else if (jsonNode instanceof ShortNode num) {
            return num.shortValue();
        } else if (jsonNode instanceof FloatNode num) {
            return num.longValue();
        } else if (jsonNode instanceof DoubleNode num) {
            return num.doubleValue();
        } else if (jsonNode instanceof BigIntegerNode num) {
            return num.bigIntegerValue();
        } else if (jsonNode instanceof DecimalNode num) {
            return num.decimalValue();
        } else if (jsonNode instanceof BinaryNode bytes) {
            return bytes.binaryValue();
        } else {
            return ctxt.readTreeAsValue(jsonNode, Object.class);
        }
    }

    public static void writeOptionalWithType(JsonGenerator gen, Optional<?> optional)
            throws IOException {
        if (optional.isPresent()) {
            gen.writeFieldName(VALUE);
            serializeObjectWithType(gen, optional.orElseThrow());
        }
    }

    public static Optional<?> readOptionalWithType(DeserializationContext ctxt, JsonNode objectNode)
            throws IOException, ReflectiveOperationException {
        return objectNode == null
                ? Optional.empty()
                : Optional.ofNullable(deserializeObjectWithType(ctxt, objectNode));
    }

    public static void serializeMap(JsonGenerator gen, Map<String, Object> map) throws IOException {
        gen.writeStartObject();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            gen.writeFieldName(entry.getKey());
            SerializationUtils.serializeObjectWithType(gen, entry.getValue());
        }
        gen.writeEndObject();
    }

    public static void deserializeMap(
            JsonParser p, DeserializationContext ctxt, Map<String, Object> map)
            throws IOException, ReflectiveOperationException {
        deserializeMap(ctxt.readTree(p), ctxt, map);

    }

    public static Map<String, Object> deserializeMap(
            JsonNode node, DeserializationContext ctxt, Map<String, Object> map)
            throws IOException, ReflectiveOperationException {
        for (Entry<String, JsonNode> item : node.properties()) {
            map.put(item.getKey(), deserializeObjectWithType(ctxt, item.getValue()));
        }
        return map;
    }

    public static void serializeCollection(JsonGenerator gen, Collection<Object> col) throws IOException {
        gen.writeStartArray();
        for (Object item : col) {
            serializeObjectWithType(gen, item);
        }
        gen.writeEndArray();
    }

    public static <T extends Collection<Object>> T deserializeCollection(JsonNode jsonNode, DeserializationContext ctxt, T col)
            throws IOException, ReflectiveOperationException {
        for (JsonNode item : jsonNode) {
            col.add(deserializeObjectWithType(ctxt, item));
        }
        return col;
    }

    public static <T> T deserializeFilterClass(
            JsonParser p,
            DeserializationContext ctxt,
            Function<FilterSerializable, T> setter,
            Class<T> objectClass)
            throws IOException {
        TreeNode treeNode = p.readValueAsTree();
        if (treeNode instanceof ObjectNode node && SerializationUtils.hasType(node)) {
            try {
                return setter.apply(
                        (FilterSerializable) SerializationUtils.deserializeObjectWithType(ctxt, node));
            } catch (ReflectiveOperationException e) {
                throw new IOException(e);
            }
        } else {
            return DeserializeHelper.deserializeOneOf(
                    treeNode, p, objectClass, List.of(String.class, Object.class));
        }
    }

    public static boolean isFilterSerializable(Object object) {
        return object instanceof FilterSerializable;
    }

    public static boolean hasType(ObjectNode node) {
        return node.has(TYPE);
    }
}
