# Quarkus Flow - Core Guidelines

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

**For Bob-Shell**: When the user asks you to create a PR, you MUST run this command first and verify it succeeds before proceeding with `gh pr create`. If the build fails, fix the issues before creating the PR.

## Build Commands

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

### Pre-PR validation (REQUIRED)
```bash
./mvnw clean install -DskipITs=false
```

## Git & PR Guidelines

### Before submitting PRs

**REQUIRED**: Run the full validation build:
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
