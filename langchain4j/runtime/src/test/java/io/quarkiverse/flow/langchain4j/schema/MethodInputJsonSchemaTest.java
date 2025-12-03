package io.quarkiverse.flow.langchain4j.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import dev.langchain4j.service.V;
import io.quarkiverse.flow.langchain4j.Agents;
import io.serverlessworkflow.api.types.Document;
import io.serverlessworkflow.api.types.Input;
import io.serverlessworkflow.api.types.SchemaInline;
import io.serverlessworkflow.api.types.SchemaUnion;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.jackson.JsonUtils;

class MethodInputJsonSchemaTest {

    private static java.util.Set<String> toStringSet(ArrayNode arrayNode) {
        java.util.Set<String> set = new java.util.HashSet<>();
        for (JsonNode n : arrayNode) {
            set.add(n.asText());
        }
        return set;
    }

    @Test
    void shouldAttachSchemaForSimpleStringArguments() throws Exception {
        Method write = Agents.StoryCreatorWithConfigurableStyleEditor.class.getMethod(
                "write", String.class, String.class, String.class);

        Workflow workflow = new Workflow().withDocument(new Document().withName("story"));

        MethodInputJsonSchema.applySchemaIfAbsent(workflow, write);

        Input input = workflow.getInput();
        assertThat(input).isNotNull();
        assertThat(input.getSchema()).isNotNull();

        SchemaUnion union = input.getSchema();
        SchemaInline inline = union.getSchemaInline();
        assertThat(inline).isNotNull();

        Object document = inline.getDocument();
        assertThat(document).isInstanceOf(ObjectNode.class);

        ObjectNode schema = (ObjectNode) document;

        // Root-level assertions
        assertThat(schema.get("type").asText()).isEqualTo("object");
        assertThat(schema.get("$schema").asText())
                .isEqualTo("http://json-schema.org/draft-07/schema#");

        ObjectNode properties = (ObjectNode) schema.get("properties");
        ArrayNode required = (ArrayNode) schema.get("required");

        assertThat(properties).isNotNull();
        assertThat(required).isNotNull();

        // Properties should exist with the @V names
        assertThat(properties.get("topic")).isNotNull();
        assertThat(properties.get("style")).isNotNull();
        assertThat(properties.get("audience")).isNotNull();

        // All are strings
        assertThat(properties.get("topic").get("type").asText()).isEqualTo("string");
        assertThat(properties.get("style").get("type").asText()).isEqualTo("string");
        assertThat(properties.get("audience").get("type").asText()).isEqualTo("string");

        // All required (order-independent)
        assertThat(toStringSet(required))
                .containsExactlyInAnyOrder("topic", "style", "audience");
    }

    @Test
    void shouldMapPrimitiveAndBooleanTypesCorrectly() throws Exception {
        Method configure = MixedTypesAgent.class.getMethod(
                "configure", String.class, int.class, boolean.class);

        Workflow workflow = new Workflow().withDocument(new Document().withName("mixed"));

        MethodInputJsonSchema.applySchemaIfAbsent(workflow, configure);

        SchemaInline inline = workflow.getInput().getSchema().getSchemaInline();
        ObjectNode schema = (ObjectNode) inline.getDocument();
        ObjectNode properties = (ObjectNode) schema.get("properties");

        assertThat(properties.get("name")).isNotNull();
        assertThat(properties.get("retries")).isNotNull();
        assertThat(properties.get("enabled")).isNotNull();

        assertThat(properties.get("name").get("type").asText()).isEqualTo("string");
        // mapJavaTypeToJsonType maps all numeric primitives to "number"
        assertThat(properties.get("retries").get("type").asText()).isEqualTo("number");
        assertThat(properties.get("enabled").get("type").asText()).isEqualTo("boolean");
    }

    @Test
    void shouldNotOverrideExistingSchema() throws Exception {
        Method write = Agents.StoryCreatorWithConfigurableStyleEditor.class.getMethod(
                "write", String.class, String.class, String.class);

        Workflow workflow = new Workflow().withDocument(new Document().withName("pre"));

        // Pre-populate an input schema
        ObjectNode existingSchema = JsonUtils.object();
        existingSchema.put("type", "object");
        SchemaInline inline = new SchemaInline(existingSchema);
        SchemaUnion union = new SchemaUnion().withSchemaInline(inline);

        Input input = new Input();
        input.setSchema(union);
        workflow.setInput(input);

        MethodInputJsonSchema.applySchemaIfAbsent(workflow, write);

        // Expect the same instance, not overridden
        assertThat(workflow.getInput().getSchema().getSchemaInline().getDocument())
                .isSameAs(existingSchema);
    }

    @Test
    void shouldDoNothingForNoArgMethod() throws Exception {
        Method ping = NoArgAgent.class.getMethod("ping");

        Workflow workflow = new Workflow().withDocument(new Document().withName("no-arg"));

        MethodInputJsonSchema.applySchemaIfAbsent(workflow, ping);

        // No input at all (or no schema) for 0-arg methods
        assertThat(workflow.getInput()).isNull();
    }

    /**
     * Agent with mixed types to verify type mapping.
     */
    interface MixedTypesAgent {
        void configure(
                @V("name") String name,
                @V("retries") int retries,
                @V("enabled") boolean enabled);
    }

    /**
     * No-arg method: should NOT create a schema.
     */
    interface NoArgAgent {
        String ping();
    }
}
