# Quarkus Flow - Developer Makefile
#
# Quick commands for common development tasks.
# All commands run with parallel Maven execution (-T 15C) for maximum speed.

.PHONY: help quick-check build test-all verify clean format docs install-local native pre-commit

# Default target shows help
.DEFAULT_GOAL := help

# Maven parallel threads configuration
MAVEN_THREADS ?= 15C
MAVEN_OPTS := -T $(MAVEN_THREADS) -Dquarkus.log.level=OFF -ntp

help: ## Show this help message
	@echo "Quarkus Flow - Available Make Targets"
	@echo ""
	@echo "Common workflows:"
	@echo "  make quick-check    - Fast: Run all unit tests (skip docs, ITs)"
	@echo "  make build          - Build all modules (format, skip tests)"
	@echo "  make test-all       - Run all tests (unit + integration)"
	@echo "  make verify         - Full verification for PRs (required before PR)"
	@echo ""
	@echo "Other targets:"
	@echo "  make clean          - Clean all build artifacts"
	@echo "  make format         - Format code only (no build)"
	@echo "  make docs           - Build documentation only"
	@echo "  make install-local  - Install to local Maven repository"
	@echo "  make native         - Build native images (requires GraalVM)"
	@echo "  make pre-commit     - Quick pre-commit check (format + unit tests)"
	@echo ""
	@echo "Configuration:"
	@echo "  MAVEN_THREADS       - Number of parallel threads (default: 15C)"
	@echo ""
	@echo "Examples:"
	@echo "  make quick-check MAVEN_THREADS=8C"
	@echo "  make verify"
	@echo ""

quick-check: ## Fast check: Run all unit tests, skip docs and integration tests
	@echo "⚡ Quick Check: Unit tests only (skipping docs and ITs)"
	@mvn clean test $(MAVEN_OPTS) -pl '!docs' -DskipITs=true -Dno-format

build: ## Build all modules with formatting, skip tests
	@echo "🔨 Building all modules (format + compile, no tests)"
	@mvn clean install $(MAVEN_OPTS) -DskipTests -DskipITs=true

test-all: ## Run all tests (unit + integration)
	@echo "🧪 Running all tests (unit + integration)"
	@mvn clean install $(MAVEN_OPTS) -DskipITs=false -Dno-format

verify: ## Full verification build (required before creating PR)
	@echo "✅ Full verification (format + all tests + examples)"
	@echo "⚠️  This is what runs in CI - must pass before PR"
	@mvn clean install $(MAVEN_OPTS) -DskipITs=false

clean: ## Clean all build artifacts
	@echo "🧹 Cleaning all build artifacts"
	@mvn clean $(MAVEN_OPTS)

format: ## Format code only (no build or tests)
	@echo "💅 Formatting code"
	@mvn process-sources $(MAVEN_OPTS)

docs: ## Build documentation only
	@echo "📚 Building documentation"
	@mvn clean install -pl docs -am -DskipTests -DskipITs=true -T 1 -ntp

install-local: ## Install all artifacts to local Maven repository
	@echo "📦 Installing to local Maven repository"
	@mvn clean install $(MAVEN_OPTS) -DskipTests -DskipITs=true

native: ## Build native images (requires GraalVM)
	@echo "🚀 Building native images (this will take a while...)"
	@mvn clean install -Dnative -Dquarkus.native.container-build -DskipITs=false -T 1 -ntp
	@echo "✅ Native build complete!"

pre-commit: ## Pre-commit check: format + quick unit tests
	@echo "🔍 Pre-commit check (format + unit tests)"
	@mvn clean test $(MAVEN_OPTS) -Dno-format=false
	@echo "✅ Pre-commit check passed! Safe to commit."

# Module-specific targets
test-core: ## Test core module only
	@echo "🧪 Testing core module"
	@mvn clean test $(MAVEN_OPTS) -pl core/runtime,core/deployment -am

test-langchain4j: ## Test langchain4j module only
	@echo "🧪 Testing langchain4j module"
	@mvn clean test $(MAVEN_OPTS) -pl langchain4j/runtime,langchain4j/deployment -am

test-examples: ## Test all examples
	@echo "🧪 Testing all examples"
	@mvn clean test $(MAVEN_OPTS) -pl examples -am

# Development helpers
watch: ## Watch for changes and recompile (uses quarkus:dev on core)
	@echo "👀 Starting Quarkus dev mode (hot reload enabled)"
	@mvn quarkus:dev -pl core/runtime

.PHONY: test-core test-langchain4j test-examples watch
