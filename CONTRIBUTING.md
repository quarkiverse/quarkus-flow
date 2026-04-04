# Contributing to Quarkus Flow

[![Documentation](https://img.shields.io/badge/docs-DeepWiki-blue)](https://deepwiki.com/quarkiverse/quarkus-flow)

Thank you for your interest in contributing to Quarkus Flow! This guide will help you get started with contributing to the project.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Setup](#development-setup)
- [Project Structure](#project-structure)
- [Building the Project](#building-the-project)
- [Running Tests](#running-tests)
- [Code Style and Conventions](#code-style-and-conventions)
- [Continuous Integration](#continuous-integration)
- [Documentation](#documentation)
- [Submitting Changes](#submitting-changes)
- [Release Process](#release-process)
- [Getting Help](#getting-help)

## Code of Conduct

This project follows the [Quarkiverse Code of Conduct](https://github.com/quarkiverse/.github/blob/main/CODE_OF_CONDUCT.md). By participating, you are expected to uphold this code.

## Getting Started

### Prerequisites

- **JDK 17 or later** (JDK 17, 21, and 25 are tested in CI)
- **Maven 3.9+**
- **Git**
- **Docker** (optional, for running integration tests with Dev Services)

### Fork and Clone

1. Fork the repository on GitHub
2. Clone your fork locally:
   ```bash
   git clone https://github.com/YOUR_USERNAME/quarkus-flow.git
   cd quarkus-flow
   ```
3. Add the upstream repository:
   ```bash
   git remote add upstream https://github.com/quarkiverse/quarkus-flow.git
   ```

## Development Setup

### Initial Build

Before starting development, build the project to ensure everything works:

```bash
./mvnw clean install -DskipTests
```

### IDE Setup

The project uses standard Maven structure and works with any Java IDE:

- **IntelliJ IDEA**: Import as Maven project
- **Eclipse**: Import as existing Maven project
- **VS Code**: Use Java Extension Pack

## Project Structure

Quarkus Flow is a multi-module Maven project:

```
quarkus-flow/
├── core/                      # Core workflow engine
│   ├── deployment/           # Build-time processing
│   ├── runtime/              # Runtime components
│   ├── runtime-dev/          # Dev UI and dev-mode features
│   └── integration-tests/    # Integration tests
├── langchain4j/              # LangChain4j integration
│   ├── deployment/
│   ├── integration-tests/
├── messaging/                # Messaging support (Kafka, AMQP, etc.)
├── persistence/              # Persistence implementations
│   ├── common/              # Common persistence abstractions
│   ├── jpa/                 # JPA-based persistence
│   ├── mvstore/             # MVStore-based persistence
│   ├── redis/               # Redis-based persistence
│   └── test-common/         # Shared test utilities
├── scheduler/                # Scheduler integration
├── durable-kubernetes/       # Kubernetes durable workflows
├── bom/                      # Bill of Materials
├── docs/                     # Antora documentation
├── examples/                 # Example applications
└── quarkus-platform-checks/  # Platform compatibility checks
```

### Key Concepts

- **Flow**: Base class for defining workflows using Java DSL
- **WorkflowDefinition**: Compiled workflow ready for execution
- **Deployment modules**: Build-time processing and CDI bean discovery
- **Runtime modules**: Runtime execution and integration

## Building the Project

### Full Build

```bash
./mvnw clean install
```

### Skip Tests

```bash
./mvnw clean install -DskipTests
```

### Skip Integration Tests Only

```bash
./mvnw clean install -DskipITs=true
```

### Build with Code Coverage

```bash
./mvnw clean install -Pcode-coverage
```

Coverage reports are generated in `target/jacoco-report/`.

### Build Documentation

```bash
./mvnw -pl docs -am quarkus:dev
```

Then press `w` when Quarkus starts to open the documentation site in your browser.

## Running Tests

### Test Structure

The project uses:
- **JUnit 5** for unit and integration tests
- **Quarkus Test Framework** (`@QuarkusTest`, `@QuarkusUnitTest`)
- **AssertJ** for fluent assertions
- **REST Assured** for REST API testing
- **Testcontainers** (via Quarkus Dev Services) for external dependencies

### Unit Tests

```bash
./mvnw test
```

### Integration Tests

```bash
./mvnw verify
```

### Run Specific Test

```bash
./mvnw test -Dtest=FlowDefinitionInjectionTest
```

### Run Tests in a Specific Module

```bash
./mvnw test -pl core/deployment
```

### Testing Best Practices

1. **Use `@QuarkusTest` for integration tests** that need a full Quarkus application
2. **Use `@QuarkusUnitTest` for focused tests** that only need specific beans/extensions
3. **Leverage Dev Services** for external dependencies (databases, message brokers, etc.)
4. **Use AssertJ** for readable assertions:
   ```java
   assertThat(result)
       .isNotNull()
       .extracting("message")
       .isEqualTo("expected value");
   ```
5. **Test both success and failure scenarios**
6. **Keep tests fast and isolated**

### Example Test Structure

```java
package io.quarkiverse.flow.deployment.test;

import static org.assertj.core.api.Assertions.assertThat;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

public class MyWorkflowTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(MyWorkflow.class));

    @Test
    public void testWorkflowExecution() {
        // Test implementation
    }
}
```

## Code Style and Conventions

### Formatting

The project uses **automatic code formatting** via Maven plugins:

- **formatter-maven-plugin**: Formats Java code
- **impsort-maven-plugin**: Sorts imports

Formatting is applied automatically during the build. To format manually:

```bash
./mvnw formatter:format impsort:sort
```

### Code Conventions

1. **Follow Java naming conventions**
   - Classes: `PascalCase`
   - Methods/variables: `camelCase`
   - Constants: `UPPER_SNAKE_CASE`

2. **Use meaningful names**
   - Prefer descriptive names over abbreviations
   - Example: `WorkflowDefinition` not `WfDef`

3. **Keep methods focused**
   - Single Responsibility Principle
   - Extract complex logic into separate methods

4. **Document public APIs**
   - Use Javadoc for public classes and methods
   - Include `@param`, `@return`, and `@throws` tags

5. **Prefer immutability**
   - Use `final` for variables that don't change
   - Consider immutable data structures

6. **CDI Best Practices**
   - Use `@ApplicationScoped` for stateless beans
   - Use `@Dependent` for stateful beans
   - Avoid `@RequestScoped` unless necessary

### Example Code Style

```java
package io.quarkiverse.flow;

import jakarta.enterprise.context.ApplicationScoped;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.spec.WorkflowBuilder;

/**
 * Example workflow demonstrating best practices.
 */
@ApplicationScoped
public class ExampleWorkflow extends Flow {
    
    /**
     * Defines the workflow descriptor.
     *
     * @return the workflow definition
     */
    @Override
    public Workflow descriptor() {
        return WorkflowBuilder
                .workflow("example", "examples", "v1")
                .tasks(t -> t.set("result", "${ .input }"))
                .build();
    }
}
```

## Continuous Integration

### GitHub Actions Workflows

The project uses several CI workflows:

#### 1. **Build** (`.github/workflows/build.yml`)
- Runs on: Push to `main`, Pull Requests
- Tests on: Ubuntu & Windows
- Java versions: 17, 21, 25
- Executes: `mvn clean install -DskipITs=true`

#### 2. **Coverage** (`.github/workflows/coverage.yml`)
- Runs on: Pull Requests
- Generates JaCoCo coverage reports
- Posts coverage comments on PRs
- Minimum coverage: 40% overall, 40% changed files

#### 3. **Integration Tests** (`.github/workflows/build-it.yml`)
- Runs full integration test suite
- Uses Docker for external dependencies

#### 4. **Native Build** (`.github/workflows/native-nigthly-ci.yaml`)
- Nightly native image builds
- Ensures GraalVM compatibility

#### 5. **Quarkus Platform Tests**
- Tests against Quarkus snapshots and platform
- Ensures compatibility with latest Quarkus versions

### CI Requirements

All PRs must:
- ✅ Pass all builds (Ubuntu & Windows)
- ✅ Pass all tests
- ✅ Meet code coverage requirements (40%)
- ✅ Pass code formatting checks
- ✅ Have no merge conflicts

### Running CI Checks Locally

Before pushing, run:

```bash
# Format code
./mvnw formatter:format impsort:sort

# Run tests with coverage
./mvnw clean install -Pcode-coverage

# Check formatting (will fail if not formatted)
./mvnw formatter:validate impsort:check
```

## Documentation

### Documentation Structure

Documentation is written in **AsciiDoc** using **Antora**:

```
docs/
├── modules/
│   └── ROOT/
│       ├── pages/          # Documentation pages
│       ├── examples/       # Code examples
│       └── images/         # Images and diagrams
└── antora.yml             # Antora configuration
```

### Writing Documentation

1. **Create or edit `.adoc` files** in `docs/modules/ROOT/pages/`
2. **Add code examples** in `docs/modules/ROOT/examples/`
3. **Use AsciiDoc syntax**: [AsciiDoc Quick Reference](https://docs.asciidoctor.org/asciidoc/latest/syntax-quick-reference/)

### Preview Documentation Locally

```bash
./mvnw -pl docs -am quarkus:dev
```

Press `w` to open the documentation site.

### Documentation Best Practices

- Keep examples simple and focused
- Test code examples to ensure they work
- Use cross-references for related topics
- Include both conceptual and practical content
- Add diagrams where helpful

## Submitting Changes

### Before Submitting

1. **Ensure all tests pass**:
   ```bash
   ./mvnw clean install
   ```

2. **Format your code**:
   ```bash
   ./mvnw formatter:format impsort:sort
   ```

3. **Update documentation** if needed

4. **Add tests** for new features or bug fixes

### Commit Messages

Follow conventional commit format:

```
<type>(<scope>): <subject>

<body>

<footer>
```

**Types:**
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation changes
- `style`: Code style changes (formatting, etc.)
- `refactor`: Code refactoring
- `test`: Adding or updating tests
- `chore`: Maintenance tasks

**Examples:**
```
feat(langchain4j): add support for parallel agent execution

Implements parallel execution of LangChain4j agents using
the @ParallelAgent annotation.

Closes #123
```

```
fix(persistence): resolve transaction rollback issue

Fixes a bug where transactions were not properly rolled back
on workflow failure.

Fixes #456
```

### Pull Request Process

1. **Create a feature branch**:
   ```bash
   git checkout -b feature/my-feature
   ```

2. **Make your changes** and commit:
   ```bash
   git add .
   git commit -m "feat: add new feature"
   ```

3. **Push to your fork**:
   ```bash
   git push origin feature/my-feature
   ```

4. **Open a Pull Request** on GitHub

5. **Fill out the PR template** with:
   - Description of changes
   - Related issues
   - Testing performed
   - Documentation updates

6. **Respond to review feedback**

7. **Ensure CI passes**

### PR Review Process

- PRs require approval from maintainers
- Address review comments promptly
- Keep PRs focused and reasonably sized
- Rebase on `main` if needed to resolve conflicts

## Release Process

Releases are managed by maintainers using GitHub Actions:

1. **Prepare Release**: `.github/workflows/release-prepare.yml`
   - Updates version numbers
   - Creates release branch

2. **Perform Release**: `.github/workflows/release-perform.yml`
   - Builds and publishes artifacts
   - Creates GitHub release
   - Updates documentation

Current version: See `.github/project.yml`

## Getting Help

### Resources

- 📚 **Documentation**: https://docs.quarkiverse.io/quarkus-flow/dev/
- 🤖 **DeepWiki**: https://deepwiki.com/quarkiverse/quarkus-flow
- 💬 **GitHub Discussions**: https://github.com/quarkiverse/quarkus-flow/discussions
- 🐛 **Issue Tracker**: https://github.com/quarkiverse/quarkus-flow/issues

### Asking Questions

- Check existing documentation and issues first
- Use GitHub Discussions for general questions
- Use GitHub Issues for bug reports and feature requests
- Be specific and provide context

### Reporting Bugs

When reporting bugs, include:
- Quarkus Flow version
- Quarkus version
- Java version
- Operating system
- Minimal reproduction example
- Stack traces and error messages

Use the bug report template: [New Bug Report](https://github.com/quarkiverse/quarkus-flow/issues/new?template=bug_report.yml)

### Suggesting Features

When suggesting features, include:
- Use case and motivation
- Proposed API or behavior
- Examples of how it would be used
- Alternatives considered

Use the feature request template: [New Feature Request](https://github.com/quarkiverse/quarkus-flow/issues/new?template=feature_request.yml)

## Testing Libraries Reference

The project uses the following testing libraries:

| Library | Purpose | Documentation |
|---------|---------|---------------|
| **JUnit 5** | Test framework | https://junit.org/junit5/ |
| **AssertJ** | Fluent assertions | https://assertj.github.io/doc/ |
| **Quarkus Test** | Quarkus testing support | https://quarkus.io/guides/getting-started-testing |
| **REST Assured** | REST API testing | https://rest-assured.io/ |
| **Mockito** | Mocking framework | https://site.mockito.org/ |
| **Testcontainers** | Container-based testing | https://www.testcontainers.org/ |
| **JaCoCo** | Code coverage | https://www.jacoco.org/ |

## Additional Notes

### Performance Considerations

- Quarkus Flow is designed for fast startup and low memory footprint
- Consider native image compatibility when adding dependencies
- Profile performance-critical code paths

### Security

- Report security vulnerabilities privately to the maintainers
- Do not open public issues for security problems
- Follow responsible disclosure practices

### License

By contributing, you agree that your contributions will be licensed under the Apache License 2.0.

---

Thank you for contributing to Quarkus Flow! 🚀