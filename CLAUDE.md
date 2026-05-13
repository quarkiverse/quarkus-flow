# Quarkus Flow - Claude Code Guide

This guide helps Claude Code (and contributors using it) work effectively with the Quarkus Flow project.

## ⚠️ CRITICAL: Required Workflow Validation

**BEFORE CREATING ANY PULL REQUEST**, you MUST run the full build with integration tests:

```bash
./mvnw clean install -DskipITs=false
```

This is **NON-NEGOTIABLE**. This command ensures:
- All unit tests pass
- All integration tests pass (including cross-module compatibility)
- No build errors across all modules
- Dev Services (Testcontainers) work correctly

**For Claude Code**: When the user asks you to create a PR, you MUST run this command first and verify it succeeds before proceeding with `gh pr create`. If the build fails, fix the issues before creating the PR.

## Project Overview

**Quarkus Flow** is a lightweight workflow engine for Quarkus based on the CNCF Serverless Workflow specification. It supports classic workflows and Agentic AI orchestrations with LangChain4j integration.

- **Tech Stack**: Java 17+, Maven, Quarkus framework
- **Architecture**: Multi-module Quarkus extension with runtime/deployment split
- **Spec**: CNCF Serverless Workflow (https://serverlessworkflow.io/)
- **Docs**: https://docs.quarkiverse.io/quarkus-flow/dev/

## Repository Structure

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

## Build & Test Commands

### Standard build (includes unit tests)
```bash
./mvnw clean install
```
This automatically runs unit tests. Integration tests are skipped by default.

### Quick build (skip all tests)
```bash
./mvnw clean install -DskipTests
```

### Build specific module
```bash
./mvnw clean install -pl core -am  # -am = also make dependencies
```

### Run documentation locally
```bash
./mvnw -pl docs -am quarkus:dev
# Press 'w' when Quarkus starts to open the docs site
```

### Pre-PR validation (REQUIRED)
```bash
./mvnw clean install -DskipITs=false
```

**Why this matters**:
- Integration tests validate cross-module compatibility
- Catches issues with Quarkus Dev Services and Testcontainers
- Ensures all modules work together correctly
- Prevents CI failures and broken builds
- Required by project policy before any PR creation

**When to run**: Before creating ANY pull request. No exceptions.

## Testing

- **Unit tests**: Run via Surefire (`**/src/test/**/*Test.java`) - automatically included in `./mvnw clean install`
- **Integration tests**: Run via Failsafe (`**/src/test/**/*IT.java`) - require `-DskipITs=false`
- **Test matrix**: Ubuntu + Windows, JDK 17/21/25
- Integration tests use Quarkus Dev Services (Testcontainers)

### Important Testing Rules

**Mocked LLM Calls**: Integration tests mock Ollama/LLM model calls to avoid resource-intensive operations in CI. Never make real LLM API calls in tests.

**Parallel Execution**: Tests run in parallel. **Never use fixed ports** (e.g., 8080). Use unusual/random ports or let Quarkus assign them automatically.

**Special Test Profiles**: Some tests require optional dependencies that are isolated with Maven profiles to avoid affecting other tests:

```bash
# Test quarkus-logging-json integration (adds dependency only for this test)
./mvnw test -pl core/integration-tests -Ptest-logging-json -Dtest=StructuredLoggingWithQuarkusLoggingJsonTest
```

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

## Documentation

Documentation uses **Antora** format:
- Source: `docs/modules/ROOT/`
- Pages: `docs/modules/ROOT/pages/*.adoc` (AsciiDoc)
- Examples: `docs/modules/ROOT/examples/` (Java code snippets)
- Navigation: `docs/modules/ROOT/nav.adoc`

When updating features:
1. Update relevant `.adoc` pages
2. Add/update code examples if needed
3. Test docs locally with `./mvnw -pl docs quarkus:dev`

## Architecture Decision Records (ADR)

**Location**: `adr/` directory in project root

This project uses ADRs to document significant architectural and design decisions. When working with Claude Code's superpowers skills (brainstorming, planning), design specifications should be saved to the `adr/` directory, NOT `docs/superpowers/specs/`.

**ADR Format**:
- Filename: `adr/YYYY-MM-DD-<topic>-design.md`
- Use Markdown format
- Include: context, decision, consequences, alternatives considered
- Reference related issues/PRs

**When to create an ADR**:
- New modules or extensions (e.g., `runner/` module)
- Significant API changes or additions
- Architecture decisions that affect multiple modules
- Security model changes
- Breaking changes or major refactorings

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

## Git & PR Guidelines

### Before submitting PRs

**REQUIRED**: Run the full validation build (see top of this document):
```bash
./mvnw clean install -DskipITs=false
```

This must pass completely before creating a PR. Do not skip this step.

### Commit messages
- Follow conventional commits style (see git log for examples)
- Use imperative mood and keep commits atomic
- Reference issues: `Fix #123` or `Closes #456`
- Keep commits focused and atomic

### PR checklist
- [ ] **Full build with integration tests passed** (`./mvnw clean install -DskipITs=false`)
- [ ] Code builds successfully on all modules
- [ ] All tests pass (unit + integration)
- [ ] Documentation updated if user-facing change
- [ ] Examples updated if API changed
- [ ] No unnecessary formatting changes in unrelated code

## Module Dependencies

When adding dependencies:
- Check if already in `quarkus-bom` (Quarkus version: see `pom.xml`)
- Check if in `serverlessworkflow-bom`
- For new deps, add to parent `<dependencyManagement>` first
- Use `<properties>` to define dependency versions
- If a dependency is used by multiple modules, the property must live in their parent module's `pom.xml`
- Avoid version conflicts with Quarkus core

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

## Common Pitfalls

1. **CRITICAL**: **Don't** create PRs without running `./mvnw clean install -DskipITs=false` first
2. **Don't** reference deployment code from runtime
3. **Don't** add dependencies without checking BOMs first
4. **Don't** skip integration tests - they catch cross-module issues
5. **Do** run the full build with ITs before every PR
6. **Do** check existing examples before adding new patterns
7. **Do** keep docs in sync with code changes

## External Resources

- CNCF Serverless Workflow Spec: https://github.com/serverlessworkflow/specification
- LangChain4j Agentic Workflows: https://docs.langchain4j.dev/tutorials/agents
- Quarkus Extension Guide: https://quarkus.io/guides/writing-extensions
- Project docs: https://docs.quarkiverse.io/quarkus-flow/dev/

## Getting Help

- Issues: https://github.com/quarkiverse/quarkus-flow/issues
- Discussions: GitHub Discussions
- Quarkiverse: https://github.com/quarkiverse

## Claude Code Hooks Configuration

This project uses **hooks** to enforce quality gates automatically. Hooks are configured in `.claude/settings.json`.

### Active Hooks

**Pre-PR Validation Hook** (CRITICAL)
- **Trigger**: When attempting to create a PR (`gh pr create`)
- **Action**: Runs `mvn clean install -DskipITs=false`
- **Behavior**: Blocks PR creation if build/tests fail
- **Why**: Prevents broken builds from reaching GitHub

### Managing Hooks

View active hooks in a Claude session:
```bash
claude
> /hooks
```

Temporarily disable (emergency only):
```bash
CLAUDE_DISABLE_HOOKS=true claude
```

See `.claude/README.md` for complete hook documentation.

---

**For Claude Code users**: This file provides context for AI-assisted development. Feel free to ask Claude to "check CLAUDE.md" when working on this project for guidance on conventions and structure.
