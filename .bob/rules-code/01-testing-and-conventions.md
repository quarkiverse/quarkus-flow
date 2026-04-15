# Quarkus Flow - Code Mode Guidelines

## Testing

- **Unit tests**: Run via Surefire (`**/src/test/**/*Test.java`) - automatically included in `./mvnw clean install`
- **Integration tests**: Run via Failsafe (`**/src/test/**/*IT.java`) - require `-DskipITs=false`
- **Test matrix**: Ubuntu + Windows, JDK 17/21/25
- Integration tests use Quarkus Dev Services (Testcontainers)

### Important Testing Rules

**Mocked LLM Calls**: Integration tests mock Ollama/LLM model calls to avoid resource-intensive operations in CI. Never make real LLM API calls in tests.

**Parallel Execution**: Tests run in parallel. **Never use fixed ports** (e.g., 8080). Use unusual/random ports or let Quarkus assign them automatically.

When writing tests:
- Use AssertJ for assertions (preferred in this project)
- Follow existing test patterns in each module
- Integration tests go in `integration-tests/` submodules
- **Examples tests**: Mock using `quarkus-mockito`
- **Integration tests**: Mock using `WireMock` for external services
- **Test naming**: Use `snake_case` with `@DisplayName` annotation (e.g., `@DisplayName("test_workflow_execution_completes")`)
- Mock external services (LLMs, APIs) to keep tests fast and reliable

## Code Conventions

### Quarkus Extension Pattern
Each module follows Quarkus extension structure:
- `runtime/`: Code that runs in the application
- `deployment/`: Build-time processors, code generation
- Never reference deployment code from runtime code

### CDI & Build Items
- Use `@BuildStep` in deployment modules for build-time processing
- Runtime beans use standard CDI annotations (`@ApplicationScoped`, etc.)
- Build items are the contract between build steps

### Serverless Workflow DSL
- Workflows extend `io.quarkiverse.flow.Flow`
- Use the fluent DSL from `io.serverlessworkflow.fluent.func.dsl.FuncDSL`
- Support both Java DSL and YAML workflow definitions

## Common Development Tasks

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

## Helpful Context for AI Assistance

### When asked about workflow features
- Check CNCF Serverless Workflow spec compliance first
- Refer to `io.serverlessworkflow` packages for DSL
- Examples in `examples/` show real usage patterns

### When debugging build issues
- Quarkus extensions have strict runtime/deployment separation
- Build item issues often mean wrong module boundary crossed
- Dev Services (Testcontainers) can cause test failures on Windows

### When working with agentic workflows
- Understand three usage patterns (see README):
  1. Java DSL calling LangChain4j beans
  2. Annotations generating workflows
  3. Hybrid approach
- `@SequenceAgent`, `@ParallelAgent`, etc. are LangChain4j Agentic API
