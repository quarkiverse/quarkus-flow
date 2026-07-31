package io.quarkiverse.flow.langchain4j.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.jboss.jandex.MethodInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import dev.langchain4j.agentic.declarative.LoopCounter;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.service.MemoryId;

class JandexMethodInputJsonSchemaTest {

    private static Index index;

    @BeforeAll
    static void indexTestClasses() throws IOException {
        Indexer indexer = new Indexer();
        indexer.indexClass(TestAgentMethods.class);
        indexer.indexClass(TestPojo.class);
        indexer.indexClass(TestEnum.class);
        indexer.indexClass(TestRecord.class);
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
        assertThat(properties.get("text").get("type").asText()).isEqualTo("string");
        assertThat(properties.has("number")).isTrue();
        assertThat(properties.get("number").get("type").asText()).isEqualTo("number");
        assertThat(properties.has("flag")).isTrue();
        assertThat(properties.get("flag").get("type").asText()).isEqualTo("boolean");

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
        assertThat(enumProp.get("type").asText()).isEqualTo("string");

        ArrayNode enumValues = (ArrayNode) enumProp.get("enum");
        assertThat(enumValues).isNotNull();
        assertThat(enumValues).hasSize(3);
        assertThat(enumValues.toString()).contains("RED", "GREEN", "BLUE");
    }

    @Test
    @DisplayName("test_array_parameter_generates_array_schema")
    void test_array_parameter_generates_array_schema() throws Exception {
        MethodInfo method = getMethod("withArray", String[].class);

        ObjectNode schema = JandexMethodInputJsonSchema.generateSchemaNode(index, method);

        assertThat(schema).isNotNull();

        ObjectNode properties = (ObjectNode) schema.get("properties");
        JsonNode arrayProp = properties.get("items");
        assertThat(arrayProp.get("type").asText()).isEqualTo("array");
        assertThat(arrayProp.get("items")).isNotNull();
        assertThat(arrayProp.get("items").get("type").asText()).isEqualTo("string");
    }

    @Test
    @DisplayName("test_collection_parameter_generates_array_schema")
    void test_collection_parameter_generates_array_schema() throws Exception {
        MethodInfo method = getMethod("withCollection", List.class);

        ObjectNode schema = JandexMethodInputJsonSchema.generateSchemaNode(index, method);

        assertThat(schema).isNotNull();

        ObjectNode properties = (ObjectNode) schema.get("properties");
        JsonNode collectionProp = properties.get("items");
        assertThat(collectionProp.get("type").asText()).isEqualTo("array");
    }

    @Test
    @DisplayName("test_map_parameter_generates_object_schema")
    void test_map_parameter_generates_object_schema() throws Exception {
        MethodInfo method = getMethod("withMap", Map.class);

        ObjectNode schema = JandexMethodInputJsonSchema.generateSchemaNode(index, method);

        assertThat(schema).isNotNull();

        ObjectNode properties = (ObjectNode) schema.get("properties");
        JsonNode mapProp = properties.get("data");
        assertThat(mapProp.get("type").asText()).isEqualTo("object");
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
        assertThat(pojoProp.get("type").asText()).isEqualTo("object");

        ObjectNode pojoProperties = (ObjectNode) pojoProp.get("properties");
        assertThat(pojoProperties).isNotNull();
        assertThat(pojoProperties.has("name")).isTrue();
        assertThat(pojoProperties.has("age")).isTrue();
        assertThat(pojoProperties.get("name").get("type").asText()).isEqualTo("string");
        assertThat(pojoProperties.get("age").get("type").asText()).isEqualTo("number");

        ArrayNode pojoRequired = (ArrayNode) pojoProp.get("required");
        assertThat(pojoRequired).hasSize(2);
    }

    @Test
    @DisplayName("test_record_parameter_expands_one_level")
    void test_record_parameter_expands_one_level() throws Exception {
        MethodInfo method = getMethod("withRecord", TestRecord.class);

        ObjectNode schema = JandexMethodInputJsonSchema.generateSchemaNode(index, method);

        assertThat(schema).isNotNull();

        ObjectNode properties = (ObjectNode) schema.get("properties");
        JsonNode recordProp = properties.get("record");
        assertThat(recordProp.get("type").asText()).isEqualTo("object");

        ObjectNode recordProperties = (ObjectNode) recordProp.get("properties");
        assertThat(recordProperties).isNotNull();
        assertThat(recordProperties.has("title")).isTrue();
        assertThat(recordProperties.has("count")).isTrue();
    }

    @Test
    @DisplayName("test_boxed_jdk_types_do_not_throw_npe")
    void test_boxed_jdk_types_do_not_throw_npe() throws Exception {
        MethodInfo method = getMethod("withBoxedTypes", Integer.class, Long.class, Double.class, Boolean.class);

        ObjectNode schema = JandexMethodInputJsonSchema.generateSchemaNode(index, method);

        assertThat(schema).isNotNull();

        ObjectNode properties = (ObjectNode) schema.get("properties");
        assertThat(properties.get("travelers").get("type").asText()).isEqualTo("number");
        assertThat(properties.get("distance").get("type").asText()).isEqualTo("number");
        assertThat(properties.get("price").get("type").asText()).isEqualTo("number");
        assertThat(properties.get("active").get("type").asText()).isEqualTo("boolean");
    }

    @Test
    @DisplayName("test_only_framework_params_returns_null")
    void test_only_framework_params_returns_null() throws Exception {
        MethodInfo method = getMethod("onlyFrameworkParams", AgenticScope.class);

        ObjectNode schema = JandexMethodInputJsonSchema.generateSchemaNode(index, method);

        assertThat(schema).isNull();
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

        public void withPojo(TestPojo pojo) {
        }

        public void withRecord(TestRecord record) {
        }

        public void onlyFrameworkParams(AgenticScope scope) {
        }

        public void withBoxedTypes(Integer travelers, Long distance, Double price, Boolean active) {
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
}
