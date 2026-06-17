package io.quarkiverse.flow.runner.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.eclipse.microprofile.openapi.models.Operation;
import org.eclipse.microprofile.openapi.models.media.MediaType;
import org.eclipse.microprofile.openapi.models.media.Schema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.quarkiverse.flow.runner.FlowRunnerConfig;
import io.serverlessworkflow.api.WorkflowReader;
import io.serverlessworkflow.api.types.Input;
import io.serverlessworkflow.api.types.SchemaInline;
import io.serverlessworkflow.api.types.SchemaUnion;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowApplication;

/**
 * Unit tests for WorkflowOpenApiFilter.
 * Tests the input schema parsing logic.
 */
@DisplayName("WorkflowOpenApiFilter Unit Tests")
class WorkflowOpenApiFilterTest {

    private WorkflowOpenApiFilter filter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        filter = new WorkflowOpenApiFilter();
        objectMapper = new ObjectMapper();

        // Mock dependencies
        filter.config = mock(FlowRunnerConfig.class);
        filter.application = mock(WorkflowApplication.class);
        filter.mapper = objectMapper;

        FlowRunnerConfig.Security securityConfig = mock(FlowRunnerConfig.Security.class);
        when(filter.config.security()).thenReturn(securityConfig);
        when(securityConfig.type()).thenReturn(FlowRunnerConfig.Security.Type.NONE);

        FlowRunnerConfig.OpenApi openApiConfig = mock(FlowRunnerConfig.OpenApi.class);
        when(filter.config.openapi()).thenReturn(openApiConfig);
        when(openApiConfig.expandWorkflows()).thenReturn(false); // We're testing the method directly

        when(filter.application.workflowDefinitions()).thenReturn(Map.of());
    }

    @Test
    @DisplayName("test_createInputSchema_with_inline_schema_parses_properties")
    void test_createInputSchema_with_inline_schema_parses_properties() throws Exception {
        // Given - Create an input schema with properties
        ObjectNode schemaDocument = objectMapper.createObjectNode();
        schemaDocument.put("type", "object");

        ObjectNode properties = schemaDocument.putObject("properties");

        ObjectNode nameProperty = properties.putObject("name");
        nameProperty.put("type", "string");
        nameProperty.put("description", "Your name");

        ObjectNode ageProperty = properties.putObject("age");
        ageProperty.put("type", "integer");
        ageProperty.put("description", "Your age");

        schemaDocument.putArray("required").add("name");

        // Create SchemaInline with the document
        SchemaInline schemaInline = new SchemaInline();
        schemaInline.setDocument(schemaDocument);

        SchemaUnion schemaUnion = new SchemaUnion();
        schemaUnion.setSchemaInline(schemaInline);

        Input workflowInput = new Input();
        workflowInput.setSchema(schemaUnion);

        // When - Call the method directly
        Schema result = filter.createInputSchema(workflowInput);

        // Then - Schema should have properties from the inline schema
        assertThat(result).isNotNull();
        assertThat(result.getType()).contains(Schema.SchemaType.OBJECT);
        assertThat(result.getProperties()).isNotNull();
        assertThat(result.getProperties()).containsKey("name");
        assertThat(result.getProperties()).containsKey("age");
        assertThat(result.getRequired()).contains("name");

        // Verify property details
        Schema nameSchema = result.getProperties().get("name");
        assertThat(nameSchema.getType()).contains(Schema.SchemaType.STRING);
        assertThat(nameSchema.getDescription()).isEqualTo("Your name");

        Schema ageSchema = result.getProperties().get("age");
        assertThat(ageSchema.getType()).contains(Schema.SchemaType.INTEGER);
        assertThat(ageSchema.getDescription()).isEqualTo("Your age");
    }

    @Test
    @DisplayName("test_createInputSchema_without_schema_returns_generic_object")
    void test_createInputSchema_without_schema_returns_generic_object() throws Exception {
        // Given - Input with no schema
        Input workflowInput = new Input();

        // When
        Schema result = filter.createInputSchema(workflowInput);

        // Then - Should return a generic object schema
        assertThat(result).isNotNull();
        assertThat(result.getType()).contains(Schema.SchemaType.OBJECT);
        assertThat(result.getDescription()).isEqualTo("Workflow input parameters");
        assertThat(result.getProperties()).isNullOrEmpty();
    }

    @Test
    @DisplayName("test_createInputSchema_with_null_input_returns_generic_object")
    void test_createInputSchema_with_null_input_returns_generic_object() throws Exception {
        // Given - null input
        Input workflowInput = null;

        // When
        Schema result = filter.createInputSchema(workflowInput);

        // Then - Should return a generic object schema
        assertThat(result).isNotNull();
        assertThat(result.getType()).contains(Schema.SchemaType.OBJECT);
        assertThat(result.getDescription()).isEqualTo("Workflow input parameters");
    }

    @Test
    @DisplayName("test_createInputSchema_with_string_schema_document_parses_correctly")
    void test_createInputSchema_with_string_schema_document_parses_correctly() throws Exception {
        // Given - Schema as JSON string
        String schemaJson = "{\"type\":\"object\",\"required\":[\"name\"],\"properties\":{\"name\":{\"type\":\"string\",\"description\":\"Your name\"}}}";

        SchemaInline schemaInline = new SchemaInline();
        schemaInline.setDocument(schemaJson);

        SchemaUnion schemaUnion = new SchemaUnion();
        schemaUnion.setSchemaInline(schemaInline);

        Input workflowInput = new Input();
        workflowInput.setSchema(schemaUnion);

        // When
        Schema result = filter.createInputSchema(workflowInput);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getType()).contains(Schema.SchemaType.OBJECT);
        assertThat(result.getProperties()).containsKey("name");
        assertThat(result.getRequired()).contains("name");
    }

    @Test
    @DisplayName("test_createWorkflowOperation_with_workflow_from_yaml")
    void test_createWorkflowOperation_with_workflow_from_yaml() throws Exception {
        // Given - Load workflow from classpath
        Workflow workflow = WorkflowReader.readWorkflowFromClasspath("emit-event.yaml");

        assertThat(workflow).isNotNull();
        assertThat(workflow.getDocument().getName()).isEqualTo("emit-event");
        assertThat(workflow.getInput()).isNotNull();
        assertThat(workflow.getInput().getSchema()).isNotNull();

        // When - Create OpenAPI operation
        Operation operation = filter.createWorkflowOperation(
                workflow.getDocument(),
                workflow.getInput(),
                false,
                false);

        // Then - Operation should have correct request body schema
        assertThat(operation).isNotNull();
        assertThat(operation.getRequestBody()).isNotNull();
        assertThat(operation.getRequestBody().getContent()).isNotNull();

        MediaType mediaType = operation.getRequestBody().getContent().getMediaType("application/json");
        assertThat(mediaType).isNotNull();
        assertThat(mediaType.getSchema()).isNotNull();

        Schema requestSchema = mediaType.getSchema();
        assertThat(requestSchema.getType()).contains(Schema.SchemaType.OBJECT);
        assertThat(requestSchema.getProperties()).isNotNull();
        assertThat(requestSchema.getProperties()).containsKey("name");
        assertThat(requestSchema.getRequired()).contains("name");

        // Verify property details
        Schema nameSchema = requestSchema.getProperties().get("name");
        assertThat(nameSchema.getType()).contains(Schema.SchemaType.STRING);
        assertThat(nameSchema.getDescription()).isEqualTo("Your name");
    }

}
