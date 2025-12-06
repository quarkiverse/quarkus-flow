package io.quarkiverse.flow.langchain4j.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    private static Set<String> toStringSet(ArrayNode arrayNode) {
        Set<String> set = new HashSet<>();
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
    void shouldHandleEnumParameterAsStringEnum() throws Exception {
        Method setLevel = EnumAgent.class.getMethod("setLevel", TestLevel.class);

        Workflow workflow = new Workflow().withDocument(new Document().withName("enum"));

        MethodInputJsonSchema.applySchemaIfAbsent(workflow, setLevel);

        SchemaInline inline = workflow.getInput().getSchema().getSchemaInline();
        ObjectNode schema = (ObjectNode) inline.getDocument();
        ObjectNode properties = (ObjectNode) schema.get("properties");

        ObjectNode level = (ObjectNode) properties.get("level");
        assertThat(level).isNotNull();
        assertThat(level.get("type").asText()).isEqualTo("string");

        ArrayNode enumNode = (ArrayNode) level.get("enum");
        assertThat(enumNode).isNotNull();
        assertThat(toStringSet(enumNode))
                .containsExactlyInAnyOrder("LOW", "MEDIUM", "HIGH");
    }

    @Test
    void shouldHandleMapCollectionsAndArrayTypes() throws Exception {
        Method complex = MapAndCollectionsAgent.class.getMethod(
                "complex", Map.class, List.class, int[].class);

        Workflow workflow = new Workflow().withDocument(new Document().withName("collections"));

        MethodInputJsonSchema.applySchemaIfAbsent(workflow, complex);

        SchemaInline inline = workflow.getInput().getSchema().getSchemaInline();
        ObjectNode schema = (ObjectNode) inline.getDocument();
        ObjectNode properties = (ObjectNode) schema.get("properties");

        // Map<String,Object> -> type: object + additionalProperties: { type: object }
        ObjectNode config = (ObjectNode) properties.get("config");
        assertThat(config.get("type").asText()).isEqualTo("object");
        ObjectNode additionalProps = (ObjectNode) config.get("additionalProperties");
        assertThat(additionalProps).isNotNull();
        assertThat(additionalProps.get("type").asText()).isEqualTo("object");

        // List<String> -> type: array, items: { type: object } (we don't inspect generics)
        ObjectNode tags = (ObjectNode) properties.get("tags");
        assertThat(tags.get("type").asText()).isEqualTo("array");
        ObjectNode tagsItems = (ObjectNode) tags.get("items");
        assertThat(tagsItems).isNotNull();
        assertThat(tagsItems.get("type").asText()).isEqualTo("object");

        // int[] -> type: array, items: { type: number }
        ObjectNode scores = (ObjectNode) properties.get("scores");
        assertThat(scores.get("type").asText()).isEqualTo("array");
        ObjectNode scoresItems = (ObjectNode) scores.get("items");
        assertThat(scoresItems).isNotNull();
        assertThat(scoresItems.get("type").asText()).isEqualTo("number");
    }

    @Test
    void shouldExpandSingleRecordParameterOneLevel() throws Exception {
        Method handle = RecordAgent.class.getMethod("handle", SimpleRecord.class);

        Workflow workflow = new Workflow().withDocument(new Document().withName("record"));

        MethodInputJsonSchema.applySchemaIfAbsent(workflow, handle);

        SchemaInline inline = workflow.getInput().getSchema().getSchemaInline();
        ObjectNode schema = (ObjectNode) inline.getDocument();
        ObjectNode properties = (ObjectNode) schema.get("properties");

        ObjectNode payloadSchema = (ObjectNode) properties.get("payload");
        assertThat(payloadSchema.get("type").asText()).isEqualTo("object");

        ObjectNode payloadProps = (ObjectNode) payloadSchema.get("properties");
        ArrayNode payloadRequired = (ArrayNode) payloadSchema.get("required");

        assertThat(payloadProps.get("title").get("type").asText()).isEqualTo("string");
        assertThat(payloadProps.get("count").get("type").asText()).isEqualTo("number");
        assertThat(payloadProps.get("flag").get("type").asText()).isEqualTo("boolean");

        assertThat(toStringSet(payloadRequired))
                .containsExactlyInAnyOrder("title", "count", "flag");
    }

    @Test
    void shouldExpandSinglePojoParameterOneLevel() throws Exception {
        Method handle = PojoAgent.class.getMethod("handle", SimplePojo.class);

        Workflow workflow = new Workflow().withDocument(new Document().withName("pojo"));

        MethodInputJsonSchema.applySchemaIfAbsent(workflow, handle);

        SchemaInline inline = workflow.getInput().getSchema().getSchemaInline();
        ObjectNode schema = (ObjectNode) inline.getDocument();
        ObjectNode properties = (ObjectNode) schema.get("properties");

        ObjectNode payloadSchema = (ObjectNode) properties.get("payload");
        assertThat(payloadSchema.get("type").asText()).isEqualTo("object");

        ObjectNode payloadProps = (ObjectNode) payloadSchema.get("properties");
        ArrayNode payloadRequired = (ArrayNode) payloadSchema.get("required");

        assertThat(payloadProps.get("title").get("type").asText()).isEqualTo("string");
        assertThat(payloadProps.get("count").get("type").asText()).isEqualTo("number");
        assertThat(payloadProps.get("flag").get("type").asText()).isEqualTo("boolean");

        // Static field should not appear
        assertThat(payloadProps.get("IGNORED")).isNull();

        assertThat(toStringSet(payloadRequired))
                .containsExactlyInAnyOrder("title", "count", "flag");
    }

    @Test
    void shouldNotRecurseIntoNestedPojoInsideRecord() throws Exception {
        Method handle = NestedRecordAgent.class.getMethod("handle", WrapperRecord.class);

        Workflow workflow = new Workflow().withDocument(new Document().withName("nested-record"));

        MethodInputJsonSchema.applySchemaIfAbsent(workflow, handle);

        SchemaInline inline = workflow.getInput().getSchema().getSchemaInline();
        ObjectNode schema = (ObjectNode) inline.getDocument();
        ObjectNode properties = (ObjectNode) schema.get("properties");

        ObjectNode payloadSchema = (ObjectNode) properties.get("payload");
        ObjectNode payloadProps = (ObjectNode) payloadSchema.get("properties");

        ObjectNode innerSchema = (ObjectNode) payloadProps.get("inner");
        assertThat(innerSchema).isNotNull();
        assertThat(innerSchema.get("type").asText()).isEqualTo("object");

        // No deep recursion: inner itself should not have "properties"
        assertThat(innerSchema.get("properties")).isNull();
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

    // -------------------------------------------------------------------------
    // Helper types for the tests
    // -------------------------------------------------------------------------

    enum TestLevel {
        LOW,
        MEDIUM,
        HIGH
    }

    interface EnumAgent {
        void setLevel(@V("level") TestLevel level);
    }

    interface MapAndCollectionsAgent {
        void complex(
                @V("config") Map<String, Object> config,
                @V("tags") java.util.List<String> tags,
                @V("scores") int[] scores);
    }

    interface MixedTypesAgent {
        void configure(
                @V("name") String name,
                @V("retries") int retries,
                @V("enabled") boolean enabled);
    }

    interface NoArgAgent {
        String ping();
    }

    // Simple record to test one-level expansion
    record SimpleRecord(String title, int count, boolean flag) {
    }

    interface RecordAgent {
        void handle(@V("payload") SimpleRecord payload);
    }

    // Simple POJO to test one-level expansion
    static class SimplePojo {
        String title;
        int count;
        boolean flag;
        static String IGNORED = "ignore-me";
    }

    interface PojoAgent {
        void handle(@V("payload") SimplePojo payload);
    }

    // Nested record: we expand only the outer one, inner becomes plain "object"
    record InnerRecord(String value) {
    }

    record WrapperRecord(InnerRecord inner) {
    }

    interface NestedRecordAgent {
        void handle(@V("payload") WrapperRecord payload);
    }
}
