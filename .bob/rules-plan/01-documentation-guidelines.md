# Quarkus Flow - Plan Mode Guidelines

## Documentation

Documentation uses **Antora** format:
- Source: `docs/modules/ROOT/`
- Pages: `docs/modules/ROOT/pages/*.adoc` (AsciiDoc)
- Examples: `docs/modules/ROOT/examples/` (Java code snippets)
- Navigation: `docs/modules/ROOT/nav.adoc`

### Run documentation locally
```bash
./mvnw -pl docs -am quarkus:dev
# Press 'w' when Quarkus starts to open the docs site
```

### When updating features
1. Update relevant `.adoc` pages
2. Add/update code examples if needed
3. Test docs locally with `./mvnw -pl docs quarkus:dev`

## Architecture Patterns

### Quarkus Extension Pattern
Each module follows Quarkus extension structure:
- `runtime/`: Code that runs in the application
- `deployment/`: Build-time processors, code generation
- Never reference deployment code from runtime code

### Module Organization
- Core workflow engine in `core/`
- Feature extensions in separate modules (`messaging/`, `langchain4j/`, etc.)
- Integration tests in `integration-tests/` submodules
- Examples in `examples/` directory

### Serverless Workflow DSL
- Workflows extend `io.quarkiverse.flow.Flow`
- Use the fluent DSL from `io.serverlessworkflow.fluent.func.dsl.FuncDSL`
- Support both Java DSL and YAML workflow definitions

## Planning Development Tasks

### Adding a new workflow task type
1. Define task in `core/runtime` (e.g., new function handler)
2. Add build-time registration in `core/deployment`
3. Add tests in `core/integration-tests`
4. Document in `docs/modules/ROOT/pages/`
5. Optionally add example in `examples/`

### Adding LangChain4j integration features
- Work in `langchain4j/` module
- Understand the difference between:
  - Standard LangChain4j AI services (basic usage)
  - Agentic Workflow API (requires `quarkus-langchain4j-agentic`)
- Integration tests often use mocked LLM responses

### Working with messaging
- Module: `messaging/`
- Auto-activates when Kafka/AMQP connector present
- Uses SmallRye Reactive Messaging
- Key config: `mp.messaging.incoming.flow-in`, `mp.messaging.outgoing.flow-out`

## Module Dependencies

When planning new features that require dependencies:
- Check if already in `quarkus-bom` (Quarkus version: see `pom.xml`)
- Check if in `serverlessworkflow-bom`
- For new deps, add to parent `<dependencyManagement>` first
- Use `<properties>` to define dependency versions
- If a dependency is used by multiple modules, the property must live in their parent module's `pom.xml`
- Avoid version conflicts with Quarkus core
