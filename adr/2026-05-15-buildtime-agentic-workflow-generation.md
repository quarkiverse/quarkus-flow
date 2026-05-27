# Build-Time Generation of LangChain4j Agentic Workflows

**Date:** 2026-05-15  
**Status:** Implemented  
**Context:** Issue #545 - Move LangChain4j agentic workflow creation from runtime to build-time

## Context

Previously, LangChain4j agentic workflows in Quarkus Flow were built at runtime:

1. `FlowAgentService` implementations (Sequential, Parallel, Loop, Conditional) created a `FlowAgentWorkflowBuilder`
2. The builder received a `taskFactory` function that depended on the `List<AgentInstance>` provided by LangChain4j at runtime
3. When `Planner.init()` was called with actual agent instances, `FlowAgentWorkflowBuilder.buildOrGet(agents)` constructed the workflow
4. The workflow was registered in `WorkflowRegistry` on-the-fly

**Problems with the previous approach:**
- Workflows invisible to DevUI until first execution
- Not aligned with `core` module architecture (which uses `DiscoveredWorkflowBuildItem` and generates Flow classes)
- Runtime workflow construction added overhead to first execution
- WorkflowRegistry complexity managing both static and dynamic workflows
- Not optimal for native image / AOT compilation
- Workflow structure couldn't be validated at build-time

**Build-time information available:**
From `FlowAgenticWorkflowBuildItem` (created by scanning LangChain4j annotations):
- Agent interface name (e.g., `com.example.CustomerAgent`)
- Agentic method signature with parameter names and types
- Workflow topology (`AgenticSystemTopology`: SEQUENCE, PARALLEL, LOOP, ROUTER/Conditional)
- Sub-agent class list (the agents to execute)
- **Topology-specific metadata:**
  - **Conditional**: Activation predicate methods (with parameter types) mapped to sub-agents
  - **Loop**: Max iterations, exit condition method (with parameter types), testExitAtLoopEnd flag

**Runtime-only information:**
- Actual `AgentInstance` objects created by LangChain4j
- Agent ordering in execution context

## Decision

**Generate AgenticFlow subclasses at build-time using Gizmo**, following the same pattern as `core` module for user-defined Flow classes.

### Architecture

```
Build-Time (Deployment Module):
  FlowAgenticWorkflowBuildItem (from LC4J annotations)
    ↓
  Generate AgenticFlow subclass with Gizmo
    ↓
  DiscoveredWorkflowBuildItem.fromSource(className)
    ↓
  Core module creates CDI beans + WorkflowDefinitions

Runtime-Init:
  Core calls descriptor() → Workflows registered

Runtime Execution:
  Planner.init() → Store ordered AgentInstance list
  Tasks execute → Lookup agents by index from Planner
```

### Key Components

#### 1. AgenticFlow Base Class

```java
package io.quarkiverse.flow.langchain4j.workflow;

/**
 * Base class for LangChain4j agentic workflows generated at build-time.
 */
public abstract class AgenticFlow extends Flow {
    
    /** Agent interface FQCN this workflow implements */
    public abstract String agentClassName();
    
    /** Base task names (method names) for sub-agents */
    protected abstract List<String> subAgentTaskNames();
    
    /** JSON Schema for workflow input (generated at build-time) */
    protected abstract String getInputSchemaJson();
    
    /** Execute sub-agent by index */
    protected Object executeAgent(DefaultAgenticScope scope, int subAgentIndex) {
        FlowPlanner planner = scope.executionContextAs(FlowPlanner.class);
        return planner.executeAgent(subAgentIndex).join();
    }
    
    // Shared helpers
    protected Consumer<DocumentBuilder> buildDocument() { ... }
    protected Consumer<InputBuilder> inputSchema() { ... }
    static Object agenticScopePassthrough(WorkflowModel rawInput) { ... }
}
```

**Topology-specific implementations:**
- `SequentialAgenticFlow` - Sequential execution
- `ParallelAgenticFlow` - Parallel execution with fork/join
- `LoopAgenticFlow` - Loop with exit conditions
- `ConditionalAgenticFlow` - Conditional routing with activation predicates

#### 2. Metadata Classes (Build-Time Only)

**PredicateMetadata** - Stores predicate method information:
```java
public record PredicateMetadata(
    String methodName,
    List<String> parameterTypeNames,
    String description
) { }
```

**ConditionalMetadata** - For `@ConditionalAgent`:
```java
public record ConditionalMetadata(
    Map<String, PredicateMetadata> activationConditions  // subAgent class name -> predicate
) { }
```

**LoopMetadata** - For `@LoopAgent`:
```java
public record LoopMetadata(
    int maxIterations,
    Optional<PredicateMetadata> exitCondition,
    boolean testExitAtLoopEnd
) { }
```

#### 3. Index-Based Agent Execution

**Critical discovery:** LangChain4j recursively appends parent indices to agent IDs via `setParent(parent, index)`. Build-time generated IDs (e.g., `medical$0`) don't match runtime IDs (e.g., `medical$0$1`).

**Solution:** Use index-based execution instead of agent ID matching:

**FlowPlanner** (enhanced):
```java
public class FlowPlanner implements Planner {
    private List<AgentInstance> subAgentsList;  // Ordered list from InitPlanningContext
    
    @Override
    public void init(InitPlanningContext ctx) {
        this.subAgentsList = new ArrayList<>(ctx.subagents());
        // ... rest of init
    }
    
    public CompletableFuture<Void> executeAgent(int agentIndex) {
        return executeAgent(getAgentByIndex(agentIndex));
    }
    
    public AgentInstance getAgentByIndex(int index) {
        if (index < 0 || index >= subAgentsList.size()) {
            throw new IllegalStateException(
                "Invalid subagent index: " + index +
                ". Available subagents: " + subAgentsList.size() +
                " (IDs: " + subAgentsList.stream().map(AgentInstance::agentId).toList() + ")");
        }
        return subAgentsList.get(index);
    }
}
```

**Task Naming:** Uses `methodName-index` format for workflow visualization:
- `execute-0`, `execute-1` (for sequential agents with same method name)
- `scoreStyle-0`, `editStory-1` (for agents with different method names)

#### 4. Generated Flow Classes

**Naming:** `Generated{InterfaceSimpleName}AgenticFlow` in same package as agent interface

**Example 1: Sequential Agent**
```java
package com.example;

@Identifier("com.example.CustomerAgent")
@ApplicationScoped
@Unremovable
public class GeneratedCustomerAgentAgenticFlow extends SequentialAgenticFlow {
    
    @Override
    public String agentClassName() {
        return "com.example.CustomerAgent";
    }
    
    @Override
    protected List<String> subAgentTaskNames() {
        return List.of("processProfile", "checkHistory");
    }
    
    @Override
    protected String getInputSchemaJson() {
        return "{\"type\":\"object\",\"properties\":{\"customerId\":{\"type\":\"string\"}},\"required\":[\"customerId\"],\"$schema\":\"http://json-schema.org/draft-07/schema#\"}";
    }
}
```

**Example 2: Conditional Agent**
```java
package com.example;

@Identifier("com.example.ExpertsAgent")
@ApplicationScoped
@Unremovable
public class GeneratedExpertsAgentAgenticFlow extends ConditionalAgenticFlow {
    
    @Override
    public String agentClassName() {
        return "com.example.ExpertsAgent";
    }
    
    @Override
    protected List<String> subAgentTaskNames() {
        return List.of("medicalExpert", "technicalExpert");
    }
    
    @Override
    protected Map<Integer, Predicate<AgenticScope>> activationPredicates() {
        return Map.of(
            0, buildActivationPredicate(
                ExpertsAgent.class,
                "activateMedical",
                List.of("dev.langchain4j.agentic.scope.AgenticScope")
            ),
            1, buildActivationPredicate(
                ExpertsAgent.class,
                "activateTechnical",
                List.of("dev.langchain4j.agentic.scope.AgenticScope")
            )
        );
    }
    
    @Override
    protected String getInputSchemaJson() {
        return "{...}";
    }
}
```

**Key change:** Conditional predicates use **Integer indices** as map keys instead of String agent class names, aligning with index-based execution.

**Example 3: Loop Agent**
```java
package com.example;

@Identifier("com.example.StyleReviewAgent")
@ApplicationScoped
@Unremovable
public class GeneratedStyleReviewAgentAgenticFlow extends LoopAgenticFlow {
    
    @Override
    public String agentClassName() {
        return "com.example.StyleReviewAgent";
    }
    
    @Override
    protected List<String> subAgentTaskNames() {
        return List.of("scoreStyle", "editStory");
    }
    
    @Override
    protected int maxIterations() {
        return 5;
    }
    
    @Override
    protected Optional<BiPredicate<AgenticScope, Integer>> exitCondition() {
        return Optional.of(buildLoopExitPredicate(
            StyleReviewAgent.class,
            "shouldExit",
            List.of("dev.langchain4j.agentic.scope.AgenticScope", "java.lang.Integer")
        ));
    }
    
    @Override
    protected boolean testExitAtLoopEnd() {
        return true;
    }
    
    @Override
    protected String getInputSchemaJson() {
        return "{...}";
    }
}
```

#### 5. Input Schema Generation (Build-Time)

JSON Schema Draft-7 is generated from method signatures using Jandex:

```java
// In GizmoAgentFlowsHelper
static String generateInputSchema(IndexView index, MethodInfo method) {
    ObjectNode schema = objectMapper.createObjectNode();
    schema.put("type", "object");
    schema.put("$schema", "http://json-schema.org/draft-07/schema#");
    
    ObjectNode properties = objectMapper.createObjectNode();
    ArrayNode required = objectMapper.createArrayNode();
    
    for (int i = 0; i < method.parametersCount(); i++) {
        Type paramType = method.parameterType(i);
        String paramName = method.parameterName(i);
        
        ObjectNode propSchema = properties.putObject(paramName);
        propSchema.put("type", mapJavaTypeToJsonSchemaType(paramType));
        required.add(paramName);
    }
    
    schema.set("properties", properties);
    schema.set("required", required);
    return schema.toString();
}
```

**Consumed at RUNTIME_INIT:**
```java
public Consumer<InputBuilder> inputSchema() {
    String schemaJson = getInputSchemaJson();
    if (schemaJson == null || schemaJson.isEmpty()) {
        return null;
    }
    JsonNode schemaNode = objectMapper.readTree(schemaJson);
    return builder -> {
        builder.schema(schemaNode);
        builder.build().setFrom(null);  // Clear default InputFrom to avoid SDK errors
    };
}
```

### Build-Time Processing

**Build step in `FlowLangChain4jProcessor`:**

```java
@BuildStep
void generateAgenticFlowClasses(
        CombinedIndexBuildItem combinedIndex,
        List<FlowAgenticWorkflowBuildItem> agenticWorkflows,
        BuildProducer<GeneratedBeanBuildItem> generatedClasses,
        BuildProducer<DiscoveredWorkflowBuildItem> discoveredWorkflows) {
    
    for (FlowAgenticWorkflowBuildItem workflow : agenticWorkflows) {
        String generatedClassName = generateClassName(workflow.ifaceName());
        Class<? extends AgenticFlow> flowInterface = getFlowClass(workflow.topology());
        
        ClassOutput classOutput = new GeneratedBeanGizmoAdaptor(generatedClasses);
        try (ClassCreator classCreator = ClassCreator.builder()
                .classOutput(classOutput)
                .className(generatedClassName)
                .superClass(flowInterface.getName())
                .build()) {
            
            classCreator.addAnnotation(Identifier.class).add("value", generatedClassName);
            classCreator.addAnnotation(Unremovable.class);
            classCreator.addAnnotation(ApplicationScoped.class);
            
            // Generate accessor methods
            generateAgentClassNameMethod(classCreator, workflow.ifaceName());
            generateSubAgentTaskNamesMethod(classCreator, computeTaskNames(index, workflow.subAgents()));
            generateAgentDescriptionMethod(classCreator, workflow.description(), workflow.ifaceName());
            generateInputSchemaMethod(classCreator, combinedIndex.getIndex(), workflow.method());
            
            // Generate topology-specific metadata
            if (workflow.conditionalMetadata().isPresent()) {
                generateConditionalMetadataField(classCreator, workflow.ifaceName(),
                    workflow.conditionalMetadata().get(), workflow.subAgents());
            }
            if (workflow.loopMetadata().isPresent()) {
                generateLoopMetadataFields(classCreator, workflow.ifaceName(), 
                    workflow.loopMetadata().get());
            }
        }
        
        // Notify core module
        discoveredWorkflows.produce(DiscoveredWorkflowBuildItem.fromSource(generatedClassName));
    }
}
```

**Key generation helpers:**

1. **computeTaskNames()** - Extracts method names from sub-agent interfaces using Jandex (count derived from list size)
2. **generateConditionalMetadataField()** - Maps Integer indices to predicates
3. **generateLoopMetadataFields()** - Generates maxIterations, exitCondition, testExitAtLoopEnd accessors
4. **generateInputSchemaMethod()** - Generates JSON schema string from method signature

### Timeline

1. **Build-Time**:
   - Gizmo generates AgenticFlow subclasses
   - `DiscoveredWorkflowBuildItem.fromSource()` produced
   - Core creates CDI beans

2. **Runtime-Init** (recorder phase):
   - Core calls `descriptor()` on each generated Flow
   - Input schema parsed and applied
   - Workflows registered in WorkflowApplication
   - Visible in DevUI with full task details

3. **Runtime Execution**:
   - LangChain4j calls `FlowAgentsBuilderService` via ServiceLoader
   - Service creates `FlowPlanner` with Flow from CDI
   - `Planner.init()` stores ordered `AgentInstance` list
   - Tasks execute, looking up agents by index from Planner

## Consequences

### Positive

- ✅ **Alignment with `core` architecture**: Uses same `DiscoveredWorkflowBuildItem` → `FlowProcessor` → CDI bean pattern
- ✅ **DevUI visibility**: Workflows appear in DevUI with full details before first execution
- ✅ **Build-time validation**: Workflow structure and input schema validated at build-time
- ✅ **Native image friendly**: All workflow structure decisions made at build-time
- ✅ **Performance**: No runtime workflow construction overhead
- ✅ **Index-based execution**: Robust against LangChain4j agent ID changes
- ✅ **Meaningful task names**: `methodName-index` format for workflow visualization
- ✅ **Input schema generation**: JSON Schema Draft-7 generated from method signatures at build-time

### Negative

- ❌ **Initial complexity**: Requires Gizmo bytecode generation
- ❌ **Reflection at RUNTIME_INIT**: Predicate methods looked up via reflection when `descriptor()` is called
- ❌ **Index-based coupling**: Depends on stable agent ordering from LangChain4j (but more robust than ID matching)

### Neutral

- ⚠️ **More generated classes**: One per agentic interface (but organized in user packages)

## Implementation Details

### Index-Based Agent Execution

**Problem:** LangChain4j recursively builds agent IDs by appending parent indices. A sub-agent in a nested workflow gets IDs like `parent$0$1` where:
- `parent` = parent agent method name
- `$0` = first sub-agent index
- `$1` = nested sub-agent index

Build-time can't predict these runtime IDs.

**Solution:** Store agents in ordered list at runtime and execute by index:

1. **Build-time**: Generate `subAgentTaskNames()` returning list of task names (count derived from `size()`)
2. **Runtime (init)**: `FlowPlanner.init()` receives ordered list from `InitPlanningContext.subagents()`
3. **Runtime (execution)**: Tasks call `executeAgent(scope, index)` which looks up by position

**Benefits:**
- Order-independent of agent naming
- Self-documenting error messages with available indices
- Survives LangChain4j agent ID changes

### Task Naming Strategy

Tasks are named `{methodName}-{index}` where:
- `methodName` = agent interface method name (e.g., `execute`, `scoreStyle`)
- `index` = position in sub-agents array (0, 1, 2, ...)

**Example workflow visualization:**
```
SequenceAgent:
  - execute-0 (GenerateStoryAgent)
  - execute-1 (EditStoryAgent)
  
LoopAgent:
  - loop:
    - scoreStyle-0 (StyleScorer)
    - editStory-1 (StoryEditor)
```

This provides meaningful task names for DevUI and mermaid diagrams while using indices for execution.

### Conditional Predicate Generation

**Metadata captured at build-time:**
```java
ConditionalMetadata(
    Map.of(
        "com.example.MedicalExpert" -> PredicateMetadata("activateMedical", [...], "description"),
        "com.example.TechnicalExpert" -> PredicateMetadata("activateTechnical", [...], "description")
    )
)
```

**Generated code uses Integer indices:**
```java
protected Map<Integer, Predicate<AgenticScope>> activationPredicates() {
    // Map agent class names to indices
    Map<String, Integer> classNameToIndex = Map.of(
        "com.example.MedicalExpert", 0,
        "com.example.TechnicalExpert", 1
    );
    
    // Build predicates with Integer keys
    return Map.of(
        0, buildActivationPredicate(...),
        1, buildActivationPredicate(...)
    );
}
```

This aligns with index-based execution while preserving class name metadata for predicate building.

### Build-Time Config Fix

**Issue:** `FlowProcessor` was field-injecting `FlowDefinitionsConfig`:
```java
class FlowProcessor {
    FlowDefinitionsConfig flowDefinitionsConfig;  // ❌ Not allowed
}
```

**Fix:** Pass config as BuildStep parameters:
```java
@BuildStep
void produceWorkflowDefinitions(
        ...,
        FlowDefinitionsConfig flowDefinitionsConfig) {  // ✅ Correct
    // Use flowDefinitionsConfig
}
```

Quarkus deployment processors cannot field-inject build-time config. It must be passed as method parameters to `@BuildStep` methods.

## Code Removal

**Deleted:**
- `FlowPlannerBuilder` class
- `FlowAgentWorkflowBuilder` class
- `FlowLangChain4jWorkflowRecorder.registerAgenticWorkflows()` method
- `GeneratedAgenticFlow` interface (replaced with AgenticFlow base class)
- `GeneratedAgenticFlowRegistry` (core handles workflow management)
- Runtime workflow building logic from service classes
- Agent ID-based lookup (`getAgent(String agentClassName)`)

**Simplified:**
- Service classes now use index-based execution
- FlowPlanner simplified to ordered list management
- WorkflowRegistry no longer handles dynamic agentic workflows

## Related Issues

- Issue #545: Move workflow creation to build-time
- Issue #538: Allow to inject versioned workflow loaded by Yaml DSL

## References

- CNCF Serverless Workflow Specification: https://serverlessworkflow.io/
- LangChain4j Agentic API: https://docs.langchain4j.dev/tutorials/agents
- Quarkus Extension Development: https://quarkus.io/guides/writing-extensions
- Quarkus Gizmo: https://github.com/quarkusio/gizmo
- JSON Schema Draft-7: http://json-schema.org/draft-07/schema
