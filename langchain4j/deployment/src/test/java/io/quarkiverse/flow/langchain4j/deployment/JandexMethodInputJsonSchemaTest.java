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

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

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
        indexer.indexClass(NotNullRecord.class);
        indexer.indexClass(JSpecifyRecord.class);
        indexer.indexClass(JetBrainsRecord.class);
        indexer.indexClass(NotNullIgnoredRecord.class);
        indexer.indexClass(TestKey.class);
        indexer.indexClass(RenamedEnum.class);
        indexer.indexClass(LabeledEnum.class);
        indexer.indexClass(FieldValueEnum.class);
        indexer.indexClass(CodedEnum.class);
        indexer.indexClass(Labeled.class);
        indexer.indexClass(InheritedLabelEnum.class);
        indexer.indexClass(DisabledValueEnum.class);
        indexer.indexClass(DisabledInheritedValueEnum.class);
        indexer.indexClass(OrdinalEnum.class);
        indexer.indexClass(StringShapeEnum.class);
        indexer.indexClass(SerializedEnum.class);
        indexer.indexClass(LabeledEnumRecord.class);
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
    @DisplayName("test_json_property_on_enum_constants_renames_enum_values")
    void test_json_property_on_enum_constants_renames_enum_values() throws Exception {
        JsonNode enumProp = propertyOf("withRenamedEnum", "theme", RenamedEnum.class);

        assertThat(typesOf(enumProp)).containsExactly("string", "null");
        assertThat(enumValuesOf(enumProp)).containsExactlyInAnyOrder("light", "DARK", "null");
    }

    @Test
    @DisplayName("test_json_value_method_on_enum_drops_the_enum_list")
    void test_json_value_method_on_enum_drops_the_enum_list() throws Exception {
        JsonNode enumProp = propertyOf("withLabeledEnum", "priority", LabeledEnum.class);

        // Jackson writes the label, which is only known at runtime: listing the constant names would reject it
        assertThat(typesOf(enumProp)).containsExactly("string", "null");
        assertThat(enumProp.has("enum")).isFalse();
    }

    @Test
    @DisplayName("test_json_value_field_on_enum_drops_the_enum_list")
    void test_json_value_field_on_enum_drops_the_enum_list() throws Exception {
        JsonNode enumProp = propertyOf("withFieldValueEnum", "level", FieldValueEnum.class);

        assertThat(typesOf(enumProp)).containsExactly("string", "null");
        assertThat(enumProp.has("enum")).isFalse();
    }

    @Test
    @DisplayName("test_json_value_type_types_the_enum")
    void test_json_value_type_types_the_enum() throws Exception {
        JsonNode enumProp = propertyOf("withCodedEnum", "code", CodedEnum.class);

        assertThat(typesOf(enumProp)).containsExactly("number", "null");
        assertThat(enumProp.has("enum")).isFalse();
    }

    @Test
    @DisplayName("test_json_value_inherited_from_an_interface_drops_the_enum_list")
    void test_json_value_inherited_from_an_interface_drops_the_enum_list() throws Exception {
        JsonNode enumProp = propertyOf("withInheritedLabelEnum", "size", InheritedLabelEnum.class);

        assertThat(typesOf(enumProp)).containsExactly("string", "null");
        assertThat(enumProp.has("enum")).isFalse();
    }

    @Test
    @DisplayName("test_disabled_json_value_keeps_the_constant_names")
    void test_disabled_json_value_keeps_the_constant_names() throws Exception {
        JsonNode enumProp = propertyOf("withDisabledValueEnum", "mode", DisabledValueEnum.class);

        assertThat(enumValuesOf(enumProp)).containsExactlyInAnyOrder("ON", "OFF", "null");
    }

    @Test
    @DisplayName("test_json_value_false_disables_an_inherited_json_value")
    void test_json_value_false_disables_an_inherited_json_value() throws Exception {
        JsonNode enumProp = propertyOf("withDisabledInheritedValueEnum", "weight", DisabledInheritedValueEnum.class);

        assertThat(enumValuesOf(enumProp)).containsExactlyInAnyOrder("LIGHT", "HEAVY", "null");
    }

    @Test
    @DisplayName("test_non_string_json_format_shape_leaves_the_enum_open")
    void test_non_string_json_format_shape_leaves_the_enum_open() throws Exception {
        JsonNode enumProp = propertyOf("withOrdinalEnum", "rank", OrdinalEnum.class);

        // Written as its ordinal
        assertThat(enumProp.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("test_string_json_format_shape_keeps_the_constant_names")
    void test_string_json_format_shape_keeps_the_constant_names() throws Exception {
        JsonNode enumProp = propertyOf("withStringShapeEnum", "side", StringShapeEnum.class);

        assertThat(enumValuesOf(enumProp)).containsExactlyInAnyOrder("LEFT", "RIGHT", "null");
    }

    @Test
    @DisplayName("test_json_serialize_on_enum_leaves_it_open")
    void test_json_serialize_on_enum_leaves_it_open() throws Exception {
        JsonNode enumProp = propertyOf("withSerializedEnum", "flavor", SerializedEnum.class);

        assertThat(enumProp.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("test_json_value_enum_in_a_record_and_a_collection_drops_the_enum_list")
    void test_json_value_enum_in_a_record_and_a_collection_drops_the_enum_list() throws Exception {
        MethodInfo method = getMethod("withLabeledEnumInRecordAndList", LabeledEnumRecord.class, List.class);

        ObjectNode properties = (ObjectNode) JandexMethodInputJsonSchema.generateSchemaNode(index, method).get("properties");

        JsonNode nested = properties.get("holder").get("properties").get("priority");
        JsonNode element = properties.get("asked").get("items");
        assertThat(List.of(nested, element)).allSatisfy(enumProp -> {
            assertThat(typesOf(enumProp)).containsExactly("string", "null");
            assertThat(enumProp.has("enum")).isFalse();
        });
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
    @DisplayName("test_not_null_nested_property_is_strict_and_required")
    void test_not_null_nested_property_is_strict_and_required() throws Exception {
        MethodInfo method = getMethod("withNotNullField", NotNullRecord.class);

        ObjectNode schema = JandexMethodInputJsonSchema.generateSchemaNode(index, method);

        JsonNode recordProp = schema.get("properties").get("fields");
        assertThat(typesOf(recordProp.get("properties").get("category"))).containsExactly("string");
        assertThat(typesOf(recordProp.get("properties").get("location"))).containsExactly("string", "null");
        assertThat((ArrayNode) recordProp.get("required")).singleElement()
                .extracting(JsonNode::asText)
                .isEqualTo("category");
    }

    @Test
    @DisplayName("test_jspecify_non_null_nested_property_is_strict_and_required")
    void test_jspecify_non_null_nested_property_is_strict_and_required() throws Exception {
        MethodInfo method = getMethod("withJSpecifyField", JSpecifyRecord.class);

        ObjectNode schema = JandexMethodInputJsonSchema.generateSchemaNode(index, method);

        JsonNode recordProp = schema.get("properties").get("fields");
        assertThat(typesOf(recordProp.get("properties").get("category"))).containsExactly("string");
        // A non-null element type says nothing about the list itself
        assertThat(typesOf(recordProp.get("properties").get("tags"))).containsExactly("array", "null");
        assertThat((ArrayNode) recordProp.get("required")).singleElement()
                .extracting(JsonNode::asText)
                .isEqualTo("category");
    }

    @Test
    @DisplayName("test_jetbrains_not_null_nested_property_is_strict_and_required")
    void test_jetbrains_not_null_nested_property_is_strict_and_required() throws Exception {
        MethodInfo method = getMethod("withJetBrainsField", JetBrainsRecord.class);

        ObjectNode schema = JandexMethodInputJsonSchema.generateSchemaNode(index, method);

        JsonNode recordProp = schema.get("properties").get("fields");
        assertThat(typesOf(recordProp.get("properties").get("category"))).containsExactly("string");
        assertThat((ArrayNode) recordProp.get("required")).singleElement()
                .extracting(JsonNode::asText)
                .isEqualTo("category");
    }

    @Test
    @DisplayName("test_not_null_on_optional_or_primitive_nested_property_is_not_required")
    void test_not_null_on_optional_or_primitive_nested_property_is_not_required() throws Exception {
        MethodInfo method = getMethod("withNotNullIgnoredFields", NotNullIgnoredRecord.class);

        ObjectNode schema = JandexMethodInputJsonSchema.generateSchemaNode(index, method);

        JsonNode recordProp = schema.get("properties").get("fields");
        // An empty Optional is still null on the wire, and NON_DEFAULT may still omit a primitive
        assertThat(typesOf(recordProp.get("properties").get("note"))).containsExactly("string", "null");
        assertThat(typesOf(recordProp.get("properties").get("count"))).containsExactly("number");
        assertThat(recordProp.has("required")).isFalse();
    }

    @Test
    @DisplayName("test_not_null_parameter_is_not_nullable")
    void test_not_null_parameter_is_not_nullable() throws Exception {
        MethodInfo method = getMethod("withNotNullParam", String.class, String.class);

        ObjectNode schema = JandexMethodInputJsonSchema.generateSchemaNode(index, method);

        ObjectNode properties = (ObjectNode) schema.get("properties");
        assertThat(typesOf(properties.get("text"))).containsExactly("string");
        assertThat(typesOf(properties.get("other"))).containsExactly("string", "null");
        assertThat((ArrayNode) schema.get("required"))
                .extracting(JsonNode::asText)
                .containsExactly("text", "other");
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

    private JsonNode propertyOf(String methodName, String propertyName, Class<?> paramType) {
        ObjectNode schema = JandexMethodInputJsonSchema.generateSchemaNode(index, getMethod(methodName, paramType));

        assertThat(schema).isNotNull();
        return schema.get("properties").get(propertyName);
    }

    private static List<String> enumValuesOf(JsonNode property) {
        assertThat(property.get("enum")).isNotNull();

        return StreamSupport.stream(property.get("enum").spliterator(), false)
                .map(JsonNode::asText)
                .toList();
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

        public void withNotNullField(NotNullRecord fields) {
        }

        public void withJSpecifyField(JSpecifyRecord fields) {
        }

        public void withJetBrainsField(JetBrainsRecord fields) {
        }

        public void withNotNullIgnoredFields(NotNullIgnoredRecord fields) {
        }

        public void withNotNullParam(@jakarta.validation.constraints.NotNull String text, String other) {
        }

        public void withRenamedEnum(RenamedEnum theme) {
        }

        public void withLabeledEnum(LabeledEnum priority) {
        }

        public void withFieldValueEnum(FieldValueEnum level) {
        }

        public void withCodedEnum(CodedEnum code) {
        }

        public void withInheritedLabelEnum(InheritedLabelEnum size) {
        }

        public void withDisabledValueEnum(DisabledValueEnum mode) {
        }

        public void withDisabledInheritedValueEnum(DisabledInheritedValueEnum weight) {
        }

        public void withOrdinalEnum(OrdinalEnum rank) {
        }

        public void withStringShapeEnum(StringShapeEnum side) {
        }

        public void withSerializedEnum(SerializedEnum flavor) {
        }

        public void withLabeledEnumInRecordAndList(LabeledEnumRecord holder, List<LabeledEnum> asked) {
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

    public record NotNullRecord(@jakarta.validation.constraints.NotNull String category, String location) {
    }

    public record JSpecifyRecord(@org.jspecify.annotations.NonNull String category,
            List<@org.jspecify.annotations.NonNull String> tags) {
    }

    public record JetBrainsRecord(@org.jetbrains.annotations.NotNull String category) {
    }

    public record NotNullIgnoredRecord(@jakarta.validation.constraints.NotNull Optional<String> note,
            @jakarta.validation.constraints.NotNull int count) {
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

    public enum RenamedEnum {
        @JsonProperty("light")
        LIGHT,
        DARK
    }

    public enum LabeledEnum {
        LOW("low priority"),
        HIGH("high priority");

        private final String label;

        LabeledEnum(String label) {
            this.label = label;
        }

        @JsonValue
        public String label() {
            return label;
        }
    }

    public enum FieldValueEnum {
        DEBUG("debug"),
        INFO("info");

        @JsonValue
        private final String value;

        FieldValueEnum(String value) {
            this.value = value;
        }
    }

    public enum CodedEnum {
        OK(200),
        NOT_FOUND(404);

        private final int code;

        CodedEnum(int code) {
            this.code = code;
        }

        @JsonValue
        public int code() {
            return code;
        }
    }

    public interface Labeled {
        @JsonValue
        String label();
    }

    public enum InheritedLabelEnum implements Labeled {
        SMALL,
        LARGE;

        @Override
        public String label() {
            return name().toLowerCase();
        }
    }

    public enum DisabledValueEnum {
        ON,
        OFF;

        @JsonValue(false)
        public String label() {
            return name().toLowerCase();
        }
    }

    public enum DisabledInheritedValueEnum implements Labeled {
        LIGHT,
        HEAVY;

        @Override
        @JsonValue(false)
        public String label() {
            return name().toLowerCase();
        }
    }

    @JsonFormat(shape = JsonFormat.Shape.NUMBER)
    public enum OrdinalEnum {
        FIRST,
        SECOND
    }

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public enum StringShapeEnum {
        LEFT,
        RIGHT
    }

    @JsonSerialize(using = ToStringSerializer.class)
    public enum SerializedEnum {
        VANILLA,
        CHOCOLATE
    }

    public record LabeledEnumRecord(LabeledEnum priority) {
    }
}
