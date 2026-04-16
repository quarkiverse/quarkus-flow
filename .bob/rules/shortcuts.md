# Quarkus Flow - Bob-Shell Shortcuts

Quick access to project documentation and resources.

## Project Documentation

For complete project information, refer to these files in the project root:
- **CLAUDE.md** - Complete guide for working with Quarkus Flow (build commands, testing, conventions, etc.)
- **CONTRIBUTING.md** - Contribution guidelines
- **README.md** - Project overview and quick start

When the user asks about project conventions, testing, or build processes, read the relevant sections from CLAUDE.md.

## Key Commands

```bash
# CRITICAL: Run before creating any PR
./mvnw clean install -DskipITs=false

# Standard build (unit tests only)
./mvnw clean install

# Quick build (skip tests)
./mvnw clean install -DskipTests

# Run docs locally
./mvnw -pl docs -am quarkus:dev
```

## External Resources

- [CNCF Serverless Workflow Spec](https://github.com/serverlessworkflow/specification)
- [LangChain4j Agentic Workflows](https://docs.langchain4j.dev/tutorials/agents)
- [Quarkus Extension Guide](https://quarkus.io/guides/writing-extensions)
- [Project Documentation](https://docs.quarkiverse.io/quarkus-flow/dev/)
- [GitHub Issues](https://github.com/quarkiverse/quarkus-flow/issues)

## Quick Reference

For detailed information, read the corresponding sections in CLAUDE.md:
- **Testing**: See CLAUDE.md "Testing" section
- **Code Conventions**: See CLAUDE.md "Code Conventions" section
- **Git & PR Guidelines**: See CLAUDE.md "Git & PR Guidelines" section
- **Module Dependencies**: See CLAUDE.md "Module Dependencies" section
- **Common Pitfalls**: See CLAUDE.md "Common Pitfalls" section