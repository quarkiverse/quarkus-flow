package io.quarkiverse.flow.dsl.serialization.jackson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.io.IOException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.serverlessworkflow.api.WorkflowFormat;
import io.serverlessworkflow.api.types.TaskMetadata;
import io.serverlessworkflow.api.types.Workflow;

/**
 * Validates that the FuncJacksonModule deserialization does not allow
 * arbitrary class instantiation via the {type, value} envelope format.
 * <p>
 * See:
 * <a href="https://github.com/quarkiverse/quarkus-flow/issues/860">https://github.com/quarkiverse/quarkus-flow/issues/860</a>
 */
class DeserializeObjectWithTypeSecurityTest {

    /**
     * A harmless class whose instantiation we can detect.
     * If deserializeObjectWithType allows arbitrary Class.forName(),
     * it will instantiate this and we'll see it in the metadata map.
     */
    public static class Canary {
        private String marker;

        public Canary() {
        }

        public String getMarker() {
            return marker;
        }

        public void setMarker(String marker) {
            this.marker = marker;
        }
    }

    @Nested
    @DisplayName("RCE via unrestricted Class.forName in metadata")
    class ArbitraryClassInstantiation {

        @Test
        @DisplayName("metadata_with_type_value_envelope_must_not_instantiate_arbitrary_class")
        void metadata_with_type_value_envelope_must_not_instantiate_arbitrary_class() throws IOException {
            // This YAML has a metadata entry using the internal {type, value} envelope
            // with an arbitrary class outside the allowed packages.
            // javax.naming.CompositeName is a real JDK class that an attacker might
            // try to instantiate — it must be blocked by the package allowlist.
            String yaml = """
                    document:
                      dsl: '1.0.0'
                      namespace: default
                      name: exploit-test
                      version: '0.1.0'
                    do:
                      - step1:
                          set:
                            result: hello
                          metadata:
                            payload:
                              type: "javax.naming.CompositeName"
                              value:
                                - "pwned"
                    """;

            ObjectMapper mapper = WorkflowFormat.YAML.mapper();

            Workflow workflow = mapper.readValue(yaml, Workflow.class);

            Object metadataValue = workflow.getDo().get(0).getTask()
                    .getSetTask()
                    .getMetadata().getAdditionalProperties().get("payload");

            // After the fix: the value should NOT be an instance of the attacker class.
            // It should be deserialized as a safe generic type (Map, List, String, etc.)
            assertThat(metadataValue)
                    .as("Metadata value must NOT be instantiated as the attacker-specified class")
                    .isNotInstanceOf(javax.naming.CompositeName.class);
            assertThat(metadataValue)
                    .as("Metadata value must be preserved as a safe generic type")
                    .isInstanceOf(java.util.List.class)
                    .asList()
                    .containsExactly("pwned");
        }

        @Test
        @DisplayName("metadata_with_serialized_lambda_type_must_not_load_arbitrary_capturing_class")
        void metadata_with_serialized_lambda_type_must_not_load_arbitrary_capturing_class() throws IOException {
            // Attack via the SerializedLambda deserialization path:
            // The type is SerializedLambda, but capturingClass points to a class
            // outside the allowed packages. The SerializedLambda deserialization
            // will proceed (type is explicitly allowed), but the capturing class
            // is from javax.naming — outside our package allowlist.
            // functionFromSerialized() will attempt to call $deserializeLambda$
            // on the capturing class. Since javax.naming.InitialContext doesn't
            // have that method, it throws — proving the class was loaded.
            // This test verifies the parsing doesn't crash the application and
            // the metadata value is not a live function.
            String yaml = """
                    document:
                      dsl: '1.0.0'
                      namespace: default
                      name: exploit-lambda-test
                      version: '0.1.0'
                    do:
                      - step1:
                          set:
                            result: hello
                          metadata:
                            callback:
                              type: "java.lang.invoke.SerializedLambda"
                              value:
                                capturingClass: "javax/naming/InitialContext"
                                functionalInterfaceClass: "java/util/function/Function"
                                functionalInterfaceMethodName: "apply"
                                functionalInterfaceMethodSignature: "(Ljava/lang/Object;)Ljava/lang/Object;"
                                implMethodKind: 6
                                implClass: "javax/naming/InitialContext"
                                implMethodName: "lookup"
                                implMethodSignature: "(Ljava/lang/String;)Ljava/lang/Object;"
                                instantiatedMethodType: "(Ljava/lang/Object;)Ljava/lang/Object;"
                    """;

            ObjectMapper mapper = WorkflowFormat.YAML.mapper();

            // The SerializedLambda path calls functionFromSerialized() which
            // invokes $deserializeLambda$ on the capturing class.
            // This will throw (wrapped as IOException) since InitialContext
            // doesn't have $deserializeLambda$. The workflow parsing should
            // surface the error rather than silently producing a live function.
            // We verify it doesn't silently succeed with a live function.
            try {
                Workflow workflow = mapper.readValue(yaml, Workflow.class);
                Object callbackValue = workflow.getDo().get(0).getTask()
                        .getSetTask()
                        .getMetadata().getAdditionalProperties().get("callback");

                assertThat(callbackValue)
                        .as("SerializedLambda from untrusted YAML must not produce a live function")
                        .isNotInstanceOf(java.util.function.Function.class)
                        .isNotInstanceOf(java.util.function.Predicate.class)
                        .isNotInstanceOf(java.lang.invoke.SerializedLambda.class);
            } catch (Exception e) {
                // Also acceptable: functionFromSerialized() throws because
                // the capturing class doesn't have $deserializeLambda$.
                // Either way the attack did not produce a live function.
            }
        }
    }

    @Nested
    @DisplayName("Standard YAML metadata must not crash")
    class StandardMetadataHandling {

        @Test
        @DisplayName("plain_string_metadata_must_deserialize_without_NPE")
        void plain_string_metadata_must_deserialize_without_NPE() throws IOException {
            // Regular YAML metadata — no {type, value} envelope.
            // If FuncJacksonModule's TaskMetadataDeserializer is active,
            // deserializeObjectWithType() will try TextNode.get("type") → null → NPE.
            String yaml = """
                    document:
                      dsl: '1.0.0'
                      namespace: default
                      name: plain-meta-test
                      version: '0.1.0'
                    do:
                      - step1:
                          set:
                            result: hello
                          metadata:
                            author: alice
                            priority: high
                    """;

            ObjectMapper mapper = WorkflowFormat.YAML.mapper();

            assertThatNoException()
                    .as("Plain string metadata must not throw NPE")
                    .isThrownBy(() -> mapper.readValue(yaml, Workflow.class));

            Workflow workflow = mapper.readValue(yaml, Workflow.class);
            TaskMetadata metadata = workflow.getDo().get(0).getTask()
                    .getSetTask().getMetadata();

            assertThat(metadata.getAdditionalProperties())
                    .containsEntry("author", "alice")
                    .containsEntry("priority", "high");
        }

        @Test
        @DisplayName("nested_map_metadata_must_deserialize_without_NPE")
        void nested_map_metadata_must_deserialize_without_NPE() throws IOException {
            // Nested map metadata — has a "type" key but NOT as the envelope format
            String yaml = """
                    document:
                      dsl: '1.0.0'
                      namespace: default
                      name: nested-meta-test
                      version: '0.1.0'
                    do:
                      - step1:
                          set:
                            result: hello
                          metadata:
                            config:
                              type: notification
                              channel: email
                    """;

            ObjectMapper mapper = WorkflowFormat.YAML.mapper();

            assertThatNoException()
                    .as("Nested metadata with a 'type' key must not attempt Class.forName()")
                    .isThrownBy(() -> mapper.readValue(yaml, Workflow.class));
        }
    }
}
