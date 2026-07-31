package io.quarkiverse.flow.langchain4j.deployment;

import static java.lang.reflect.Modifier.isStatic;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.PrimitiveType;
import org.jboss.jandex.Type;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import dev.langchain4j.agentic.declarative.LoopCounter;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.service.MemoryId;
import io.serverlessworkflow.impl.jackson.JsonUtils;

/**
 * Build-time utility to derive a JSON Schema for a workflow's input from a LangChain4j agent method signature.
 * <p>
 * This implementation uses Jandex APIs instead of reflection, allowing schema generation at build-time.
 */
final class JandexMethodInputJsonSchema {

    private static final DotName MEMORY_ID = DotName.createSimple(MemoryId.class.getName());
    private static final DotName AGENTIC_SCOPE = DotName.createSimple(AgenticScope.class.getName());
    private static final DotName LOOP_COUNTER = DotName.createSimple(LoopCounter.class.getName());

    private static final DotName STRING = DotName.createSimple(String.class.getName());
    private static final DotName CHAR_SEQUENCE = DotName.createSimple(CharSequence.class.getName());
    private static final DotName BOOLEAN = DotName.createSimple(Boolean.class.getName());
    private static final DotName NUMBER = DotName.createSimple(Number.class.getName());
    private static final DotName MAP = DotName.createSimple(Map.class.getName());
    private static final DotName COLLECTION = DotName.createSimple(Collection.class.getName());

    private static final java.util.Set<DotName> BOXED_NUMBER_TYPES = java.util.Set.of(
            DotName.createSimple("java.lang.Byte"),
            DotName.createSimple("java.lang.Short"),
            DotName.createSimple("java.lang.Integer"),
            DotName.createSimple("java.lang.Long"),
            DotName.createSimple("java.lang.Float"),
            DotName.createSimple("java.lang.Double"));

    private JandexMethodInputJsonSchema() {
    }

    /**
     * Builds a JSON Schema (Draft-7-ish) for the method parameters.
     * Root is always an object with properties matching parameter names.
     *
     * @param index the Jandex index for type inspection
     * @param method the agent method
     * @return ObjectNode representing the JSON schema, or null if no parameters
     */
    public static ObjectNode generateSchemaNode(IndexView index, MethodInfo method) {
        List<Type> paramTypes = method.parameterTypes();

        if (paramTypes.isEmpty()) {
            return null;
        }

        ObjectNode root = JsonUtils.object();
        root.put("type", "object");

        ObjectNode properties = JsonUtils.object();
        ArrayNode requiredArray = JsonUtils.array();

        for (int i = 0; i < paramTypes.size(); i++) {
            Type paramType = paramTypes.get(i);

            // Skip framework parameters
            if (paramType.kind() == Type.Kind.CLASS) {
                DotName typeName = paramType.name();
                if (typeName.equals(AGENTIC_SCOPE)) {
                    continue;
                }
            }

            // Skip parameters with @MemoryId or @LoopCounter annotations
            if (hasParameterAnnotation(method, i, MEMORY_ID) || hasParameterAnnotation(method, i, LOOP_COUNTER)) {
                continue;
            }

            // Get parameter name from Jandex, fallback to generic name if not available
            String paramName = method.parameterName(i);
            if (paramName == null || paramName.isEmpty()) {
                paramName = "arg" + i;
            }

            String jsonType = mapJavaTypeToJsonType(index, paramType);
            ObjectNode propNode = buildPropertySchema(index, paramType, true);
            propNode.put("type", jsonType);

            properties.set(paramName, propNode);
            requiredArray.add(paramName);
        }

        if (properties.isEmpty()) {
            return null;
        }

        root.set("properties", properties);
        root.set("required", requiredArray);
        root.put("$schema", "http://json-schema.org/draft-07/schema#");

        return root;
    }

    /**
     * Check if a method parameter has a specific annotation.
     */
    private static boolean hasParameterAnnotation(MethodInfo method, int paramIndex, DotName annotation) {
        for (AnnotationInstance ann : method.annotations()) {
            if (ann.name().equals(annotation) && ann.target().kind() == AnnotationTarget.Kind.METHOD_PARAMETER) {
                if (ann.target().asMethodParameter().position() == paramIndex) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Main entry for property schema generation.
     *
     * @param index the Jandex index
     * @param type type of the property
     * @param expandPojoOrRecord whether we are allowed to expand "one level deeper" for POJO-like classes
     */
    private static ObjectNode buildPropertySchema(IndexView index, Type type, boolean expandPojoOrRecord) {
        ObjectNode node = JsonUtils.object();

        if (type.kind() == Type.Kind.PRIMITIVE) {
            node.put("type", mapPrimitiveToJsonType(type.asPrimitiveType()));
            return node;
        }

        // Array - check before the CLASS check since arrays are not CLASS kind
        if (type.kind() == Type.Kind.ARRAY) {
            node.put("type", "array");
            ObjectNode items = JsonUtils.object();
            items.put("type", mapJavaTypeToJsonType(index, type.asArrayType().constituent()));
            node.set("items", items);
            return node;
        }

        // Parameterized types (e.g., List<String>, Map<K,V>)
        if (type.kind() == Type.Kind.PARAMETERIZED_TYPE) {
            DotName rawTypeName = type.name();
            ClassInfo rawTypeInfo = index.getClassByName(rawTypeName);

            // Check if it's a Collection or Map
            // Note: rawTypeInfo might be null if not indexed, so check name directly too
            if (rawTypeInfo != null && isAssignableFrom(index, rawTypeName, COLLECTION)) {
                node.put("type", "array");
                ObjectNode items = JsonUtils.object();
                items.put("type", "object");
                node.set("items", items);
                return node;
            }
            if (rawTypeInfo != null && isAssignableFrom(index, rawTypeName, MAP)) {
                node.put("type", "object");
                ObjectNode additionalProps = JsonUtils.object();
                additionalProps.put("type", "object");
                node.set("additionalProperties", additionalProps);
                return node;
            }

            // Fallback: check by name if not indexed
            String typeNameStr = rawTypeName.toString();
            if (typeNameStr.equals("java.util.List") ||
                    typeNameStr.equals("java.util.ArrayList") ||
                    typeNameStr.equals("java.util.Set") ||
                    typeNameStr.equals("java.util.Collection")) {
                node.put("type", "array");
                ObjectNode items = JsonUtils.object();
                items.put("type", "object");
                node.set("items", items);
                return node;
            }
            if (typeNameStr.equals("java.util.Map") || typeNameStr.equals("java.util.HashMap")) {
                node.put("type", "object");
                ObjectNode additionalProps = JsonUtils.object();
                additionalProps.put("type", "object");
                node.set("additionalProperties", additionalProps);
                return node;
            }

            // Other parameterized types
            node.put("type", "object");
            return node;
        }

        if (type.kind() != Type.Kind.CLASS) {
            // Wildcards, type variables, etc.
            node.put("type", "object");
            return node;
        }

        DotName typeName = type.name();
        ClassInfo classInfo = index.getClassByName(typeName);

        // Enum
        if (classInfo != null && classInfo.isEnum()) {
            node.put("type", "string");
            ArrayNode enumValues = JsonUtils.array();
            for (FieldInfo field : classInfo.fields()) {
                if (field.isEnumConstant()) {
                    enumValues.add(field.name());
                }
            }
            node.set("enum", enumValues);
            return node;
        }

        // Collection
        if (isAssignableFrom(index, typeName, COLLECTION)) {
            node.put("type", "array");
            ObjectNode items = JsonUtils.object();
            items.put("type", "object");
            node.set("items", items);
            return node;
        }

        // Map
        if (isAssignableFrom(index, typeName, MAP)) {
            node.put("type", "object");
            ObjectNode additionalProps = JsonUtils.object();
            additionalProps.put("type", "object");
            node.set("additionalProperties", additionalProps);
            return node;
        }

        // POJO expansion
        if (expandPojoOrRecord && isPojoLike(index, typeName)) {
            return buildPojoSchema(index, classInfo);
        }

        node.put("type", mapJavaTypeToJsonType(index, type));
        return node;
    }

    /**
     * Build a one-level object schema for records or POJO-ish classes.
     * Nested POJOs are not expanded recursively; they become type: "object".
     */
    private static ObjectNode buildPojoSchema(IndexView index, ClassInfo pojoType) {
        ObjectNode root = JsonUtils.object();
        root.put("type", "object");

        ObjectNode properties = JsonUtils.object();
        ArrayNode required = JsonUtils.array();

        for (FieldInfo field : pojoType.fields()) {
            if (isStatic(field.flags())) {
                continue;
            }
            String fieldName = field.name();
            Type fieldType = field.type();

            ObjectNode fieldSchema = buildPropertySchema(index, fieldType, false);
            properties.set(fieldName, fieldSchema);
            required.add(fieldName);
        }

        if (!properties.isEmpty()) {
            root.set("properties", properties);
            root.set("required", required);
        }

        return root;
    }

    /**
     * Decide if a class is "POJO-like enough" to expand one level.
     */
    private static boolean isPojoLike(IndexView index, DotName typeName) {
        if (typeName.equals(STRING) || isAssignableFrom(index, typeName, CHAR_SEQUENCE)) {
            return false;
        }
        if (typeName.equals(BOOLEAN)) {
            return false;
        }
        if (BOXED_NUMBER_TYPES.contains(typeName) || isAssignableFrom(index, typeName, NUMBER)) {
            return false;
        }
        if (isAssignableFrom(index, typeName, MAP)) {
            return false;
        }
        if (isAssignableFrom(index, typeName, COLLECTION)) {
            return false;
        }

        ClassInfo classInfo = index.getClassByName(typeName);
        return classInfo != null && !classInfo.isEnum();
    }

    /**
     * Type-mapping helper for JSON Schema types.
     */
    private static String mapJavaTypeToJsonType(IndexView index, Type type) {
        if (type.kind() == Type.Kind.PRIMITIVE) {
            return mapPrimitiveToJsonType(type.asPrimitiveType());
        }

        // Array - check before the CLASS check since arrays are not CLASS kind
        if (type.kind() == Type.Kind.ARRAY) {
            return "array";
        }

        // Parameterized types (e.g., List<String>, Map<K,V>)
        if (type.kind() == Type.Kind.PARAMETERIZED_TYPE) {
            DotName rawTypeName = type.name();
            ClassInfo rawTypeInfo = index.getClassByName(rawTypeName);

            if (rawTypeInfo != null && isAssignableFrom(index, rawTypeName, COLLECTION)) {
                return "array";
            }
            if (rawTypeInfo != null && isAssignableFrom(index, rawTypeName, MAP)) {
                return "object";
            }

            // Fallback: check by name if not indexed
            String typeNameStr = rawTypeName.toString();
            if (typeNameStr.equals("java.util.List") ||
                    typeNameStr.equals("java.util.ArrayList") ||
                    typeNameStr.equals("java.util.Set") ||
                    typeNameStr.equals("java.util.Collection")) {
                return "array";
            }
            if (typeNameStr.equals("java.util.Map") || typeNameStr.equals("java.util.HashMap")) {
                return "object";
            }

            return "object";
        }

        if (type.kind() != Type.Kind.CLASS) {
            return "object";
        }

        DotName typeName = type.name();

        if (typeName.equals(STRING) || isAssignableFrom(index, typeName, CHAR_SEQUENCE)) {
            return "string";
        }

        if (typeName.equals(BOOLEAN)) {
            return "boolean";
        }

        if (BOXED_NUMBER_TYPES.contains(typeName) || isAssignableFrom(index, typeName, NUMBER)) {
            return "number";
        }

        if (isAssignableFrom(index, typeName, COLLECTION)) {
            return "array";
        }

        if (isAssignableFrom(index, typeName, MAP)) {
            return "object";
        }

        ClassInfo classInfo = index.getClassByName(typeName);
        if (classInfo != null && classInfo.isEnum()) {
            return "string";
        }

        return "object";
    }

    private static String mapPrimitiveToJsonType(PrimitiveType primitive) {
        return switch (primitive.primitive()) {
            case BOOLEAN -> "boolean";
            case BYTE, SHORT, INT, LONG, FLOAT, DOUBLE -> "number";
            case CHAR -> "string";
        };
    }

    /**
     * Simple check if type is assignable from a given interface/superclass.
     */
    private static boolean isAssignableFrom(IndexView index, DotName type, DotName superType) {
        if (type.equals(superType)) {
            return true;
        }

        ClassInfo classInfo = index.getClassByName(type);
        if (classInfo == null) {
            return false;
        }

        // Check interfaces
        for (Type iface : classInfo.interfaceTypes()) {
            if (iface.name().equals(superType) || isAssignableFrom(index, iface.name(), superType)) {
                return true;
            }
        }

        // Check superclass
        Type superClass = classInfo.superClassType();
        if (superClass != null && !superClass.name().equals(DotName.createSimple("java.lang.Object"))) {
            return isAssignableFrom(index, superClass.name(), superType);
        }

        return false;
    }
}
