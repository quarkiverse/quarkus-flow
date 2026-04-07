# Contributing to Quarkus Flow

**Want to contribute? Great!**  
We welcome all contributions — bug reports, fixes, documentation, examples, and new features. This guide will help you get started.

## Quick Start

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/my-feature`
3. Make your changes
4. **Run the full build with integration tests**: `./mvnw clean install -DskipITs=false`
5. Commit your changes
6. Push and create a pull request

## Reporting Issues

Open an issue on [GitHub Issues](https://github.com/quarkiverse/quarkus-flow/issues).

Please include:
- Description of the issue
- Steps to reproduce
- Expected vs. actual behavior
- Quarkus, Java, and Maven versions
- Relevant logs or stack traces

## Before You Contribute

### Git Setup

Configure your Git authorship:

```bash
git config --global user.name "Your Full Name"
git config --global user.email your.email@example.com
```

We use this information to acknowledge your contributions in release announcements.

### Code Reviews

All submissions require review by at least one maintainer before being merged. We follow the [GitHub Pull Request Review Process](https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/reviewing-changes-in-pull-requests/about-pull-request-reviews).

## Build Requirements

### Prerequisites

- **Java 17+** (OpenJDK recommended)
- **Maven 3.9+**
- **Docker or Podman** (for integration tests using Testcontainers)
- **Git**

### Building the Project

Standard build (includes unit tests, skips integration tests):
```bash
./mvnw clean install
```

**Full build with integration tests** (required before PR):
```bash
./mvnw clean install -DskipITs=false
```

Build specific module:
```bash
./mvnw clean install -pl core -am
```

**Note**: `./mvnw clean install` automatically runs unit tests. Integration tests are skipped by default for faster builds.

### Project Structure

Quarkus Flow follows the standard Quarkus extension pattern:

```
quarkus-flow/
├── core/               # Core workflow engine
│   ├── runtime/       # Runtime code
│   ├── deployment/    # Build-time code
│   └── integration-tests/
├── messaging/         # Reactive Messaging support
├── langchain4j/       # LangChain4j integration
├── persistence/       # Workflow persistence
├── durable-kubernetes/# Kubernetes durable execution
├── scheduler/         # Scheduled workflows
├── docs/             # Antora documentation
└── examples/         # Example applications
```

**Key principle**: Never reference deployment code from runtime code.

## Testing Requirements

### Integration Tests Are Mandatory

**CRITICAL**: You MUST run the full test suite including integration tests before submitting a PR:

```bash
./mvnw clean install -DskipITs=false
```

Why this matters:
- Integration tests validate cross-module compatibility
- Testcontainers tests ensure proper Dev Services integration
- Prevents CI failures and broken builds
- Required by project policy

### Test Conventions

- **Unit tests**: `*Test.java` (run via Surefire, included in `./mvnw clean install`)
- **Integration tests**: `*IT.java` (run via Failsafe, requires `-DskipITs=false`)
- Use **AssertJ** for assertions (preferred in this project)
- Follow existing test patterns in each module

### Important Testing Notes

**Mocked LLM Calls**: Integration tests mock Ollama/LLM model calls to avoid resource-intensive operations in CI. Do not make real LLM API calls in tests.

**Parallel Execution**: Tests run in parallel. **Never use fixed ports** in your tests. If you need a port, use an unusual/random port or let the framework assign one (e.g., `@QuarkusTest` auto-assigns ports).

## Code Conventions

### General Guidelines

- Follow existing code style in the module you're editing
- No `@author` tags in Javadoc (we use Git history)
- Commits should be atomic and semantic
- Properly squash your PRs before final merge
- Limit lambdas/streams in runtime code (minimize footprint)

### Quarkus Extension Pattern

- **runtime/**: Application runtime code (CDI beans, runtime logic)
- **deployment/**: Build-time processors, code generation (`@BuildStep`)
- **integration-tests/**: Integration tests for the extension

### Serverless Workflow DSL

- Workflows extend `io.quarkiverse.flow.Flow`
- Use the fluent DSL: `io.serverlessworkflow.fluent.func.dsl.FuncDSL`
- Support both Java DSL and YAML definitions

### Commit Messages

Follow conventional commits style:

```
fix(core): handle null event in workflow execution
feat(langchain4j): add support for parallel agent execution
docs: update LangChain4j integration guide
```

Reference issues when applicable:
- `Fix #123` or `Closes #456`

## Documentation

Documentation uses **Antora** format in `docs/modules/ROOT/`.

When changing features:
1. Update relevant `.adoc` pages in `docs/modules/ROOT/pages/`
2. Update code examples in `docs/modules/ROOT/examples/`
3. Test locally: `./mvnw -pl docs -am quarkus:dev` (press 'w' to open browser)

## Pull Request Process

1. **Fork and create a feature branch**
2. **Make your changes** following code conventions
3. **Add/update tests** — tests are not optional
4. **Update documentation** if user-facing
5. **Run full build**: `./mvnw clean install -DskipITs=false`
6. **Commit with meaningful messages**
7. **Push to your fork**
8. **Open a Pull Request** with:
   - Clear title describing the change
   - Description of what changed and why
   - Reference to related issue(s)
   - Test plan

### PR Checklist

Your PR must include:
- [ ] Code changes
- [ ] Tests (unit and/or integration)
- [ ] Documentation updates (if applicable)
- [ ] Full build passed locally (`./mvnw clean install -DskipITs=false`)

## LLM Usage Policy

We welcome AI tools (ChatGPT, GitHub Copilot, Claude Code, etc.) that help developers be more productive.

However, to maintain a healthy community and high-quality contributions, the following expectations apply:

### Acceptable Use

- ✅ Use LLMs to **assist your development** (drafting code, writing docs, proposing fixes)
- ✅ **Understand, validate, and take responsibility** for all LLM-generated content
- ✅ Submit contributions that reflect **your own understanding and intent**
- ✅ Use LLMs to help you **write better**, not to **post more**

### Unacceptable Use

- ❌ Submitting code/tests/comments **copied directly from an LLM** with little or no human oversight
- ❌ Posting **large volumes of low-effort suggestions** or vague issues
- ❌ Submitting **AI-generated tests that don't validate actual behavior**
- ❌ Using bots/agents to **automatically open PRs or file issues** without human authorship

### Consequences

- Issues, PRs, or discussions violating this policy may be closed without detailed explanation
- Repeated violations may result in temporary or permanent restrictions

### If in Doubt

**Ask!** We're happy to help contributors use AI tools effectively without creating noise.

> This isn't about banning AI — it's about keeping Quarkus Flow collaborative, human-driven, and focused on quality.

## Getting Help

- **Issues**: [GitHub Issues](https://github.com/quarkiverse/quarkus-flow/issues)
- **Documentation**: https://docs.quarkiverse.io/quarkus-flow/dev/
- **Quarkus Zulip Chat**: Join the [#dev channel](https://quarkusio.zulipchat.com/#narrow/stream/187038-dev) and mention "Quarkus Flow" in your message
- **Quarkiverse Community**: https://github.com/quarkiverse

## License

All contributions to Quarkus Flow are licensed under the [Apache License 2.0](LICENSE).

---

<sub>*This contributing guide was inspired by the [Quarkus Contributing Guide](https://github.com/quarkusio/quarkus/blob/main/CONTRIBUTING.md).*</sub>
