package io.quarkiverse.flow.langchain4j.schema;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import dev.langchain4j.agentic.internal.AgentUtil;
import dev.langchain4j.service.V;
import io.serverlessworkflow.api.types.Input;
import io.serverlessworkflow.api.types.SchemaInline;
import io.serverlessworkflow.api.types.SchemaUnion;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.jackson.JsonUtils;

/**
 * Utility to derive a JSON Schema for a workflow's input
 * from a LangChain4j agent method signature.
 * <p>
 * The generated schema is attached as an inline JSON Schema
 * to {@link Workflow#getInput()}.
 */
public final class MethodInputJsonSchema {

    private MethodInputJsonSchema() {
    }

    /**
     * Adds an inline JSON Schema to the workflow input if none is present.
     * The schema is derived from the LC4J agent method and its invoker metadata.
     */
    public static void applySchemaIfAbsent(Workflow workflow, Method method) {
        if (workflow == null || method == null)
            return;

        if (workflow.getInput() != null && workflow.getInput().getSchema() != null)
            return;

        ObjectNode schemaNode = generateSchemaNode(method);
        if (schemaNode == null)
            return;

        workflow.setInput(new Input().withSchema(new SchemaUnion().withSchemaInline(new SchemaInline(schemaNode))));
    }

    /**
     * Builds a JSON Schema (Draft-7-ish) for the method parameters.
     * Root is always an object with properties matching parameter names.
     */
    private static ObjectNode generateSchemaNode(Method method) {
        final List<AgentUtil.AgentArgument> args = AgentUtil.argumentsFromMethod(method);

        if (args.isEmpty()) {
            return null;
        }

        ObjectNode root = JsonUtils.object();
        root.put("type", "object");

        ObjectNode properties = JsonUtils.object();
        ArrayNode requiredArray = JsonUtils.array();

        for (AgentUtil.AgentArgument arg : args) {
            String logicalName = arg.name();

            if (logicalName.equals(AgentUtil.MEMORY_ID_ARG_NAME)
                    || logicalName.equals(AgentUtil.AGENTIC_SCOPE_ARG_NAME)
                    || logicalName.equals(AgentUtil.LOOP_COUNTER_ARG_NAME)) {
                continue;
            }

            String jsonType = mapJavaTypeToJsonType(arg.type());

            ObjectNode propNode = buildPropertySchema(arg.type(), true);
            propNode.put("type", jsonType);

            properties.set(logicalName, propNode);
            requiredArray.add(logicalName);
        }

        root.set("properties", properties);
        root.set("required", requiredArray);
        root.put("$schema", "http://json-schema.org/draft-07/schema#");

        return root;
    }

    private static String resolveParameterName(Parameter parameter) {
        V v = parameter.getAnnotation(V.class);
        if (v != null && !v.value().isEmpty()) {
            return v.value();
        }
        return parameter.getName();
    }

    /**
     * Main entry for property schema generation.
     *
     * @param type type of the property
     * @param expandPojoOrRecord whether we are allowed to expand “one level deeper”
     *        for POJO-like classes / records
     */
    private static ObjectNode buildPropertySchema(Class<?> type, boolean expandPojoOrRecord) {
        ObjectNode node = JsonUtils.object();

        if (type.isEnum()) {
            node.put("type", "string");
            ArrayNode enumValues = JsonUtils.array();
            for (Object constant : type.getEnumConstants()) {
                enumValues.add(constant.toString());
            }
            node.set("enum", enumValues);
            return node;
        }

        if (type.isArray()) {
            node.put("type", "array");
            ObjectNode items = JsonUtils.object();
            items.put("type", mapJavaTypeToJsonType(type.getComponentType()));
            node.set("items", items);
            return node;
        }

        if (Collection.class.isAssignableFrom(type)) {
            node.put("type", "array");
            ObjectNode items = JsonUtils.object();
            items.put("type", "object");
            node.set("items", items);
            return node;
        }

        if (Map.class.isAssignableFrom(type)) {
            node.put("type", "object");
            ObjectNode additionalProps = JsonUtils.object();
            additionalProps.put("type", "object");
            node.set("additionalProperties", additionalProps);
            return node;
        }

        if (expandPojoOrRecord && isPojoLike(type)) {
            return buildPojoSchema(type);
        }

        node.put("type", mapJavaTypeToJsonType(type));
        return node;
    }

    /**
     * Build a one-level object schema for records or POJO-ish classes.
     * Nested POJOs are not expanded recursively; they become type: "object".
     */
    private static ObjectNode buildPojoSchema(Class<?> pojoType) {
        ObjectNode root = JsonUtils.object();
        root.put("type", "object");

        ObjectNode properties = JsonUtils.object();
        ArrayNode required = JsonUtils.array();

        if (pojoType.isRecord()) {
            for (RecordComponent rc : pojoType.getRecordComponents()) {
                String fieldName = rc.getName();
                Class<?> fieldType = rc.getType();

                ObjectNode fieldSchema = buildPropertySchema(fieldType, false);
                properties.set(fieldName, fieldSchema);
                required.add(fieldName);
            }
        } else {
            Field[] fields = pojoType.getDeclaredFields();
            for (Field field : fields) {
                int mods = field.getModifiers();
                if (java.lang.reflect.Modifier.isStatic(mods)) {
                    continue;
                }
                String fieldName = field.getName();
                Class<?> fieldType = field.getType();

                ObjectNode fieldSchema = buildPropertySchema(fieldType, false);
                properties.set(fieldName, fieldSchema);
                required.add(fieldName);
            }
        }

        if (!properties.isEmpty()) {
            root.set("properties", properties);
            root.set("required", required);
        }

        return root;
    }

    /**
     * Decide if a class is “Pojo-like enough” to expand one level.
     */
    private static boolean isPojoLike(Class<?> clazz) {
        if (clazz.isPrimitive())
            return false;
        if (clazz.isEnum())
            return false;
        if (CharSequence.class.isAssignableFrom(clazz))
            return false;
        if (Number.class.isAssignableFrom(clazz))
            return false;
        if (clazz == Boolean.class)
            return false;
        if (Map.class.isAssignableFrom(clazz))
            return false;
        if (java.util.Collection.class.isAssignableFrom(clazz))
            return false;

        return !clazz.isArray();
    }

    /**
     * Very small type-mapping helper.
     * We don't need to be perfect; Dev UI just needs reasonable hints.
     */
    private static String mapJavaTypeToJsonType(Class<?> clazz) {
        if (clazz == String.class || CharSequence.class.isAssignableFrom(clazz))
            return "string";

        if (clazz == boolean.class || clazz == Boolean.class)
            return "boolean";

        if (Number.class.isAssignableFrom(clazz) || clazz.isPrimitive() && clazz != char.class)
            return "number";

        if (clazz.isArray() || java.util.Collection.class.isAssignableFrom(clazz))
            return "array";

        if (Map.class.isAssignableFrom(clazz))
            return "object";

        if (clazz.isEnum())
            return "string";

        return "object";
    }
}
