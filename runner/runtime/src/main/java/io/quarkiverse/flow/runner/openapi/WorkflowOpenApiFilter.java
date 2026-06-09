package io.quarkiverse.flow.runner.openapi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.openapi.OASFactory;
import org.eclipse.microprofile.openapi.OASFilter;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.Operation;
import org.eclipse.microprofile.openapi.models.PathItem;
import org.eclipse.microprofile.openapi.models.media.Content;
import org.eclipse.microprofile.openapi.models.media.MediaType;
import org.eclipse.microprofile.openapi.models.media.Schema;
import org.eclipse.microprofile.openapi.models.parameters.Parameter;
import org.eclipse.microprofile.openapi.models.parameters.RequestBody;
import org.eclipse.microprofile.openapi.models.responses.APIResponse;
import org.eclipse.microprofile.openapi.models.responses.APIResponses;
import org.eclipse.microprofile.openapi.models.security.SecurityScheme;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.flow.runner.FlowRunnerConfig;
import io.quarkiverse.flow.runner.security.AuthzConsts;
import io.quarkus.arc.Unremovable;
import io.quarkus.smallrye.openapi.OpenApiFilter;
import io.serverlessworkflow.api.types.Document;
import io.serverlessworkflow.api.types.Input;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowDefinitionId;

/**
 * OpenAPI filter that dynamically generates workflow-specific operations.
 * <p>
 * For each registered workflow definition, this filter creates a concrete
 * POST operation in the OpenAPI document. For example:
 *
 * <pre>
 * POST /q/flow/exec/test-namespace/hello-world/1.0.0
 * </pre>
 * <p>
 * <strong>Security Note:</strong> The OpenAPI document is publicly accessible and shows
 * all registered workflows (namespace, name, version, summary). This serves as a workflow
 * catalog. Actual execution is protected by authentication and namespace authorization,
 * returning 401/403 for unauthorized access.
 * <p>
 * This feature is controlled by {@code quarkus.flow.runner.openapi.expand-workflows}.
 * <p>
 * <strong>Execution Stage:</strong>
 * <p>
 * This filter runs at {@code RUNTIME_PER_REQUEST}, meaning it executes each time
 * the OpenAPI document is requested (e.g., when loading Swagger UI). This allows:
 * <ul>
 * <li>Dynamic workflow loading - captures workflows loaded at runtime</li>
 * <li>Config-based toggling - respects {@code expand-workflows} configuration</li>
 * <li>CDI injection - can access runtime beans like {@code FlowRunnerConfig}</li>
 * </ul>
 *
 * @see FlowRunnerConfig.OpenApi#expandWorkflows()
 */
@OpenApiFilter(stages = OpenApiFilter.RunStage.RUNTIME_PER_REQUEST)
@ApplicationScoped
@Unremovable
public class WorkflowOpenApiFilter implements OASFilter {

    @Inject
    FlowRunnerConfig config;

    @Inject
    WorkflowApplication application;

    @Inject
    ObjectMapper mapper;

    @Override
    public void filterOpenAPI(OpenAPI openAPI) {
        boolean securityEnabled = config.security().type() != FlowRunnerConfig.Security.Type.NONE;

        if (securityEnabled) {
            ensureSecurityScheme(openAPI);
            applySecurityToExistingOperations(openAPI);
        }

        if (!config.openapi().expandWorkflows()) {
            return;
        }

        Map<String, List<Map.Entry<WorkflowDefinitionId, WorkflowDefinition>>> workflowsByName = application
                .workflowDefinitions()
                .entrySet()
                .stream()
                .collect(Collectors.groupingBy(
                        entry -> entry.getKey().namespace() + ":" + entry.getKey().name()));

        application.workflowDefinitions().values().forEach(definition -> {
            Document doc = definition.workflow().getDocument();
            Workflow workflow = definition.workflow();

            String path = String.format("/q/flow/exec/%s/%s/%s",
                    doc.getNamespace(),
                    doc.getName(),
                    doc.getVersion());

            PathItem pathItem = OASFactory.createPathItem();
            Operation operation = createWorkflowOperation(doc, workflow.getInput(), false, securityEnabled);
            pathItem.POST(operation);

            openAPI.getPaths().addPathItem(path, pathItem);
        });

        // Add "latest" endpoint for each namespace:name group
        workflowsByName.forEach((namespaceName, definitions) -> {
            if (definitions.isEmpty()) {
                return;
            }

            var firstDef = definitions.get(0).getValue();
            Document doc = firstDef.workflow().getDocument();
            Workflow workflow = firstDef.workflow();

            String latestPath = String.format("/q/flow/exec/%s/%s",
                    doc.getNamespace(),
                    doc.getName());

            PathItem pathItem = OASFactory.createPathItem();
            Operation operation = createWorkflowOperation(doc, workflow.getInput(), true, securityEnabled);
            pathItem.POST(operation);

            openAPI.getPaths().addPathItem(latestPath, pathItem);
        });
    }

    /**
     * Creates an OpenAPI operation for a specific workflow.
     *
     * @param doc the workflow document (metadata)
     * @param workflowInput the workflow input schema
     * @param isLatest true if this is the "latest" endpoint (without version in path)
     * @param securityEnabled true if security is enabled (adds security requirements)
     */
    private Operation createWorkflowOperation(Document doc, Input workflowInput, boolean isLatest, boolean securityEnabled) {
        Operation operation = OASFactory.createOperation();

        // Basic metadata
        if (isLatest) {
            operation.setOperationId("execute_latest_" + sanitizeOperationId(doc.getNamespace(), doc.getName(), "latest"));
            operation.setSummary("Execute " + doc.getName() + " workflow (latest version)");
            operation.setDescription(
                    String.format("Execute the latest version of workflow %s in namespace %s. %s",
                            doc.getName(),
                            doc.getNamespace(),
                            doc.getSummary() != null && !doc.getSummary().isBlank() ? doc.getSummary() : ""));
        } else {
            operation.setOperationId("execute_" + sanitizeOperationId(doc.getNamespace(), doc.getName(), doc.getVersion()));
            operation.setSummary("Execute " + doc.getName() + " workflow");
            if (doc.getSummary() != null && !doc.getSummary().isBlank()) {
                operation.setDescription(doc.getSummary());
            } else {
                operation.setDescription(String.format("Execute workflow %s:%s (version %s) in namespace %s",
                        doc.getName(), doc.getVersion(), doc.getVersion(), doc.getNamespace()));
            }
        }

        // Tags
        operation.addTag("Workflow Execution");

        if (securityEnabled) {
            operation.addSecurityRequirement(OASFactory.createSecurityRequirement()
                    .addScheme("BearerAuth", List.of(AuthzConsts.ROLE_ADMIN, AuthzConsts.ROLE_INVOKER)));
        }

        Parameter waitParam = OASFactory.createParameter();
        waitParam.setName("wait");
        waitParam.setIn(Parameter.In.QUERY);
        waitParam.setDescription("Wait for workflow completion (default: false). " +
                "Use wait=true for synchronous execution (returns workflow result). " +
                "Use wait=false for asynchronous execution (returns execution metadata immediately).");
        waitParam.setRequired(false);
        Schema waitSchema = OASFactory.createSchema();
        waitSchema.setType(Collections.singletonList(Schema.SchemaType.BOOLEAN));
        waitSchema.setDefaultValue(false);
        waitParam.setSchema(waitSchema);
        operation.addParameter(waitParam);

        RequestBody requestBody = OASFactory.createRequestBody();
        requestBody.setDescription("Workflow execution input data");
        requestBody.setRequired(false);

        Content content = OASFactory.createContent();
        MediaType mediaType = OASFactory.createMediaType();

        Schema schema = createInputSchema(workflowInput);

        mediaType.setSchema(schema);
        content.addMediaType("application/json", mediaType);
        requestBody.setContent(content);
        operation.setRequestBody(requestBody);

        // Responses
        APIResponses responses = OASFactory.createAPIResponses();

        APIResponse successResponse = OASFactory.createAPIResponse();
        successResponse.setDescription(
                "Workflow execution completed. Returned when: (1) wait=true and workflow finished, or (2) wait=false but workflow completed before response.");
        responses.addAPIResponse("200", successResponse);

        APIResponse acceptedResponse = OASFactory.createAPIResponse();
        acceptedResponse.setDescription(
                "Workflow execution accepted for processing (wait=false and still running). Check workflowOutput field - will be null if pending.");
        responses.addAPIResponse("202", acceptedResponse);

        APIResponse unauthorizedResponse = OASFactory.createAPIResponse();
        unauthorizedResponse.setDescription("Authentication required");
        responses.addAPIResponse("401", unauthorizedResponse);

        APIResponse forbiddenResponse = OASFactory.createAPIResponse();
        forbiddenResponse.setDescription("Access denied to namespace: " + doc.getNamespace());
        responses.addAPIResponse("403", forbiddenResponse);

        APIResponse notFoundResponse = OASFactory.createAPIResponse();
        notFoundResponse.setDescription("Workflow definition not found");
        responses.addAPIResponse("404", notFoundResponse);

        operation.setResponses(responses);

        return operation;
    }

    /**
     * Creates input schema for the workflow.
     * <p>
     * If the workflow defines an input schema inline, parses it and builds
     * an OpenAPI schema from the JSON Schema properties.
     * Otherwise, returns a generic object schema.
     *
     * @param workflowInput the workflow input schema
     * @return OpenAPI schema for the workflow input
     */
    private Schema createInputSchema(Input workflowInput) {
        Schema schema = OASFactory.createSchema();

        // Check if workflow has inline schema defined
        if (workflowInput != null && workflowInput.getSchema() != null
                && workflowInput.getSchema().getSchemaInline() != null) {
            Object schemaInline = workflowInput.getSchema().getSchemaInline();

            try {
                // Parse the inline schema (can be JsonNode or String)
                JsonNode schemaNode = parseSchemaInline(schemaInline);

                if (schemaNode != null) {
                    // Extract properties from JSON Schema and map to OpenAPI schema
                    return buildSchemaFromJsonNode(schemaNode);
                }
            } catch (Exception e) {
                // If parsing fails, fall back to generic schema
                schema.setDescription("Workflow input parameters (schema parsing failed: " + e.getMessage() + ")");
            }
        } else {
            schema.setDescription("Workflow input parameters");
        }

        // Fallback: generic object schema
        schema.setType(Collections.singletonList(Schema.SchemaType.OBJECT));

        return schema;
    }

    /**
     * Parses the schema inline object (JsonNode or String) to JsonNode.
     */
    private JsonNode parseSchemaInline(Object schemaInline) throws Exception {
        if (schemaInline instanceof JsonNode) {
            return (JsonNode) schemaInline;
        } else if (schemaInline instanceof String) {
            return mapper.readTree((String) schemaInline);
        }
        return null;
    }

    /**
     * Builds an OpenAPI Schema from a JSON Schema JsonNode.
     * <p>
     * This is a simplified conversion that handles basic JSON Schema properties.
     * Full JSON Schema to OpenAPI conversion would require more comprehensive mapping.
     */
    private Schema buildSchemaFromJsonNode(JsonNode schemaNode) {
        Schema schema = OASFactory.createSchema();

        // Type
        if (schemaNode.has("type")) {
            String type = schemaNode.get("type").asText();
            schema.setType(Collections.singletonList(Schema.SchemaType.valueOf(type.toUpperCase())));
        }

        // Description
        if (schemaNode.has("description")) {
            schema.setDescription(schemaNode.get("description").asText());
        }

        // Properties (for object types)
        if (schemaNode.has("properties")) {
            JsonNode propertiesNode = schemaNode.get("properties");

            Map<String, Schema> finalProperties = new LinkedHashMap<>();
            propertiesNode.properties().forEach(entry -> {
                String propName = entry.getKey();
                JsonNode propSchema = entry.getValue();
                Schema propSchemaObj = buildSchemaFromJsonNode(propSchema);
                finalProperties.put(propName, propSchemaObj);
            });

            schema.setProperties(finalProperties);
        }

        // Required fields
        if (schemaNode.has("required") && schemaNode.get("required").isArray()) {
            List<String> required = new ArrayList<>();
            schemaNode.get("required").forEach(node -> required.add(node.asText()));
            schema.setRequired(required);
        }

        return schema;
    }

    /**
     * Applies security requirements to all existing Flow Runner operations.
     * This ensures static JAX-RS endpoints also show the lock icon in Swagger UI.
     */
    private void applySecurityToExistingOperations(OpenAPI openAPI) {
        if (openAPI.getPaths() == null) {
            return;
        }

        openAPI.getPaths().getPathItems().forEach((path, pathItem) -> {
            if (path.startsWith("/q/flow/")) {
                applySecurityToOperation(pathItem.getGET());
                applySecurityToOperation(pathItem.getPOST());
                applySecurityToOperation(pathItem.getPUT());
                applySecurityToOperation(pathItem.getDELETE());
                applySecurityToOperation(pathItem.getPATCH());
            }
        });
    }

    /**
     * Applies security requirement to a single operation if it doesn't already have one.
     */
    private void applySecurityToOperation(Operation operation) {
        if (operation == null) {
            return;
        }

        if (operation.getSecurity() == null || operation.getSecurity().isEmpty()) {
            operation.addSecurityRequirement(OASFactory.createSecurityRequirement()
                    .addScheme("BearerAuth", List.of(AuthzConsts.ROLE_ADMIN, AuthzConsts.ROLE_INVOKER)));
        }
    }

    /**
     * Ensures the BearerAuth security scheme is defined in the OpenAPI document.
     * This is required for Swagger UI to show the "Authorize" button.
     */
    private void ensureSecurityScheme(OpenAPI openAPI) {
        if (openAPI.getComponents() == null) {
            openAPI.setComponents(OASFactory.createComponents());
        }

        if (openAPI.getComponents().getSecuritySchemes() == null
                || !openAPI.getComponents().getSecuritySchemes().containsKey("BearerAuth")) {

            SecurityScheme bearerAuth = OASFactory.createSecurityScheme();
            bearerAuth.setType(SecurityScheme.Type.HTTP);
            bearerAuth.setScheme("bearer");
            bearerAuth.setBearerFormat("JWT or API Key");
            bearerAuth.setDescription(
                    "Authentication using Bearer token. Supports: (1) JWT tokens from OIDC provider (when security.type=OIDC), or (2) API keys (when security.type=API_KEY). "
                            +
                            "Include the token in the Authorization header: 'Authorization: Bearer <token>'. " +
                            "Required roles: flow-admin (full access) or flow-invoker (execution only).");

            openAPI.getComponents().addSecurityScheme("BearerAuth", bearerAuth);
        }
    }

    /**
     * Sanitizes namespace, name, and version for use in operation ID.
     */
    private String sanitizeOperationId(String namespace, String name, String version) {
        return (namespace + "_" + name + "_" + version)
                .replaceAll("[^a-zA-Z0-9_]", "_");
    }
}
