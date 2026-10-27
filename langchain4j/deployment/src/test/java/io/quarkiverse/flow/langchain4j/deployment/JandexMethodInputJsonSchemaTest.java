package io.quarkiverse.flow.langchain4j.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.jboss.jandex.MethodInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.agentic.declarative.LoopCounter;
import dev.langchain4j.agentic.declarative.TypedKey;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.V;

class JandexMethodInputJsonSchemaTest {

    private static Index index;

    @BeforeAll
    static void indexTestClasses() throws IOException {
        Indexer indexer = new Indexer();
        indexer.indexClass(TestAgentMethods.class);
        indexer.indexClass(TestPojo.class);
        indexer.indexClass(TestEnum.class);
        indexer.indexClass(TestRecord.class);
        indexer.indexClass(NullableRecord.class);
        indexer.indexClass(OptionalFieldRecord.class);
        indexer.indexClass(JacksonAnnotatedPojo.class);
        indexer.indexClass(TestKey.class);
        // Index collection hierarchy
        indexer.indexClass(java.util.Collection.class);
        indexer.indexClass(java.lang.Iterable.class);
        indexer.indexClass(List.class);
        indexer.indexClass(ArrayList.class);
        indexer.indexClass(Map.class);
        indexer.indexClass(HashMap.class);
        index = indexer.complete();
    }

    @Test
    @DisplayName("test_simple_parameters_generate_correct_schema")
    void test_simple_parameters_generate_correct_schema() throws Exception {
        MethodInfo method = getMethod("simpleParams", String.class, int.class, boolean.class);

        ObjectNode schema = JandexMethodInputJsonSchema.generateSchemaNode(index, method);

        assertThat(schema).isNotNull();
        assertThat(schema.get("type").asText()).isEqualTo("object");
        assertThat(schema.get("$schema").asText()).isEqualTo("http://json-schema.org/draft-07/schema#");

        ObjectNode properties = (ObjectNode) schema.get("properties");
        assertThat(properties).isNotNull();
        assertThat(properties.has("text")).isTrue();
        // String is a reference type, so null is a legal value
        assertThat(typesOf(properties.get("text"))).containsExactly("string", "null");
        assertThat(properties.has("number")).isTrue();
        assertThat(typesOf(properties.get("number"))).containsExactly("number");
        assertThat(properties.has("flag")).isTrue();
        assertThat(typesOf(properties.get("flag"))).containsExactly("boolean");

        ArrayNode required = (ArrayNode) schema.get("required");
        assertThat(required).hasSize(3);
        assertThat(required.get(0).asText()).isEqualTo("text");
        assertThat(required.get(1).asText()).isEqualTo("number");
        assertThat(required.get(2).asText()).isEqualTo("flag");
    }

    @Test
    @DisplayName("test_framework_parameters_are_filtered")
    void test_framework_parameters_are_filtered() throws Exception {
        MethodInfo method = getMethod("withFrameworkParams", String.class, AgenticScope.class, int.class);

        ObjectNode schema = JandexMethodInputJsonSchema.generateSchemaNode(index, method);

        assertThat(schema).isNotNull();

        ObjectNode properties = (ObjectNode) schema.get("properties");
        assertThat(properties).isNotNull();
        assertThat(properties.has("text")).isTrue(); // String
        assertThat(properties.has("scope")).isFalse(); // AgenticScope - should be filtered
        assertThat(properties.has("number")).isTrue(); // int

        ArrayNode required = (ArrayNode) schema.get("required");
        assertThat(required).hasSize(2);
    }

    @Test
    @DisplayName("test_annotated_parameters_are_filtered")
    void test_annotated_parameters_are_filtered() throws Exception {
        MethodInfo method = getMethod("withAnnotatedParams", String.class, String.class, int.class);

        ObjectNode schema = JandexMethodInputJsonSchema.generateSchemaNode(index, method);

        assertThat(schema).isNotNull();

        ObjectNode properties = (ObjectNode) schema.get("properties");
        assertThat(properties).isNotNull();
        // First String parameter has no annotation, should be included
        assertThat(properties.has("text")).isTrue();
        // Second String parameter has @MemoryId, should be filtered
        assertThat(properties.has("memoryId")).isFalse();
        // Third int parameter has @LoopCounter, should be filtered
        assertThat(properties.has("counter")).isFalse();

        ArrayNode required = (ArrayNode) schema.get("required");
        assertThat(required).hasSize(1);
    }

    @Test
    @DisplayName("test_no_parameters_returns_null")
    void test_no_parameters_returns_null() throws Exception {
        MethodInfo method = getMethod("noParams");

        ObjectNode schema = JandexMethodInputJsonSchema.generateSchemaNode(index, method);

        assertThat(schema).isNull();
    }

    @Test
    @DisplayName("test_enum_parameter_generates_enum_schema")
    void test_enum_parameter_generates_enum_schema() throws Exception {
        MethodInfo method = getMethod("withEnum", TestEnum.class);

        ObjectNode schema = JandexMethodInputJsonSchema.generateSchemaNode(index, method);

        assertThat(schema).isNotNull();

        ObjectNode properties = (ObjectNode) schema.get("properties");
        JsonNode enumProp = properties.get("color");
        assertThat(typesOf(enumProp)).containsExactly("string", "null");

        // A nullable enum must accept null as an enum value too, otherwise the union type is unsatisfiable
        ArrayNode enumValues = (ArrayNode) enumProp.get("enum");
        assertThat(enumValues)
                .isNotNull()
                .hasSize(4);
        assertThat(enumValues.toString()).contains("RED", "GREEN", "BLUE", "null");
    }

    @Test
    @DisplayName("test_array_parameter_generates_array_schema")
    void test_array_parameter_generates_array_schema() throws Exception {
        MethodInfo method = getMethod("withArray", String[].class);

        ObjectNode schema = JandexMethodInputJsonSchema.generateSchemaNode(index, method);

        assertThat(schema).isNotNull();

        ObjectNode properties = (ObjectNode) schema.get("properties");
        JsonNode arrayProp = properties.get("items");
        assertThat(typesOf(arrayProp)).containsExactly("array", "null");
        assertThat(arrayProp.get("items")).isNotNull();
        assertThat(typesOf(arrayProp.get("items"))).containsExactly("string", "null");
    }

    @Test
    @DisplayName("test_collection_parameter_generates_array_schema")
    void test_collection_parameter_generates_array_schema() throws Exception {
        MethodInfo method = getMethod("withCollection", List.class);

        ObjectNode schema = JandexMethodInputJsonSchema.generateSchemaNode(index, method);

        assertThat(schema).isNotNull();

        ObjectNode properties = (ObjectNode) schema.get("properties");
        JsonNode collectionProp = properties.get("items");
        assertThat(typesOf(collectionProp)).containsExactly("array", "null");
        // List<String> items are nullable strings, not objects
        assertThat(typesOf(collectionProp.get("items"))).containsExactly("string", "null");
    }

    @Test
    @DisplayName("test_untyped_collection_items_and_map_values_are_open")
    void test_untyped_collection_items_and_map_values_are_open() throws Exception {
        MethodInfo method = getMethod("withMap", Map.class);

        JsonNode mapProp = JandexMethodInputJsonSchema.generateSchemaNode(index, method).get("properties").get("data");
        // Map<String, Object> values may be anything
        assertThat(mapProp.get("additionalProperties").isEmpty()).isTrue();

        JsonNode typedMap = JandexMethodInputJsonSchema.generateSchemaNode(index, getMethod("withTypedMap", Map.class))
                .get("properties").get("data");
        assertThat(typesOf(typedMap.get("additionalProperties"))).containsExactly("number", "null");

        JsonNode pojoList = JandexMethodInputJsonSchema.generateSchemaNode(index, getMethod("withRecordList", List.class))
                .get("properties").get("records");
        assertThat(typesOf(pojoList.get("items"))).containsExactly("object", "null");
    }

    @Test
    @DisplayName("test_map_parameter_generates_object_schema")
    void test_map_parameter_generates_object_schema() throws Exception {
        MethodInfo method = getMethod("withMap", Map.class);

        ObjectNode schema = JandexMethodInputJsonSchema.generateSchemaNode(index, method);

        assertThat(schema).isNotNull();

        ObjectNode properties = (ObjectNode) schema.get("properties");
        JsonNode mapProp = properties.get("data");
        assertThat(typesOf(mapProp)).containsExactly("object", "null");
        assertThat(mapProp.has("additionalProperties")).isTrue();
    }

    @Test
    @DisplayName("test_pojo_parameter_expands_one_level")
    void test_pojo_parameter_expands_one_level() throws Exception {
        MethodInfo method = getMethod("withPojo", TestPojo.class);

        ObjectNode schema = JandexMethodInputJsonSchema.generateSchemaNode(index, method);

        assertThat(schema).isNotNull();

        ObjectNode properties = (ObjectNode) schema.get("properties");
        JsonNode pojoProp = properties.get("pojo");
        assertThat(typesOf(pojoProp)).containsExactly("object", "null");

        ObjectNode pojoProperties = (ObjectNode) pojoProp.get("properties");
        assertThat(pojoProperties).isNotNull();
        assertThat(pojoProperties.has("name")).isTrue();
        assertThat(pojoProperties.has("age")).isTrue();
        assertThat(typesOf(pojoProperties.get("name"))).containsExactly("string", "null");
        assertThat(typesOf(pojoProperties.get("age"))).containsExactly("number");

        // Jackson may omit even a primitive (e.g. NON_DEFAULT), so nothing nested is required
        assertThat(pojoProp.has("required")).isFalse();
    }

    @Test
    @DisplayName("test_record_parameter_expands_one_level")
    void test_record_parameter_expands_one_level() throws Exception {
        MethodInfo method = getMethod("withRecord", TestRecord.class);

        ObjectNode schema = JandexMethodInputJsonSchema.generateSchemaNode(index, method);

        assertThat(schema).isNotNull();

        ObjectNode properties = (ObjectNode) schema.get("properties");
        JsonNode recordProp = properties.get("record");
        assertThat(typesOf(recordProp)).containsExactly("object", "null");

        ObjectNode recordProperties = (ObjectNode) recordProp.get("properties");
        assertThat(recordProperties).isNotNull();
        assertThat(recordProperties.has("title")).isTrue();
        assertThat(recordProperties.has("count")).isTrue();

        assertThat(recordProp.has("required")).isFalse();
    }

    @Test
    @DisplayName("test_nullable_record_fields_accept_null")
    void test_nullable_record_fields_accept_null() throws Exception {
        MethodInfo method = getMethod("withRecord", TestRecord.class);

        ObjectNode schema = JandexMethodInputJsonSchema.generateSchemaNode(index, method);

        JsonNode recordProperties = schema.get("properties").get("record").get("properties");
        // new TestRecord(null, 1) must validate: nothing in the signature forbids a null title
        assertThat(typesOf(recordProperties.get("title"))).containsExactly("string", "null");
    }

    @Test
    @DisplayName("test_primitive_nested_property_is_not_required")
    void test_primitive_nested_property_is_not_required() throws Exception {
        MethodInfo method = getMethod("withRecord", TestRecord.class);

        ObjectNode schema = JandexMethodInputJsonSchema.generateSchemaNode(index, method);

        JsonNode recordProp = schema.get("properties").get("record");
        // An int cannot be null, but Jackson's NON_DEFAULT may omit it
        assertThat(typesOf(recordProp.get("properties").get("count"))).containsExactly("number");
        assertThat(recordProp.has("required")).isFalse();
    }

    @Test
    @DisplayName("test_nested_properties_are_not_required")
    void test_nested_properties_are_not_required() throws Exception {
        MethodInfo method = getMethod("withNestedOnlyReferences", NullableRecord.class);

        ObjectNode schema = JandexMethodInputJsonSchema.generateSchemaNode(index, method);

        JsonNode recordProp = schema.get("properties").get("fields");
        assertThat(recordProp.get("properties").properties()).hasSize(2);
        assertThat(recordProp.has("required")).isFalse();
    }

    @Test
    @DisplayName("test_invocation_parameters_are_filtered")
    void test_invocation_parameters_are_filtered() throws Exception {
        MethodInfo method = getMethod("withInvocationParameters", String.class, InvocationParameters.class);

        ObjectNode schema = JandexMethodInputJsonSchema.generateSchemaNode(index, method);

        assertThat(schema).isNotNull();

        // LangChain4j routes InvocationParameters to the execution context, never to the AgenticScope state map
        assertThat(((ObjectNode) schema.get("properties")).has("invocationParameters")).isFalse();
        assertThat((ArrayNode) schema.get("required")).singleElement()
                .extracting(JsonNode::asText)
                .isEqualTo("text");
    }

    @Test
    @DisplayName("test_optional_parameter_is_nullable_and_not_required")
    void test_optional_parameter_is_nullable_and_not_required() throws Exception {
        MethodInfo method = getMethod("withOptional", String.class, Optional.class);

        ObjectNode schema = JandexMethodInputJsonSchema.generateSchemaNode(index, method);

        ObjectNode properties = (ObjectNode) schema.get("properties");
        assertThat(properties.has("maybeText")).isTrue();
        // Optional<String> describes a nullable String, not an object wrapper
        assertThat(typesOf(properties.get("maybeText"))).containsExactly("string", "null");

        assertThat((ArrayNode) schema.get("required")).singleElement()
                .extracting(JsonNode::asText)
                .isEqualTo("text");
    }

    @Test
    @DisplayName("test_optional_nested_property_is_unwrapped_and_nullable")
    void test_optional_nested_property_is_unwrapped_and_nullable() throws Exception {
        MethodInfo method = getMethod("withOptionalField", OptionalFieldRecord.class);

        ObjectNode schema = JandexMethodInputJsonSchema.generateSchemaNode(index, method);

        JsonNode recordProp = schema.get("properties").get("holder");
        // Optional<String> describes a nullable String rather than an opaque object wrapper
        assertThat(typesOf(recordProp.get("properties").get("note"))).containsExactly("string", "null");
        assertThat(recordProp.has("required")).isFalse();
    }

    @Test
    @DisplayName("test_v_annotation_renames_property")
    void test_v_annotation_renames_property() throws Exception {
        MethodInfo method = getMethod("withVariableName", String.class);

        ObjectNode schema = JandexMethodInputJsonSchema.generateSchemaNode(index, method);

        ObjectNode properties = (ObjectNode) schema.get("properties");
        // LangChain4j stores the value under the @V name, not the declared parameter name
        assertThat(properties.has("renamed")).isTrue();
        assertThat(properties.has("bar")).isFalse();
        assertThat((ArrayNode) schema.get("required")).singleElement()
                .extracting(JsonNode::asText)
                .isEqualTo("renamed");
    }

    @Test
    @DisplayName("test_k_annotation_parameter_is_filtered")
    void test_k_annotation_parameter_is_filtered() throws Exception {
        MethodInfo method = getMethod("withTypedKey", String.class, String.class);

        ObjectNode schema = JandexMethodInputJsonSchema.generateSchemaNode(index, method);

        ObjectNode properties = (ObjectNode) schema.get("properties");
        // The state key is the TypedKey's runtime name(), which Jandex cannot resolve - so it is left unvalidated
        assertThat(properties.has("keyed")).isFalse();
        assertThat(properties.has("text")).isTrue();
    }

    @Test
    @DisplayName("test_json_ignore_field_is_omitted")
    void test_json_ignore_field_is_omitted() throws Exception {
        MethodInfo method = getMethod("withJacksonAnnotations", JacksonAnnotatedPojo.class);

        ObjectNode schema = JandexMethodInputJsonSchema.generateSchemaNode(index, method);

        JsonNode pojoProperties = schema.get("properties").get("pojo").get("properties");
        assertThat(pojoProperties.has("secret")).isFalse();
    }

    @Test
    @DisplayName("test_json_property_renames_field")
    void test_json_property_renames_field() throws Exception {
        MethodInfo method = getMethod("withJacksonAnnotations", JacksonAnnotatedPojo.class);

        ObjectNode schema = JandexMethodInputJsonSchema.generateSchemaNode(index, method);

        JsonNode pojoProperties = schema.get("properties").get("pojo").get("properties");
        assertThat(pojoProperties.has("full_name")).isTrue();
        assertThat(pojoProperties.has("fullName")).isFalse();
    }

    @Test
    @DisplayName("test_boxed_jdk_types_do_not_throw_npe")
    void test_boxed_jdk_types_do_not_throw_npe() throws Exception {
        MethodInfo method = getMethod("withBoxedTypes", Integer.class, Long.class, Double.class, Boolean.class);

        ObjectNode schema = JandexMethodInputJsonSchema.generateSchemaNode(index, method);

        assertThat(schema).isNotNull();

        ObjectNode properties = (ObjectNode) schema.get("properties");
        assertThat(typesOf(properties.get("travelers"))).containsExactly("number", "null");
        assertThat(typesOf(properties.get("distance"))).containsExactly("number", "null");
        assertThat(typesOf(properties.get("price"))).containsExactly("number", "null");
        assertThat(typesOf(properties.get("active"))).containsExactly("boolean", "null");
    }

    @Test
    @DisplayName("test_only_framework_params_returns_null")
    void test_only_framework_params_returns_null() throws Exception {
        MethodInfo method = getMethod("onlyFrameworkParams", AgenticScope.class);

        ObjectNode schema = JandexMethodInputJsonSchema.generateSchemaNode(index, method);

        assertThat(schema).isNull();
    }

    /**
     * The declared JSON Schema types of a property, normalising the single-type and {@code ["x", "null"]} union
     * forms into one list.
     */
    private static List<String> typesOf(JsonNode property) {
        JsonNode type = property.get("type");

        return type.isArray()
                ? StreamSupport.stream(type.spliterator(), false)
                        .map(JsonNode::asText)
                        .toList()
                : List.of(type.asText());
    }

    private MethodInfo getMethod(String name, Class<?>... paramTypes) {
        DotName className = DotName
                .createSimple("io.quarkiverse.flow.langchain4j.deployment.JandexMethodInputJsonSchemaTest$TestAgentMethods");
        ClassInfo classInfo = index.getClassByName(className);

        // Find method by name and parameter count (our test methods have unique names)
        for (MethodInfo method : classInfo.methods()) {
            if (method.name().equals(name) && method.parametersCount() == paramTypes.length) {
                return method;
            }
        }

        throw new IllegalArgumentException("Method not found: " + name);
    }

    // Test classes and interfaces

    public enum TestEnum {
        RED,
        GREEN,
        BLUE
    }

    public static class TestAgentMethods {
        public void simpleParams(String text, int number, boolean flag) {
        }

        public void withFrameworkParams(String text, AgenticScope scope, int number) {
        }

        public void withAnnotatedParams(String text, @MemoryId String memoryId, @LoopCounter int counter) {
        }

        public void noParams() {
        }

        public void withEnum(TestEnum color) {
        }

        public void withArray(String[] items) {
        }

        public void withCollection(List<String> items) {
        }

        public void withMap(Map<String, Object> data) {
        }

        public void withTypedMap(Map<String, Integer> data) {
        }

        public void withRecordList(List<TestRecord> records) {
        }

        public void withPojo(TestPojo pojo) {
        }

        public void withRecord(TestRecord record) {
        }

        public void onlyFrameworkParams(AgenticScope scope) {
        }

        public void withBoxedTypes(Integer travelers, Long distance, Double price, Boolean active) {
        }

        public void withInvocationParameters(String text, InvocationParameters invocationParameters) {
        }

        public void withOptional(String text, Optional<String> maybeText) {
        }

        public void withVariableName(@V("renamed") String bar) {
        }

        public void withTypedKey(String text, @K(TestKey.class) String keyed) {
        }

        public void withNestedOnlyReferences(NullableRecord fields) {
        }

        public void withJacksonAnnotations(JacksonAnnotatedPojo pojo) {
        }

        public void withOptionalField(OptionalFieldRecord holder) {
        }
    }

    public static class TestPojo {
        private String name;
        private int age;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }
    }

    public record TestRecord(String title, int count) {
    }

    /** Mirrors the reproducer's progressively-populated extraction record: every field may legitimately be null. */
    public record NullableRecord(String category, String summary) {
    }

    public record OptionalFieldRecord(Optional<String> note, int count) {
    }

    public static class JacksonAnnotatedPojo {
        @JsonProperty("full_name")
        private String fullName;

        @JsonIgnore
        private String secret;

        public String getFullName() {
            return fullName;
        }

        public String getSecret() {
            return secret;
        }
    }

    public static class TestKey implements TypedKey<String> {
    }
}
