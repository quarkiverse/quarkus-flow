package io.quarkiverse.flow.langchain4j.schema;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Map;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

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
        if (workflow == null || method == null) {
            return;
        }

        // Do not override an existing schema
        if (workflow.getInput() != null && workflow.getInput().getSchema() != null) {
            return;
        }

        ObjectNode schemaNode = generateSchemaNode(method);
        if (schemaNode == null) {
            return;
        }

        SchemaInline inline = new SchemaInline(schemaNode);
        SchemaUnion union = new SchemaUnion().withSchemaInline(inline);

        Input input = workflow.getInput();
        if (input == null) {
            input = new Input();
            workflow.setInput(input);
        }
        input.setSchema(union);
    }

    /**
     * Builds a Draft-7-ish JSON Schema for the method parameters.
     */
    private static ObjectNode generateSchemaNode(Method method) {
        Parameter[] params = method.getParameters();
        if (params.length == 0) {
            return null;
        }

        // Root schema: type: object
        ObjectNode root = JsonUtils.object();
        root.put("type", "object");

        ObjectNode properties = JsonUtils.object();
        ArrayNode requiredArray = JsonUtils.array();

        for (Parameter parameter : params) {
            // Determine logical name
            String paramName = resolveParameterName(parameter);

            // Determine JSON schema type for this parameter
            String jsonType = mapJavaTypeToJsonType(parameter.getType());

            ObjectNode propNode = JsonUtils.object();
            propNode.put("type", jsonType);

            properties.set(paramName, propNode);
            requiredArray.add(paramName);
        }

        root.set("properties", properties);
        root.set("required", requiredArray);

        return root.put("$schema", "http://json-schema.org/draft-07/schema#");
    }

    private static String resolveParameterName(Parameter parameter) {
        V v = parameter.getAnnotation(V.class);
        if (v != null && !v.value().isEmpty()) {
            return v.value();
        }

        if (parameter.isNamePresent()) {
            return parameter.getName();
        }

        return "arg" + parameter.getName();
    }

    /**
     * Very small type-mapping helper.
     * We don't need to be perfect here; Dev UI just needs reasonable hints.
     */
    private static String mapJavaTypeToJsonType(Class<?> clazz) {
        if (clazz == String.class || CharSequence.class.isAssignableFrom(clazz)) {
            return "string";
        }

        if (clazz == boolean.class || clazz == Boolean.class) {
            return "boolean";
        }

        if (Number.class.isAssignableFrom(clazz) || clazz.isPrimitive() && clazz != char.class) {
            return "number";
        }

        if (clazz.isArray() || java.util.Collection.class.isAssignableFrom(clazz)) {
            return "array";
        }

        if (Map.class.isAssignableFrom(clazz)) {
            return "object";
        }

        // For unknown or POJO types, object is most reasonable
        return "object";
    }
}
