# ADR: Model Context Protocol (MCP) Integration for Quarkus Flow

**Date:** 2026-06-06

**Status:** Proposed

**Context:** Design specification for adding MCP server capabilities to Quarkus Flow

---

## Executive Summary

This ADR proposes MCP integration for Quarkus Flow that goes beyond simple workflow execution - it enables **AI-powered workflow composition**. AI models can:

1. **Execute registered workflows** via MCP tools (standard integration)
2. **Generate workflows dynamically** from natural language descriptions (Phase 5)
3. **Discover and compose functions** from your organization's service catalog (Phase 6)

The result: AI models orchestrate your internal infrastructure (databases, CRMs, messaging, custom services) through workflows they compose on-demand, executing operations that are fundamentally impossible for AI systems alone (access internal services, maintain state, trigger side effects, run with proper security context).

## Context

The Model Context Protocol (MCP) is an open-source standard protocol (governed by the Linux Foundation's Agentic AI Foundation since December 2025) that enables AI applications to connect to external data sources, tools, and workflows through a standardized interface. MCP uses JSON-RPC 2.0 and defines three core primitives: tools (executable functions), resources (data sources), and prompts (reusable templates).

Quarkus Flow is a workflow engine based on the CNCF Serverless Workflow specification. Currently, there is no standard way for AI systems to discover, execute, and monitor Quarkus Flow workflows. Adding MCP support would enable:

1. **AI-native workflow orchestration**: LangChain4j agents and other AI systems could orchestrate Quarkus Flow workflows
2. **Standard integration**: Any MCP-compatible client (Claude Desktop, custom AI applications) could use Quarkus Flow
3. **Composability**: Workflows would become building blocks in larger AI systems
4. **Enhanced observability**: AI systems could query workflow state, history, and definitions

### Research Findings

The Quarkiverse already has a mature MCP server extension:
- **quarkus-mcp-server** (v1.13.0): https://github.com/quarkiverse/quarkus-mcp-server
- Annotation-driven API (`@Tool`, `@Resource`, `@Prompt`)
- Full MCP 2025-11-25 specification compliance
- Support for STDIO (local) and HTTP (remote) transports
- Native compilation support, testing library (McpAssured), CDI integration
- 7 production servers already built: JDBC, Filesystem, Kubernetes, Containers, JVM Insight, JavaFX, Wolfram

### Quarkus Flow Architecture Review

Based on codebase analysis, Quarkus Flow has the following key components:

1. **`Flow`**: Abstract base class that users extend to define workflows (CDI beans, typically `@ApplicationScoped`)
   - Implements `Flowable` interface
   - Provides `descriptor()` method to define workflow structure using CNCF Serverless Workflow DSL
   - Provides convenience methods: `instance()`, `instance(input)`, `startInstance()`, `startInstance(input)`
   - Each Flow instance accesses its `WorkflowDefinition` via CDI injection

2. **`WorkflowApplication`**: Central SDK object from `io.serverlessworkflow.impl.WorkflowApplication`
   - Created once per application via `WorkflowApplicationCreator`
   - Singleton CDI bean
   - Provides `workflowDefinitions()` map: `Map<WorkflowDefinitionId, WorkflowDefinition>`
   - Factory for creating `WorkflowDefinition` instances from `Workflow` descriptors
   - Configured with providers (HTTP client, event consumers/publishers, secret managers, etc.)

3. **`WorkflowDefinitionId`**: SDK identifier class with namespace, name, and version
   - Format: `WorkflowDefinitionId(namespace, name, version)`
   - Used as key in `WorkflowApplication.workflowDefinitions()` map
   - Supports versioned workflow deployments

4. **`WorkflowDefinition`**: SDK class from `io.serverlessworkflow.impl.WorkflowDefinition`
   - Created at build time from Flow descriptors
   - Registered in `WorkflowApplication` with `WorkflowDefinitionId` as key
   - Factory for `WorkflowInstance` objects
   - Contains workflow metadata and application context

5. **`WorkflowInstance`**: SDK class representing a single workflow execution
   - Created via `definition.instance(input)`
   - Calling `start()` executes the workflow, returns `CompletableFuture<WorkflowModel>`
   - Each instance is ephemeral (not stored beyond execution by default)

6. **Flow Runner Module** (incoming PR): REST API for workflow execution
   - Pattern: `/{namespace}/{name}` (latest version) or `/{namespace}/{name}/{version}` (specific)
   - Uses `WorkflowApplication.workflowDefinitions()` for workflow lookup
   - Latest version resolution via `WorkflowVersionComparator`
   - Async/sync execution modes (`wait=true/false` query parameter)
   - Namespace-based authorization support

**Key Insights**:
- Workflow identification: **namespace, name, version** (not just name)
- Discovery via `application.workflowDefinitions()` map (primary) or `Instance<Flowable>` (secondary)
- Runner module establishes patterns for workflow lookup and execution that MCP should align with
- Opportunity for shared service layer between runner and MCP

## Decision

We will create a new `quarkus-flow-mcp` extension that exposes Quarkus Flow workflows through the Model Context Protocol by leveraging the existing `quarkus-mcp-server` extension. To ensure consistency between the runner and MCP, we will also create a shared `quarkus-flow-api` module containing common workflow lookup and execution services.

### Key Design Principles

1. **Shared Service Layer**: Create `quarkus-flow-api` module with `WorkflowDefinitionService` and `WorkflowExecutionService` to eliminate code duplication between runner and MCP
2. **Consistent Identification**: Use namespace/name/version triple for all workflow operations (aligned with runner API from #52)
3. **Reuse Existing Patterns**: Leverage `WorkflowVersionComparator` for latest version resolution, same URI patterns as runner
4. **Single Source of Truth**: Workflow lookup, version resolution, and execution logic lives in shared services
5. **AI-Native Design**: Enable AI models to not only execute but also generate and compose workflows:
   - Dynamic YAML workflow generation and validation
   - Function catalog discovery (CDI beans, LangChain4j tools, community catalog)
   - AI-powered orchestration of organization-specific services
6. **Future-Proof**: Shared API enables future integrations (GraphQL, gRPC, etc.) to reuse same core logic

### Architecture

The extension will expose Quarkus Flow through MCP's three primitives:

1. **Tools**: Execute and validate workflows
   - `execute_workflow(namespace, name, version, input)` - Execute a registered workflow
   - `list_workflows(namespace)` - List available workflows, optionally filtered by namespace
   - `execute_yaml_workflow(yamlDefinition, input)` - Execute dynamically generated YAML workflows
   - `validate_yaml_workflow(yamlDefinition)` - Validate YAML syntax without execution
   - Uses shared `WorkflowExecutionService` and `WorkflowDefinitionService`

2. **Resources**: Query workflow metadata and specifications
   - `workflow://definitions` - List all registered workflow definitions
   - `workflow://definitions/{namespace}/{name}` - Get latest version of a workflow
   - `workflow://definitions/{namespace}/{name}/{version}` - Get specific version of a workflow
   - `workflow://spec/schema` - CNCF Serverless Workflow JSON Schema (for AI generation)
   - `workflow://spec/examples` - Curated YAML workflow examples
   - Each definition includes input schema (JSON Schema) in the response

3. **Prompts**: Assist with workflow operations and generation
   - `construct_workflow_input` - Help construct valid input for a workflow
   - `analyze_workflow_schema` - Help understand workflow input requirements
   - `generate_workflow` - Guide AI through creating valid YAML workflow definitions

### Module Structure

```
quarkus-flow/
├── api/                                     # NEW: Shared services module
│   └── runtime/
│       ├── WorkflowDefinitionService.java  # Shared workflow lookup logic
│       ├── WorkflowExecutionService.java   # Shared execution logic
│       ├── WorkflowNotFoundException.java  # Common exception
│       └── model/
│           └── WorkflowDefinitionInfo.java # Shared model (used by runner & MCP)
├── runner/                                  # Uses api module
│   └── runtime/
│       └── resources/
│           ├── DefinitionResource.java     # Uses WorkflowDefinitionService
│           └── RunnerExecResource.java     # Uses WorkflowExecutionService
├── mcp/                                     # NEW: MCP integration
│   ├── runtime/
│   │   ├── FlowMcpToolProvider.java        # Workflow execution tools (uses api)
│   │   ├── FlowMcpResourceProvider.java    # Workflow metadata resources (uses api)
│   │   ├── FlowMcpPromptProvider.java      # Workflow assistance prompts
│   │   ├── McpConfiguration.java           # Runtime configuration
│   │   └── model/
│   │       └── WorkflowExecutionResult.java # MCP-specific result model
│   ├── deployment/
│   │   ├── FlowMcpProcessor.java           # Build-time registration
│   │   └── DevUiMcpConsole.java            # Dev UI integration
│   └── integration-tests/
│       ├── FlowMcpServerIT.java
│       └── resources/
│           └── test-workflows/
└── pom.xml (update with new modules)
```

### Dependencies

```xml
<!-- MCP module dependencies -->
<dependency>
    <groupId>io.quarkiverse.mcp</groupId>
    <artifactId>quarkus-mcp-server-http</artifactId>
    <version>1.13.0</version>
</dependency>

<!-- Optional: STDIO transport for local development -->
<dependency>
    <groupId>io.quarkiverse.mcp</groupId>
    <artifactId>quarkus-mcp-server-stdio</artifactId>
    <version>1.13.0</version>
    <optional>true</optional>
</dependency>

<!-- Shared API module (used by both runner and MCP) -->
<dependency>
    <groupId>io.quarkiverse.flow</groupId>
    <artifactId>quarkus-flow-api</artifactId>
    <version>${project.version}</version>
</dependency>

<!-- Core Flow dependencies -->
<dependency>
    <groupId>io.quarkiverse.flow</groupId>
    <artifactId>quarkus-flow-core</artifactId>
    <version>${project.version}</version>
</dependency>
```

### Implementation Approach

#### Phase 0: Shared API Module (Foundation)

**Goal**: Create reusable services for workflow lookup and execution, shared between runner and MCP

**1. WorkflowDefinitionService** - Unified workflow lookup logic:

```java
package io.quarkiverse.flow.api;

import java.util.Optional;
import java.util.stream.Stream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.flow.internal.WorkflowVersionComparator;
import io.serverlessworkflow.api.types.Document;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowDefinitionId;

/**
 * Shared service for querying workflow definitions.
 * Used by both runner REST endpoints and MCP tools.
 */
@ApplicationScoped
public class WorkflowDefinitionService {
    
    @Inject
    WorkflowApplication application;
    
    /**
     * Find workflow by namespace, name, and optional version.
     * If version is null, returns the latest version using WorkflowVersionComparator.
     */
    public Optional<WorkflowDefinition> findWorkflow(String namespace, String name, String version) {
        if (version != null) {
            // Specific version requested
            WorkflowDefinitionId id = new WorkflowDefinitionId(namespace, name, version);
            return Optional.ofNullable(application.workflowDefinitions().get(id));
        } else {
            // Latest version (same pattern as runner)
            return application.workflowDefinitions().entrySet().stream()
                .filter(entry -> namespace.equals(entry.getKey().namespace()) && 
                                 name.equals(entry.getKey().name()))
                .max(new WorkflowVersionComparator())
                .map(Map.Entry::getValue);
        }
    }
    
    /**
     * List all workflow definitions, optionally filtered by namespace.
     */
    public Stream<WorkflowDefinition> listWorkflows(String namespace) {
        Stream<WorkflowDefinition> definitions = application.workflowDefinitions().values().stream();
        
        if (namespace != null) {
            definitions = definitions.filter(def -> 
                namespace.equals(def.workflow().getDocument().getNamespace()));
        }
        
        return definitions;
    }
    
    /**
     * Check if a workflow exists.
     */
    public boolean exists(String namespace, String name, String version) {
        return findWorkflow(namespace, name, version).isPresent();
    }
}
```

**2. WorkflowExecutionService** - Unified execution logic:

```java
package io.quarkiverse.flow.api;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowInstance;
import io.serverlessworkflow.impl.WorkflowModel;

/**
 * Shared service for executing workflows.
 * Used by both runner REST endpoints and MCP tools.
 */
@ApplicationScoped
public class WorkflowExecutionService {
    
    @Inject
    WorkflowDefinitionService definitionService;
    
    /**
     * Execute a workflow synchronously (blocking until completion).
     * Used by MCP tools and runner with wait=true.
     */
    public WorkflowModel executeSync(String namespace, String name, String version, 
                                      Map<String, Object> input) {
        WorkflowDefinition definition = definitionService.findWorkflow(namespace, name, version)
            .orElseThrow(() -> new WorkflowNotFoundException(namespace, name, version));
        
        WorkflowInstance instance = definition.instance(input != null ? input : Map.of());
        return instance.start().join();  // Block until completion
    }
    
    /**
     * Execute a workflow asynchronously (returns future immediately).
     * Used by runner with wait=false.
     */
    public CompletableFuture<WorkflowModel> executeAsync(String namespace, String name, String version,
                                                          Map<String, Object> input) {
        WorkflowDefinition definition = definitionService.findWorkflow(namespace, name, version)
            .orElseThrow(() -> new WorkflowNotFoundException(namespace, name, version));
        
        WorkflowInstance instance = definition.instance(input != null ? input : Map.of());
        return instance.start();  // Returns future immediately
    }
    
    /**
     * Execute with explicit WorkflowDefinition (for runner's optimization).
     */
    public WorkflowExecutionResult execute(WorkflowDefinition definition, 
                                            Map<String, Object> input) {
        WorkflowInstance instance = definition.instance(input != null ? input : Map.of());
        CompletableFuture<WorkflowModel> future = instance.start();
        
        return new WorkflowExecutionResult(instance, future);
    }
}
```

**3. Shared Model**:

```java
package io.quarkiverse.flow.api.model;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.serverlessworkflow.api.types.Document;
import io.serverlessworkflow.impl.WorkflowDefinition;

/**
 * Workflow definition metadata.
 * Shared between runner and MCP for consistent representation.
 */
public record WorkflowDefinitionInfo(
    String namespace,
    String name,
    String version,
    String summary,
    Map<String, Object> inputSchema
) {
    public static WorkflowDefinitionInfo from(WorkflowDefinition definition, ObjectMapper mapper) {
        Document doc = definition.workflow().getDocument();
        return new WorkflowDefinitionInfo(
            doc.getNamespace(),
            doc.getName(),
            doc.getVersion(),
            doc.getSummary(),
            extractInputSchema(definition.workflow(), mapper)
        );
    }
    
    private static Map<String, Object> extractInputSchema(Workflow w, ObjectMapper mapper) {
        if (w.getInput() == null || w.getInput().getSchema() == null) {
            return null;
        }
        
        var schemaUnion = w.getInput().getSchema();
        var inline = schemaUnion.getSchemaInline();
        if (inline == null || inline.getDocument() == null) {
            return null;
        }
        
        Object doc = inline.getDocument();
        if (doc instanceof Map) {
            return (Map<String, Object>) doc;
        }
        
        // Convert ObjectNode or other types to Map
        return mapper.convertValue(doc, Map.class);
    }
}
```

**4. Exception**:

```java
package io.quarkiverse.flow.api;

public class WorkflowNotFoundException extends RuntimeException {
    private final String namespace;
    private final String name;
    private final String version;
    
    public WorkflowNotFoundException(String namespace, String name, String version) {
        super(String.format("Workflow not found: %s/%s%s", 
            namespace, name, version != null ? "/" + version : " (latest)"));
        this.namespace = namespace;
        this.name = name;
        this.version = version;
    }
    
    // Getters...
}
```

#### Phase 1: Basic Tools (MVP)

**Goal**: Expose workflow execution via MCP tools using shared services

```java
package io.quarkiverse.flow.mcp;

import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.flow.api.WorkflowDefinitionService;
import io.quarkiverse.flow.api.WorkflowExecutionService;
import io.quarkiverse.flow.api.WorkflowNotFoundException;
import io.quarkiverse.flow.api.model.WorkflowDefinitionInfo;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.serverlessworkflow.impl.WorkflowModel;

@ApplicationScoped
public class FlowMcpToolProvider {
    
    @Inject
    WorkflowExecutionService executionService;  // Shared with runner
    
    @Inject
    WorkflowDefinitionService definitionService; // Shared with runner
    
    @Inject
    ObjectMapper objectMapper;
    
    /**
     * Execute a workflow by namespace, name, and optional version.
     * Uses shared WorkflowExecutionService (same logic as runner).
     * MCP tools are blocking, so this uses executeSync.
     */
    @Tool(description = "Execute a Quarkus Flow workflow and wait for completion")
    public WorkflowExecutionResult executeWorkflow(
        @ToolArg(description = "Workflow namespace") String namespace,
        @ToolArg(description = "Workflow name") String name,
        @ToolArg(description = "Workflow version (optional, uses latest if omitted)") String version,
        @ToolArg(description = "Input data as JSON object") Map<String, Object> input
    ) {
        try {
            // Uses shared service - same logic as runner
            WorkflowModel model = executionService.executeSync(namespace, name, version, input);
            
            String actualVersion = version != null ? version : "latest";
            return new WorkflowExecutionResult(
                namespace, name, actualVersion,
                "COMPLETED",
                model.asJavaObject()
            );
        } catch (WorkflowNotFoundException e) {
            throw new McpException(-32602, e.getMessage());
        } catch (Exception e) {
            String actualVersion = version != null ? version : "latest";
            return new WorkflowExecutionResult(
                namespace, name, actualVersion,
                "FAILED",
                Map.of("error", e.getMessage(), "type", e.getClass().getSimpleName())
            );
        }
    }
    
    /**
     * List all workflow definitions, optionally filtered by namespace.
     * Uses shared WorkflowDefinitionService.
     */
    @Tool(description = "List all available Quarkus Flow workflow definitions")
    public List<WorkflowDefinitionInfo> listWorkflows(
        @ToolArg(description = "Filter by namespace (optional)") String namespace
    ) {
        return definitionService.listWorkflows(namespace)
            .map(def -> WorkflowDefinitionInfo.from(def, objectMapper))
            .toList();
    }
}
```

**MCP-specific Model:**

```java
package io.quarkiverse.flow.mcp.model;

/**
 * MCP-specific execution result.
 * Note: WorkflowDefinitionInfo is shared with runner (in api module).
 */
public record WorkflowExecutionResult(
    String namespace,
    String name,
    String version,
    String status,  // COMPLETED, FAILED
    Object output   // workflow result or error details
) {
    public String workflowId() {
        return namespace + "/" + name + "/" + version;
    }
}
```

#### Phase 2: Resources

**Goal**: Expose workflow metadata as queryable resources (aligned with runner URIs)

```java
package io.quarkiverse.flow.mcp;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.flow.api.WorkflowDefinitionService;
import io.quarkiverse.flow.api.model.WorkflowDefinitionInfo;
import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.ResourceTemplate;
import io.quarkiverse.mcp.server.ResourceTemplateArg;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.serverlessworkflow.impl.WorkflowDefinition;

@ApplicationScoped
public class FlowMcpResourceProvider {
    
    @Inject
    WorkflowDefinitionService definitionService;  // Shared with runner
    
    @Inject
    ObjectMapper objectMapper;
    
    /**
     * List all workflow definitions.
     */
    @Resource(
        uri = "workflow://definitions",
        description = "List of all available workflow definitions with input schemas",
        mimeType = "application/json")
    public TextResourceContents listDefinitions() {
        List<WorkflowDefinitionInfo> defs = definitionService.listWorkflows(null)
            .map(def -> WorkflowDefinitionInfo.from(def, objectMapper))
            .toList();
        
        return TextResourceContents.create(
            "workflow://definitions",
            toJson(defs)
        );
    }
    
    /**
     * Get latest version of a workflow definition.
     * Pattern: workflow://definitions/{namespace}/{name}
     * Same URI pattern as runner: /{namespace}/{name} (latest)
     */
    @ResourceTemplate(
        uriTemplate = "workflow://definitions/{namespace}/{name}",
        description = "Workflow definition (latest version) by namespace and name",
        mimeType = "application/json")
    public TextResourceContents getLatestDefinition(
        @ResourceTemplateArg String namespace,
        @ResourceTemplateArg String name
    ) {
        // Uses shared service - same lookup as runner
        WorkflowDefinition definition = definitionService.findWorkflow(namespace, name, null)
            .orElseThrow(() -> new RuntimeException(
                String.format("Workflow not found: %s/%s", namespace, name)));
        
        return TextResourceContents.create(
            String.format("workflow://definitions/%s/%s", namespace, name),
            toJson(WorkflowDefinitionInfo.from(definition, objectMapper))
        );
    }
    
    /**
     * Get specific version of a workflow definition.
     * Pattern: workflow://definitions/{namespace}/{name}/{version}
     * Same URI pattern as runner: /{namespace}/{name}/{version}
     */
    @ResourceTemplate(
        uriTemplate = "workflow://definitions/{namespace}/{name}/{version}",
        description = "Workflow definition (specific version) by namespace, name, and version",
        mimeType = "application/json")
    public TextResourceContents getDefinition(
        @ResourceTemplateArg String namespace,
        @ResourceTemplateArg String name,
        @ResourceTemplateArg String version
    ) {
        // Uses shared service - same lookup as runner
        WorkflowDefinition definition = definitionService.findWorkflow(namespace, name, version)
            .orElseThrow(() -> new RuntimeException(
                String.format("Workflow not found: %s/%s/%s", namespace, name, version)));
        
        return TextResourceContents.create(
            String.format("workflow://definitions/%s/%s/%s", namespace, name, version),
            toJson(WorkflowDefinitionInfo.from(definition, objectMapper))
        );
    }
    
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize to JSON", e);
        }
    }
}
```

**MVP Scope**: The MVP does not track workflow execution state (running instances, history). Future enhancements could add:
- Integration with Flow's optional persistence layer
- Resources like `workflow://executions/{id}` for querying execution state
- Subscription support for real-time execution updates

#### Phase 3: Prompts

**Goal**: Provide helpful prompt templates for workflow operations

```java
package io.quarkiverse.flow.mcp;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.flow.api.WorkflowDefinitionService;
import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptArg;
import io.quarkiverse.mcp.server.PromptMessage;
import io.serverlessworkflow.impl.WorkflowDefinition;

@ApplicationScoped
public class FlowMcpPromptProvider {
    
    @Inject
    WorkflowDefinitionService definitionService;  // Shared with runner
    
    @Inject
    ObjectMapper objectMapper;
    
    /**
     * Help construct valid input for a workflow.
     */
    @Prompt(
        name = "construct_workflow_input",
        description = "Help construct valid input data for a Quarkus Flow workflow")
    public PromptMessage constructWorkflowInput(
        @PromptArg(description = "Workflow namespace") String namespace,
        @PromptArg(description = "Workflow name") String name,
        @PromptArg(description = "Workflow version (optional)") String version
    ) {
        WorkflowDefinition definition = definitionService.findWorkflow(namespace, name, version)
            .orElse(null);
        
        if (definition == null) {
            return PromptMessage.withUserRole(
                String.format("Error: Workflow not found: %s/%s%s", 
                    namespace, name, version != null ? "/" + version : "")
            );
        }
        
        Workflow workflow = definition.workflow();
        
        String name = workflow.getDocument().getName();
        String summary = workflow.getDocument().getSummary();
        Map<String, Object> schema = extractInputSchema(workflow);
        
        String promptText;
        if (schema != null) {
            try {
                String schemaJson = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(schema);
                
                promptText = String.format("""
                    Help me construct valid input for the "%s" workflow.
                    
                    **Description**: %s
                    
                    **Input JSON Schema**:
                    ```json
                    %s
                    ```
                    
                    Please help me create input data that:
                    1. Matches the schema requirements
                    2. Contains realistic example values
                    3. Covers the main use case
                    
                    Return the input as a JSON object.
                    """,
                    name,
                    summary != null ? summary : "No description available",
                    schemaJson
                );
            } catch (Exception e) {
                promptText = String.format(
                    "Workflow '%s' has a schema but it couldn't be formatted: %s",
                    name, e.getMessage()
                );
            }
        } else {
            promptText = String.format("""
                Help me construct input for the "%s" workflow.
                
                **Description**: %s
                
                **Note**: This workflow has no defined input schema, so it may accept:
                - An empty object `{}`
                - Any JSON object with workflow-specific fields
                
                Please suggest what input this workflow might expect based on its name and description.
                """,
                name,
                summary != null ? summary : "No description available"
            );
        }
        
        return PromptMessage.withUserRole(promptText);
    }
    
    /**
     * Analyze a workflow's schema to understand its requirements.
     */
    @Prompt(
        name = "analyze_workflow_schema",
        description = "Analyze a workflow's input schema and explain its requirements")
    public PromptMessage analyzeWorkflowSchema(
        @PromptArg(description = "Workflow namespace") String namespace,
        @PromptArg(description = "Workflow name") String name,
        @PromptArg(description = "Workflow version (optional)") String version
    ) {
        WorkflowDefinition definition = definitionService.findWorkflow(namespace, name, version)
            .orElse(null);
        
        if (definition == null) {
            return PromptMessage.withUserRole(
                String.format("Error: Workflow not found: %s/%s%s", 
                    namespace, name, version != null ? "/" + version : "")
            );
        }
        
        Workflow workflow = definition.workflow();
        
        Map<String, Object> schema = extractInputSchema(workflow);
        if (schema == null) {
            return PromptMessage.withUserRole(
                String.format("Workflow '%s' has no input schema defined.", 
                    workflow.getDocument().getName())
            );
        }
        
        try {
            String schemaJson = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(schema);
            
            String promptText = String.format("""
                Analyze this workflow's input schema and explain its requirements in simple terms:
                
                **Workflow**: %s
                **Description**: %s
                
                **Input Schema**:
                ```json
                %s
                ```
                
                Please provide:
                1. A plain-English summary of what input this workflow expects
                2. Which fields are required vs optional
                3. The expected data types for each field
                4. Any validation rules or constraints
                5. An example of valid input
                """,
                workflow.getDocument().getName(),
                workflow.getDocument().getSummary() != null ? 
                    workflow.getDocument().getSummary() : "No description available",
                schemaJson
            );
            
            return PromptMessage.withUserRole(promptText);
        } catch (Exception e) {
            return PromptMessage.withUserRole(
                "Error analyzing schema: " + e.getMessage()
            );
        }
    }
    
    private Map<String, Object> extractInputSchema(Workflow w) {
        if (w.getInput() == null || w.getInput().getSchema() == null) {
            return null;
        }
        
        var schemaUnion = w.getInput().getSchema();
        var inline = schemaUnion.getSchemaInline();
        if (inline == null || inline.getDocument() == null) {
            return null;
        }
        
        Object doc = inline.getDocument();
        if (doc instanceof Map) {
            return (Map<String, Object>) doc;
        }
        
        return objectMapper.convertValue(doc, Map.class);
    }
}
```

#### Phase 4: Advanced Features (Future)

**1. Dynamic Workflow Generation from YAML**

Enable AI models to generate workflow definitions on-the-fly and execute them:

```java
@Tool(description = "Execute a dynamically generated workflow from YAML definition")
public WorkflowExecutionResult executeYamlWorkflow(
    @ToolArg(description = "Workflow definition in YAML format (CNCF Serverless Workflow spec)") String yamlDefinition,
    @ToolArg(description = "Input data for the workflow") Map<String, Object> input
) {
    try {
        // Parse YAML into Workflow object
        Workflow workflow = WorkflowReader.readWorkflow(
            new ByteArrayInputStream(yamlDefinition.getBytes()), 
            WorkflowFormat.YAML
        );
        
        // Create ad-hoc definition (not persisted)
        WorkflowDefinition definition = application.workflowDefinition(workflow);
        WorkflowInstance instance = definition.instance(input);
        WorkflowModel result = instance.start().join();
        
        return new WorkflowExecutionResult(
            workflow.getDocument().getNamespace(),
            workflow.getDocument().getName(),
            workflow.getDocument().getVersion(),
            "COMPLETED",
            result.asJavaObject()
        );
    } catch (Exception e) {
        // Return validation errors to help model fix YAML
        return new WorkflowExecutionResult(
            "dynamic", "yaml-workflow", "1.0",
            "FAILED",
            Map.of(
                "error", e.getMessage(),
                "type", "VALIDATION_ERROR",
                "help", "Check YAML syntax against CNCF Serverless Workflow spec"
            )
        );
    }
}
```

**2. Serverless Workflow Spec as MCP Resource**

Provide the spec to help models generate valid workflows:

```java
@Resource(
    uri = "workflow://spec/schema",
    description = "CNCF Serverless Workflow JSON Schema for generating valid workflow definitions",
    mimeType = "application/json")
public TextResourceContents getWorkflowSchema() {
    // Return the official JSON Schema for Serverless Workflow
    // This helps LLMs understand the structure
    return TextResourceContents.create(
        "workflow://spec/schema",
        loadResourceAsString("/serverless-workflow-schema.json")
    );
}

@Resource(
    uri = "workflow://spec/examples",
    description = "Example YAML workflows demonstrating common patterns",
    mimeType = "application/x-yaml")
public TextResourceContents getWorkflowExamples() {
    // Curated examples: HTTP call, data transformation, branching, loops
    return TextResourceContents.create(
        "workflow://spec/examples",
        """
        # Example 1: Simple HTTP GET
        document:
          dsl: '1.0.3'
          namespace: examples
          name: simple-http
          version: '1.0'
          summary: Fetch user data from REST API
        do:
          - getUser:
              call: http
              with:
                method: GET
                endpoint:
                  uri: "${ 'https://api.example.com/users/' + .input.userId }"
        
        # Example 2: Data transformation with set
        document:
          dsl: '1.0.3'
          namespace: examples
          name: transform-data
          version: '1.0'
        do:
          - fetchData:
              call: http
              with:
                method: GET
                endpoint: https://api.example.com/data
          - transform:
              set:
                users: "${ .fetchData.body.users | map({ id: .id, name: .name }) }"
                count: "${ .users | length }"
        
        # Example 3: Conditional branching with switch
        document:
          dsl: '1.0.3'
          namespace: examples
          name: conditional-workflow
          version: '1.0'
        do:
          - checkStatus:
              call: http
              with:
                method: GET
                endpoint: https://api.example.com/status
          - decide:
              switch:
                - active:
                    when: "${ .checkStatus.body.status == 'active' }"
                    then: continue
                - inactive:
                    when: "${ .checkStatus.body.status == 'inactive' }"
                    then: end
          - processActive:
              call: http
              with:
                method: POST
                endpoint: https://api.example.com/process
        
        # Example 4: Loop over collection
        document:
          dsl: '1.0.3'
          namespace: examples
          name: process-items
          version: '1.0'
        do:
          - fetchItems:
              call: http
              with:
                method: GET
                endpoint: https://api.example.com/items
          - processEach:
              for:
                each: item
                in: "${ .fetchItems.body.items }"
              do:
                - processItem:
                    call: http
                    with:
                      method: POST
                      endpoint: https://api.example.com/process
                      body:
                        itemId: "${ .item.id }"
        """
    );
}
```

**3. Workflow Generation Prompt**

Guide models through workflow creation:

```java
@Prompt(
    name = "generate_workflow",
    description = "Guide for generating a CNCF Serverless Workflow YAML definition")
public PromptMessage generateWorkflow(
    @PromptArg(description = "Description of what the workflow should do") String requirement
) {
    return PromptMessage.withUserRole(String.format("""
        Generate a CNCF Serverless Workflow YAML definition for this requirement:
        
        "%s"
        
        Follow this structure:
        
        ```yaml
        document:
          dsl: '1.0.3'
          namespace: <your-namespace>
          name: <workflow-name>
          version: '1.0'
          summary: <one-line description>
        do:
          - taskName:
              call: http  # or: openapi, functionName, set, for, switch, try
              with:
                method: GET
                endpoint: https://api.example.com
          - transformData:
              set:
                result: "${ .taskName.body }"
        ```
        
        Available task types:
        - call: http - HTTP requests with method, endpoint, headers, body
        - call: openapi - OpenAPI operations with document, operationId, parameters
        - call: "functionName" - Call function from catalog (reference to use.functions)
        - set - Data transformation (object or runtime expression)
        - for - Iterate over collection (each, in, do)
        - switch - Conditional branching (cases with when/then)
        - try - Error handling (try/catch/retry)
        
        Runtime expression syntax:
        - Access input: "${ .inputField }"
        - Transform: "${ .data | map(.id) }"
        - Object construction: "${ { id: .user.id, name: .user.name } }"
        - Always wrap in quotes: "${ ... }"
        
        After generating, validate with the validate_yaml_workflow tool before executing.
        """, requirement));
}
```

**4. Workflow Validation Tool**

Help models validate their generated YAML before execution:

```java
@Tool(description = "Validate a workflow YAML definition without executing it")
public WorkflowValidationResult validateYamlWorkflow(
    @ToolArg(description = "Workflow YAML to validate") String yamlDefinition
) {
    try {
        Workflow workflow = WorkflowReader.readWorkflow(
            new ByteArrayInputStream(yamlDefinition.getBytes()),
            WorkflowFormat.YAML
        );
        
        // Validate against schema
        // Check required fields, task references, etc.
        
        return new WorkflowValidationResult(
            true,
            "Workflow is valid",
            extractWorkflowSummary(workflow)
        );
    } catch (Exception e) {
        return new WorkflowValidationResult(
            false,
            e.getMessage(),
            Map.of("suggestion", suggestFix(e))
        );
    }
}
```

**Use Case: AI-Generated Workflows**

This enables powerful scenarios:
1. **User**: "Create a workflow that fetches user data from API A, transforms it, and sends to API B"
2. **AI**: Reads `workflow://spec/schema` and `workflow://spec/examples`
3. **AI**: Generates YAML workflow definition
4. **AI**: Calls `validate_yaml_workflow` to check syntax
5. **AI**: Calls `execute_yaml_workflow` with generated YAML and sample input
6. **AI**: Returns result to user or refines workflow based on errors

**4. Execution Tracking** (requires persistence integration):

```java
// Future enhancement - requires Flow persistence module
@Resource(uri = "workflow://executions/{id}")
public TextResourceContents getExecution(@ResourceTemplateArg String id) {
    // Would integrate with Flow's persistence layer
    // to query execution history
}
```

**5. Auto-Discovery** (optional build-time feature):

```java
@BuildStep
void generateWorkflowTools(
        BuildProducer<ToolBuildItem> tools,
        List<WorkflowDefinitionBuildItem> workflows) {
    
    for (WorkflowDefinitionBuildItem workflow : workflows) {
        // Generate a dedicated @Tool method for this workflow
        // with typed parameters based on workflow input schema
        
        String toolName = "execute_" + workflow.getId().replace("-", "_");
        String description = String.format(
            "Execute %s workflow: %s",
            workflow.getName(),
            workflow.getDescription()
        );
        
        JsonSchema inputSchema = generateJsonSchema(workflow.getInputDefinition());
        
        // This would require generating synthetic beans at build time
        // Complexity: HIGH, Value: MEDIUM (nice-to-have)
        // Defer to post-MVP
    }
}
```

#### Phase 5: Function Catalog for AI-Powered Workflow Composition

**Goal**: Expose a catalog of available functions (CDI beans, LangChain4j tools, Serverless Workflow catalog) so AI models can discover and compose workflows using organization-specific services.

This transforms MCP from "execute workflows" to "AI-powered workflow composition using your infrastructure as building blocks."

**Implementation Approach**: Functions are exposed as HTTP endpoints (internal or external) and cataloged with metadata (name, description, parameters, category). AI models discover these endpoints via MCP resources and compose workflows using standard `call: http` tasks. This approach is fully spec-compliant and works with existing Quarkus REST patterns.

**1. Function Catalog Resources**

Expose available functions for discovery:

```java
@ApplicationScoped
public class FlowFunctionCatalogProvider {
    
    @Inject
    @Any
    Instance<Object> cdiBeans;  // All CDI beans
    
    @Inject
    @Any
    Instance<ToolSpecification> lc4jTools;  // LangChain4j tools
    
    /**
     * List all available functions across all sources.
     */
    @Resource(
        uri = "workflow://functions",
        description = "Catalog of all available functions that can be used in workflows",
        mimeType = "application/json")
    public TextResourceContents listFunctions() {
        List<FunctionInfo> functions = new ArrayList<>();
        
        // 1. Discover CDI beans with @WorkflowFunction annotation
        functions.addAll(discoverCDIFunctions());
        
        // 2. Discover LangChain4j @Tool interfaces
        functions.addAll(discoverLangChain4jTools());
        
        // 3. Load Serverless Workflow community catalog
        functions.addAll(loadServerlessWorkflowCatalog());
        
        return TextResourceContents.create(
            "workflow://functions",
            toJson(functions)
        );
    }
    
    /**
     * Get detailed information about a specific function.
     */
    @ResourceTemplate(
        uriTemplate = "workflow://functions/{functionName}",
        description = "Detailed function signature, parameters, examples",
        mimeType = "application/json")
    public TextResourceContents getFunctionDetails(
        @ResourceTemplateArg String functionName
    ) {
        FunctionInfo function = findFunction(functionName);
        
        return TextResourceContents.create(
            "workflow://functions/" + functionName,
            toJson(new FunctionDetails(
                function,
                generateExampleUsage(function),
                extractParameterDocs(function)
            ))
        );
    }
    
    /**
     * List functions by category (database, http, ai, messaging, etc.)
     */
    @ResourceTemplate(
        uriTemplate = "workflow://functions/categories/{category}",
        description = "Functions grouped by category",
        mimeType = "application/json")
    public TextResourceContents getFunctionsByCategory(
        @ResourceTemplateArg String category
    ) {
        List<FunctionInfo> functions = listFunctions().stream()
            .filter(f -> f.category().equals(category))
            .toList();
        
        return TextResourceContents.create(
            "workflow://functions/categories/" + category,
            toJson(functions)
        );
    }
}
```

**2. Function Discovery Annotations**

New annotation for marking CDI beans as workflow functions:

```java
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface WorkflowFunction {
    /**
     * Function name (unique identifier for workflow composition)
     */
    String name() default "";
    
    /**
     * Human-readable description
     */
    String description();
    
    /**
     * Category: database, http, ai, messaging, file, custom
     */
    String category() default "custom";
    
    /**
     * Example usage in YAML
     */
    String example() default "";
}
```

**Usage in user code:**

```java
@ApplicationScoped
public class CustomerService {
    
    @WorkflowFunction(
        name = "getUserProfile",
        description = "Fetch customer profile from internal CRM",
        category = "database",
        example = """
            call: function
            with:
              name: getUserProfile
              arguments:
                customerId: ${.customerId}
            """)
    public CustomerProfile getProfile(String customerId) {
        // Access internal CRM database
        return crmRepository.findById(customerId);
    }
    
    @WorkflowFunction(
        name = "updateInventory",
        description = "Update product inventory in SAP system",
        category = "custom")
    public void updateInventory(String productId, int quantity) {
        // Call internal SAP system
        sapClient.updateStock(productId, quantity);
    }
}
```

**3. Function Model**

```java
public record FunctionInfo(
    String name,
    String description,
    String category,
    String endpoint,  // HTTP endpoint to call this function
    String method,    // HTTP method (POST, GET, etc.)
    List<ParameterInfo> parameters,
    String returnType,
    String source  // "cdi", "langchain4j", "catalog"
) {}

public record ParameterInfo(
    String name,
    String type,
    String description,
    boolean required,
    Object defaultValue
) {}

public record FunctionDetails(
    FunctionInfo function,
    String exampleYAML,
    Map<String, String> parameterDocs
) {}
```

**4. Enhanced Workflow Generation Prompt**

Update the generation prompt to include function catalog:

```java
@Prompt(
    name = "generate_workflow_with_functions",
    description = "Generate workflow using available functions from catalog")
public PromptMessage generateWorkflowWithFunctions(
    @PromptArg(description = "What the workflow should accomplish") String requirement
) {
    // Query available functions
    List<FunctionInfo> functions = functionCatalog.listFunctions();
    
    return PromptMessage.withUserRole(String.format("""
        Generate a workflow for: "%s"
        
        **Available Functions:**
        %s
        
        Each function is exposed as an HTTP endpoint. Use them in your workflow:
        
        ```yaml
        do:
          - fetchUser:
              call: http
              with:
                method: POST
                endpoint: http://internal-api/functions/getUserProfile
                body:
                  customerId: "${ .input.customerId }"
          
          - processData:
              call: http
              with:
                method: POST
                endpoint: http://internal-api/functions/processOrder
                body:
                  userId: "${ .fetchUser.body.userId }"
                  items: "${ .input.items }"
        ```
        
        Or use external HTTP/OpenAPI calls:
        
        ```yaml
        - callExternalAPI:
            call: http
            with:
              method: POST
              endpoint: https://api.example.com/process
              body:
                userId: "${ .input.userId }"
        ```
        
        **Available Categories:**
        - database: getUserProfile, updateInventory, queryOrders
        - http: callInternalAPI, invokeWebhook
        - ai: analyzeSentiment, generateSummary
        - messaging: sendEmail, postToSlack, publishKafkaEvent
        - custom: (organization-specific functions)
        
        Compose these functions to achieve the requirement.
        """,
        requirement,
        formatFunctionList(functions)
    ));
}
```

**5. Use Case Example**

**User Request**: "When a new customer signs up, validate their email, create accounts in CRM and billing, send welcome email, and notify sales team"

**AI Workflow:**

1. Queries `workflow://functions` → discovers:
   - `validateEmail(email)`
   - `createCRMAccount(customerData)`
   - `createBillingAccount(customerId)`
   - `sendTemplateEmail(to, template)`
   - `postToSlack(channel, message)`

2. Generates workflow (using HTTP to call internal services):

```yaml
document:
  dsl: '1.0.3'
  namespace: onboarding
  name: customer-signup
  version: '1.0'
  summary: Automated customer onboarding workflow
do:
  - validateEmail:
      call: http
      with:
        method: POST
        endpoint: http://internal-api/validation/email
        body:
          email: "${ .input.email }"
  
  - createCRM:
      call: http
      with:
        method: POST
        endpoint: http://internal-api/crm/accounts
        body: "${ .input.customerData }"
  
  - createBilling:
      call: http
      with:
        method: POST
        endpoint: http://internal-api/billing/accounts
        body:
          customerId: "${ .createCRM.body.id }"
  
  - welcomeEmail:
      call: http
      with:
        method: POST
        endpoint: http://internal-api/email/send
        body:
          to: "${ .input.email }"
          template: welcome
  
  - notifySales:
      call: http
      with:
        method: POST
        endpoint: http://internal-api/slack/post
        body:
          channel: sales-team
          message: "${ 'New customer: ' + .input.customerData.name }"
```

**Alternative**: If using function catalog with registered functions, the workflow would reference pre-defined functions (exact syntax depends on Quarkus Flow's function registration mechanism).


3. Executes via `execute_yaml_workflow`

## Key Benefits

### What AI Cannot Do Without This:

❌ **Access internal services** (CRM, billing, SAP, internal APIs)  
❌ **Compose organization-specific functions** (doesn't know your catalog)  
❌ **Generate events & side effects** (Kafka, webhooks, notifications)  
❌ **Stateful operations** (database transactions, workflow state)  
❌ **Enterprise integration** (Active Directory, Salesforce, Oracle)  
❌ **Security context** (run with proper user identity, RBAC)  
❌ **Long-running operations** (multi-day workflows with durable state)  

### What AI Can Do With Function Catalog:

✅ **Discover** your internal service capabilities  
✅ **Compose** them into workflows solving business problems  
✅ **Execute** entirely within your secure infrastructure  
✅ **Orchestrate** complex multi-step processes across systems  
✅ **Adapt** workflows based on your evolving service catalog  

This transforms Quarkus Flow into an **AI-native orchestration platform** where models compose workflows using your infrastructure as Lego blocks.

### Configuration

```properties
# Enable/disable MCP server
quarkus.flow.mcp.enabled=true

# Transport selection (http or stdio)
quarkus.flow.mcp.transport=http

# HTTP transport configuration
quarkus.flow.mcp.http.path=/mcp
quarkus.flow.mcp.http.port=8080

# STDIO transport (for local development with Claude Desktop)
quarkus.flow.mcp.stdio.enabled=false

# Security
quarkus.flow.mcp.security.enabled=true
quarkus.flow.mcp.security.oidc=true

# Dev mode
quarkus.flow.mcp.dev-ui.enabled=true
```

### Configuration Class

```java
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public class FlowMcpConfig {
    
    /**
     * Enable/disable MCP server
     */
    @ConfigItem(defaultValue = "true")
    public boolean enabled;
    
    /**
     * Transport type: http or stdio
     */
    @ConfigItem(defaultValue = "http")
    public TransportType transport;
    
    /**
     * HTTP transport configuration
     */
    @ConfigItem
    public HttpConfig http;
    
    @ConfigGroup
    public static class HttpConfig {
        /**
         * HTTP endpoint path
         */
        @ConfigItem(defaultValue = "/mcp")
        public String path;
        
        /**
         * HTTP port (defaults to quarkus.http.port)
         */
        @ConfigItem
        public Optional<Integer> port;
    }
    
    public enum TransportType {
        HTTP, STDIO
    }
}
```

### Testing Strategy

#### Unit Tests
- Mock `Instance<Flowable>` and `WorkflowApplication`
- Test tool invocations
- Test resource reads
- Test prompt generation

#### Integration Tests (McpAssured)

```java
@QuarkusTest
@TestProfile(FlowMcpTestProfile.class)
class FlowMcpServerIT {
    
    @Test
    void testExecuteWorkflowTool() {
        McpAssured.given()
            .tool("execute_workflow")
            .argument("workflowId", "test-workflow")
            .argument("input", Map.of("testKey", "testValue"))
            .when()
            .call()
            .then()
            .statusCode(200)
            .body("workflowId", equalTo("test-workflow"))
            .body("status", equalTo("COMPLETED"));
    }
    
    @Test
    void testListWorkflowsTool() {
        McpAssured.given()
            .tool("list_workflows")
            .when()
            .call()
            .then()
            .statusCode(200)
            .body("size()", greaterThan(0))
            .body("[0].name", notNullValue());
    }
    
    @Test
    void testWorkflowDefinitionsResource() {
        McpAssured.given()
            .resource("workflow://definitions")
            .when()
            .read()
            .then()
            .statusCode(200)
            .body("size()", greaterThan(0))
            .body("[0].name", notNullValue());
    }
    
    @Test
    void testWorkflowDefinitionResource() {
        McpAssured.given()
            .resource("workflow://definitions/test-workflow")
            .when()
            .read()
            .then()
            .statusCode(200)
            .body("name", equalTo("test-workflow"))
            .body("inputSchema", notNullValue());
    }
    
    @Test
    void testConstructWorkflowInputPrompt() {
        McpAssured.given()
            .prompt("construct_workflow_input")
            .argument("workflowId", "test-workflow")
            .when()
            .get()
            .then()
            .statusCode(200)
            .body("messages[0].role", equalTo("user"))
            .body("messages[0].content.text", containsString("Help me construct"));
    }
}
```

#### Manual Testing
- Claude Desktop integration (STDIO transport)
- Gemini CLI integration (HTTP transport)
- Custom MCP client

### Dev UI Integration

Add a Dev UI card showing:
- Available MCP tools (from workflows)
- Available MCP resources
- Available MCP prompts
- Interactive tool testing
- Resource browser
- Traffic log (JSON-RPC messages)

```java
@BuildStep(onlyIf = IsDevelopment.class)
void createDevUiCard(BuildProducer<CardPageBuildItem> cardPageProducer) {
    CardPageBuildItem card = new CardPageBuildItem();
    card.addPage(Page.webComponentPageBuilder()
        .title("MCP Server")
        .componentLink("qwc-flow-mcp.js")
        .icon("font-awesome-solid:diagram-project"));
    
    cardPageProducer.produce(card);
}
```

### Documentation Updates

1. **New guide**: `docs/modules/ROOT/pages/mcp-integration.adoc`
   - What is MCP and why use it
   - Installation and setup
   - Configuration options
   - Using with Claude Desktop
   - Using with custom MCP clients
   - Security considerations

2. **Update existing docs**:
   - Add MCP section to main README
   - Update architecture diagrams
   - Add to "Integrations" section

3. **Examples**:
   - `examples/mcp-basic/` - Simple MCP server
   - `examples/mcp-claude-desktop/` - Claude Desktop integration
   - `examples/mcp-langchain4j/` - LangChain4j MCP client

### Error Handling

Use standard JSON-RPC error codes:

```java
public class FlowMcpException extends RuntimeException {
    private final int code;
    private final Object data;
    
    public static FlowMcpException workflowNotFound(String id) {
        return new FlowMcpException(
            -32602,
            "Workflow not found: " + id,
            Map.of("workflowId", id)
        );
    }
    
    public static FlowMcpException invalidInput(String message, Map<String, Object> details) {
        return new FlowMcpException(-32602, message, details);
    }
    
    public static FlowMcpException executionFailed(String message, Exception cause) {
        return new FlowMcpException(
            -32603,
            "Workflow execution failed: " + message,
            Map.of("error", cause.getMessage())
        );
    }
}
```

### Security Considerations

1. **Authentication**: Leverage Quarkus Security and quarkus-mcp-server OIDC integration
2. **Authorization**: Use `@RolesAllowed` on tool methods
3. **Input Validation**: Validate workflow input against schema
4. **Rate Limiting**: Consider adding rate limits on tool execution
5. **Audit Logging**: Log all MCP tool invocations

```java
@Tool(description = "Execute a workflow")
@RolesAllowed("workflow-executor")
public WorkflowExecutionResult executeWorkflow(
    @ToolArg String workflowId,
    @ToolArg Map<String, Object> input
) {
    // Validate input against workflow schema
    validateInput(workflowId, input);
    
    // Audit log
    auditLog.info("MCP tool execution: workflow={}, user={}", 
        workflowId, securityContext.getPrincipal().getName());
    
    // Execute
    return doExecute(workflowId, input);
}
```

## Consequences

### Positive

1. **Standard Integration**: Quarkus Flow becomes immediately compatible with any MCP client (Claude, custom AI apps, future tools)
2. **AI-Native**: Workflows can be orchestrated by AI agents, enabling complex multi-step AI-driven processes
3. **Reuse Existing Extension**: Leverage mature `quarkus-mcp-server` extension instead of building from scratch
4. **Developer Experience**: Same annotation-driven approach as rest of Quarkus ecosystem
5. **Enterprise Ready**: Built-in security, native compilation, reactive support from day one
6. **Observability**: AI systems gain insight into workflow metadata and capabilities
7. **Composability**: Workflows become building blocks in larger AI systems
8. **Community**: Tap into growing MCP ecosystem and Quarkiverse community
9. **Aligned with Flow Architecture**: Uses `WorkflowApplication` and proper namespace/name/version identification
10. **Code Reuse**: Shared API module eliminates duplication between runner and MCP
11. **Consistency**: Same workflow lookup and execution logic across all interfaces (REST, MCP, future integrations)
12. **Maintainability**: Single source of truth for core workflow operations reduces maintenance burden

### Negative

1. **Additional Dependency**: Adds `quarkus-mcp-server` as a dependency (though it's well-maintained Quarkiverse project)
2. **Learning Curve**: Developers need to understand MCP concepts (though similar to REST APIs)
3. **Protocol Evolution**: MCP spec is evolving (2024-11-05 → 2025-11-25), may require updates
4. **Security Surface**: Exposes workflow execution to external systems, requires careful security design
5. **Blocking Execution**: MVP uses blocking workflow execution (`.join()`), may not scale for long-running workflows
6. **No Execution Tracking**: MVP doesn't track running/completed workflow instances (future enhancement)

### Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| MCP spec breaking changes | High | Pin to stable spec version, test against conformance suite, monitor spec changes |
| Security vulnerabilities | High | Mandatory authentication, input validation, audit logging, security review before release |
| Performance overhead | Medium | Document blocking nature, consider async execution in future, monitor with Micrometer |
| Long-running workflows | Medium | Document limitation, suggest using Flow's event/messaging for async patterns, add timeout config |
| quarkus-mcp-server maintenance | Medium | It's a Quarkiverse project with active maintenance; we can contribute back if needed |
| Schema extraction complexity | Low | Handle missing/malformed schemas gracefully, provide clear error messages |

## Alternatives Considered

### Alternative 1: Custom MCP Implementation

**Approach**: Implement MCP protocol from scratch without using `quarkus-mcp-server`

**Pros**:
- Full control over implementation
- No external dependencies
- Could optimize for Quarkus Flow-specific use cases

**Cons**:
- Significant development effort (~4-6 weeks)
- Need to maintain protocol compliance ourselves
- Lose quarkus-mcp-server features (testing, dev UI, transport abstractions)
- Duplicate work already done by community

**Verdict**: ❌ Rejected - Reinventing the wheel when a mature extension exists

### Alternative 2: REST API with Custom AI Integration

**Approach**: Build a custom REST API for AI systems instead of using MCP

**Pros**:
- Familiar REST patterns
- Full control over API design
- No MCP protocol constraints

**Cons**:
- Non-standard - every AI client would need custom integration
- Lose MCP ecosystem benefits (Claude Desktop support, MCP clients)
- More work for users to integrate
- No standardized discovery mechanism

**Verdict**: ❌ Rejected - MCP provides standardization and ecosystem benefits

### Alternative 3: LangChain4j-Only Integration

**Approach**: Build integration only for LangChain4j tools/agents, skip MCP

**Pros**:
- Simpler implementation
- Direct integration with Quarkus Flow's existing LangChain4j module
- No protocol overhead

**Cons**:
- Only works with LangChain4j, not other AI systems
- Doesn't benefit from MCP ecosystem (Claude Desktop, etc.)
- Less composable with other tools

**Verdict**: ❌ Rejected - Too narrow, MCP provides broader compatibility

### Alternative 4: Hybrid Approach (Chosen)

**Approach**: Use `quarkus-mcp-server` extension + leverage Flow's CDI architecture

**Pros**:
- Best of both worlds
- Reuse mature MCP implementation
- Aligns with Flow's existing patterns (`Instance<Flowable>`, `WorkflowApplication`)
- Minimal development effort
- Leverages community work

**Cons**:
- External dependency on quarkus-mcp-server

**Verdict**: ✅ **Selected** - Optimal balance of effort, functionality, and standardization

## Implementation Plan

### Phase 0: Shared API Module (1 week)
- [ ] Create `quarkus-flow-api` module
- [ ] Implement `WorkflowDefinitionService` (shared lookup logic with WorkflowVersionComparator)
- [ ] Implement `WorkflowExecutionService` (shared execution logic, sync/async modes)
- [ ] Create `WorkflowDefinitionInfo` model (shared between runner and MCP)
- [ ] Create `WorkflowNotFoundException` exception
- [ ] Update runner to use shared services (refactor existing DefinitionResource and RunnerExecResource)
- [ ] Add unit tests for shared services
- [ ] Update runner integration tests to verify no regression

### Phase 1: MVP (2-3 weeks)
- [ ] Create `quarkus-flow-mcp` module structure
- [ ] Add `quarkus-mcp-server` and `quarkus-flow-api` dependencies
- [ ] Implement tools: `execute_workflow(namespace, name, version, input)`, `list_workflows(namespace)`
- [ ] Implement `FlowMcpToolProvider` using shared `WorkflowExecutionService`
- [ ] Add integration tests with McpAssured
- [ ] Basic documentation

### Phase 2: Resources (1-2 weeks)
- [ ] Implement `workflow://definitions` (list all)
- [ ] Implement `workflow://definitions/{namespace}/{name}` (latest version)
- [ ] Implement `workflow://definitions/{namespace}/{name}/{version}` (specific version)
- [ ] Use shared `WorkflowDefinitionService` for all lookups
- [ ] Update tests and docs

### Phase 3: Prompts (1 week)
- [ ] Implement `construct_workflow_input` prompt
- [ ] Implement `analyze_workflow_schema` prompt
- [ ] Add prompt tests

### Phase 4: Polish & Release (1-2 weeks)
- [ ] Dev UI integration
- [ ] Complete documentation (guide + examples)
- [ ] Security review
- [ ] Performance testing
- [ ] Example applications
- [ ] Release 0.11.0 with MCP support (MVP: tools, resources, prompts)

### Post-MVP Enhancements

**Phase 5: Dynamic YAML Workflow Generation (High Priority)**
- [ ] `execute_yaml_workflow(yamlDefinition, input)` tool - Execute AI-generated workflows
- [ ] `validate_yaml_workflow(yamlDefinition)` tool - Validate YAML syntax
- [ ] `workflow://spec/schema` resource - CNCF Serverless Workflow JSON Schema
- [ ] `workflow://spec/examples` resource - Curated YAML workflow examples
- [ ] `generate_workflow` prompt - Guide AI through creating valid workflow definitions

**Phase 6: Function Catalog for Workflow Composition (High Priority)**
- [ ] `workflow://functions` resource - List all available functions
- [ ] `workflow://functions/{name}` resource - Function details with examples
- [ ] `workflow://functions/categories/{category}` resource - Functions by category
- [ ] `@WorkflowFunction` annotation - Mark CDI beans as workflow functions
- [ ] Auto-discover LangChain4j `@Tool` interfaces
- [ ] Integration with Serverless Workflow community catalog
- [ ] `generate_workflow_with_functions` prompt - Enhanced generation with function awareness

**Medium Priority:**
  - `workflow://functions` resource (list all functions)
  - `workflow://functions/{name}` resource (function details)
  - `@WorkflowFunction` annotation for CDI beans
  - Auto-discover LangChain4j `@Tool` interfaces
  - Integration with Serverless Workflow community catalog
  - Enhanced generation prompt with function awareness
- [ ] Execution state tracking (requires persistence integration)

**Medium Priority:**
- [ ] Async/reactive execution (non-blocking tools)
- [ ] Advanced resource subscriptions with WebSocket
- [ ] Integration with Quarkus Flow observability (metrics, tracing)
- [ ] Claude Desktop example project
- [ ] Function catalog categories and search

**Low Priority:**
- [ ] Auto-generated typed tools per workflow (complexity: high, value: medium)

## References

### MCP Specification
- Official Specification: https://modelcontextprotocol.io/specification/2025-11-25
- GitHub: https://github.com/modelcontextprotocol/modelcontextprotocol
- Anthropic Announcement: https://www.anthropic.com/news/model-context-protocol

### Quarkus MCP Server
- Documentation: https://docs.quarkiverse.io/quarkus-mcp-server/dev/
- GitHub: https://github.com/quarkiverse/quarkus-mcp-server
- Example Servers: https://github.com/quarkiverse/quarkus-mcp-servers

### Related Guides
- MCP JSON-RPC Guide: https://portkey.ai/blog/mcp-message-types-complete-json-rpc-reference-guide/
- arXiv MCP Tutorial: https://glaforge.dev/posts/2026/01/18/implementing-an-arxiv-mcp-server-with-quarkus-in-java/
- MCP Architecture Deep Dive: https://latenode.com/blog/model-context-protocol-json-rpc

### Internal References
- Flow Runner API Issue: https://github.com/quarkiverse/quarkus-flow/issues/52
- CNCF Serverless Workflow Spec: https://serverlessworkflow.io/
- Quarkus Extension Guide: https://quarkus.io/guides/writing-extensions
- Quarkus Flow Documentation: https://docs.quarkiverse.io/quarkus-flow/dev/

---

**Authors**: Claude Code Research (based on comprehensive MCP research and Quarkus Flow codebase analysis)

**Reviewers**: [To be assigned]

**Decision Date**: [To be determined after review]
