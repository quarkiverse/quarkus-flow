# Quarkus Flow - Ask Mode Guidelines

## Helpful Context for Questions

### When asked about workflow features
- Check CNCF Serverless Workflow spec compliance first
- Refer to `io.serverlessworkflow` packages for DSL
- Examples in `examples/` show real usage patterns
- Documentation: https://docs.quarkiverse.io/quarkus-flow/dev/

### When asked about build issues
- Quarkus extensions have strict runtime/deployment separation
- Build item issues often mean wrong module boundary crossed
- Dev Services (Testcontainers) can cause test failures on Windows
- Always check if integration tests were run with `-DskipITs=false`

### When asked about agentic workflows
- Understand three usage patterns:
  1. Java DSL calling LangChain4j beans
  2. Annotations generating workflows (`@SequenceAgent`, `@ParallelAgent`, etc.)
  3. Hybrid approach
- LangChain4j Agentic API requires `quarkus-langchain4j-agentic` dependency
- Integration tests mock LLM responses to avoid resource-intensive operations

### When asked about testing
- Unit tests: `**/src/test/**/*Test.java` (run automatically with `./mvnw clean install`)
- Integration tests: `**/src/test/**/*IT.java` (require `-DskipITs=false`)
- Tests run in parallel - never use fixed ports
- Use AssertJ for assertions (project preference)
- Mock external services (LLMs, APIs) to keep tests fast

### When asked about messaging
- Module: `messaging/`
- Auto-activates when Kafka/AMQP connector present
- Uses SmallRye Reactive Messaging
- Key config: `mp.messaging.incoming.flow-in`, `mp.messaging.outgoing.flow-out`

### When asked about persistence
- Multiple backends: JPA, MVStore, Redis
- Common interface in `persistence/common`
- Test utilities in `persistence/test-common`

### When asked about Kubernetes durable execution
- Module: `durable-kubernetes/`
- Kubernetes-native durable execution support
- Check `manifests/` for deployment examples

## External Resources to Reference

- CNCF Serverless Workflow Spec: https://github.com/serverlessworkflow/specification
- LangChain4j Agentic Workflows: https://docs.langchain4j.dev/tutorials/agents
- Quarkus Extension Guide: https://quarkus.io/guides/writing-extensions
- Project docs: https://docs.quarkiverse.io/quarkus-flow/dev/
- Issues: https://github.com/quarkiverse/quarkus-flow/issues
- Discussions: GitHub Discussions
- Quarkiverse: https://github.com/quarkiverse

## Repository Structure Reference

```
quarkus-flow/
├── core/                    # Core workflow engine
│   ├── runtime/            # Runtime code
│   ├── deployment/         # Build-time/deployment code
│   ├── runtime-dev/        # Dev mode features
│   └── integration-tests/  # Integration tests
├── messaging/              # Reactive Messaging integration
├── langchain4j/           # LangChain4j integration for agentic workflows
├── persistence/           # Workflow persistence support
├── durable-kubernetes/    # Kubernetes-native durable execution
├── scheduler/             # Scheduled workflow execution
├── docs/                  # Antora documentation
├── examples/              # Example applications
└── bom/                   # Bill of Materials for dependency management
```
