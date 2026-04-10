# Developer Makefile Guide

This project includes a Makefile to simplify common development tasks. All commands use parallel Maven execution for maximum speed.

## Quick Start

```bash
# Show all available commands
make help

# Fast unit tests (recommended for quick feedback)
make quick-check

# Full verification before creating a PR (required)
make verify
```

## Common Workflows

### 🚀 Quick Development Cycle

```bash
# 1. Make your changes
# 2. Quick test (unit tests only, ~2-3 min)
make quick-check

# 3. If passing, run full verification
make verify
```

### 📝 Before Committing

```bash
# Format code and run unit tests
make pre-commit
```

### 🔨 Build Without Tests

```bash
# Fast build with formatting, no tests
make build
```

### 🧪 Run All Tests

```bash
# Run all tests (unit + integration, ~5 min)
make test-all
```

## Available Targets

### Main Targets

| Command | Description | Time | Use When |
|---------|-------------|------|----------|
| `make quick-check` | Unit tests only, skip docs | ~2-3 min | Quick iteration during development |
| `make build` | Format + compile, skip tests | ~1-2 min | Just need artifacts, no testing |
| `make test-all` | All tests (unit + integration) | ~5 min | Before committing changes |
| `make verify` | **Full verification (required for PRs)** | ~5 min | Before creating PR |

### Other Targets

| Command | Description |
|---------|-------------|
| `make clean` | Clean all build artifacts |
| `make format` | Format code only (no build) |
| `make docs` | Build documentation only |
| `make install-local` | Install to local Maven repository |
| `make native` | Build native images (requires GraalVM) |
| `make pre-commit` | Quick pre-commit check |

### Module-Specific Targets

```bash
# Test specific modules
make test-core          # Core module only
make test-langchain4j   # LangChain4j module only
make test-examples      # All examples
```

## Configuration

### Adjust Parallelism

By default, Make uses `15C` (15 threads per CPU core). Adjust if needed:

```bash
# Use 8 threads per core
make quick-check MAVEN_THREADS=8C

# Use fixed 4 threads total
make verify MAVEN_THREADS=4
```

### Clean Build

All targets use `clean` automatically. To just clean:

```bash
make clean
```

## Tips

### Faster Iteration

For fastest iteration during development:
```bash
make quick-check  # Unit tests only, skips docs (~2-3 min)
```

### Before Creating PR

Always run full verification:
```bash
make verify  # Must pass before PR
```

This is what CI runs, so catching issues locally saves time.

### IDE Users

You can still use your IDE's test runners for individual tests. The Makefile is for:
- Full project validation
- CI-equivalent local builds
- Quick whole-project checks

## Troubleshooting

### Build Fails on Windows

The Makefile uses Unix-style commands. On Windows, use:
- Git Bash
- WSL (Windows Subsystem for Linux)
- Or run Maven commands directly (see `Makefile` for exact commands)

### Out of Memory

Reduce parallelism:
```bash
make verify MAVEN_THREADS=4C
```

### Port Conflicts

If you get port binding errors, another instance might be running:
```bash
# Kill any running Quarkus processes
pkill -f quarkus

# Clean and retry
make clean
make quick-check
```

## What Gets Run

### `make quick-check`
```bash
mvn clean test -T 15C -pl '!docs' -DskipITs=true -Dno-format -Dquarkus.log.level=OFF -ntp
```
- Runs unit tests only
- Skips docs module (saves ~2 min)
- Skips integration tests
- Uses existing formatting

### `make verify`
```bash
mvn clean install -T 15C -DskipITs=false -Dquarkus.log.level=OFF -ntp
```
- Full build with formatting
- All unit tests
- All integration tests
- All examples
- **This is what CI runs**

## See Also

- Main README: [README.md](README.md)
- Claude Code Guide: [CLAUDE.md](CLAUDE.md)
- Contributing Guide: [CONTRIBUTING.md](CONTRIBUTING.md)
