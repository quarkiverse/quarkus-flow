.PHONY: help build-minimal build-standard build-messaging build-all run-example clean test dev

# Default target
.DEFAULT_GOAL := help

VARIANT ?= minimal
WORKFLOWS ?= ./workflows

# Export variables for docker-compose
export WORKFLOWS

help: ## Show this help message
	@echo "Quarkus Flow Runner - Available targets:"
	@echo ""
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-30s\033[0m %s\n", $$1, $$2}'
	@echo ""
	@echo "Variables:"
	@echo "  VARIANT     Image variant to build (minimal|standard|messaging) [default: minimal]"
	@echo "  WORKFLOWS   Path to workflows directory to mount [default: ./workflows]"
	@echo ""
	@echo "Examples:"
	@echo "  make build-minimal                    Build minimal variant"
	@echo "  make build-all                        Build all variants"
	@echo "  make docker-compose-up                Start minimal variant"
	@echo "  make docker-compose-up-standard       Start with PostgreSQL"
	@echo "  make docker-compose-up WORKFLOWS=/tmp/my-flows  Mount custom workflow directory"
	@echo "  make dev                              Run in dev mode (MVStore)"

build-minimal: ## Build minimal variant (MVStore, single replica)
	@./scripts/build-image.sh minimal

build-standard: ## Build standard variant (JPA + PostgreSQL + HA)
	@./scripts/build-image.sh standard

build-messaging: ## Build messaging variant (JPA + PostgreSQL + Kafka + HA)
	@./scripts/build-image.sh messaging

build-all: build-minimal build-standard build-messaging ## Build all image variants

build: ## Build specified variant (use VARIANT variable)
	@./scripts/build-image.sh $(VARIANT)

run-example: ## Run example workflow with minimal image
	@./scripts/run-example.sh

stop-example: ## Stop and remove the example container
	@echo "🛑 Stopping example container..."
	@docker stop flow-runner-example 2>/dev/null || true
	@docker rm flow-runner-example 2>/dev/null || true
	@echo "✅ Container stopped and removed"

clean: stop-example ## Clean up containers and build artifacts
	@echo "🧹 Cleaning build artifacts..."
	@cd ../.. && mvn clean -pl runner/app
	@echo "✅ Clean complete"

test: ## Run tests
	@echo "🧪 Running tests..."
	@cd ../.. && mvn test -pl runner/app

dev: ## Run in development mode (Quarkus dev mode with MVStore)
	@echo "🚀 Starting Quarkus in dev mode..."
	@echo "📍 Open http://localhost:8080 for the dashboard"
	@echo "📍 Press 'w' to open OpenAPI docs"
	@echo ""
	@cd ../.. && mvn quarkus:dev -pl runner/app -am

docker-compose-up: ## Start minimal variant with docker-compose
	@echo "🐳 Starting minimal variant with docker-compose..."
	@docker-compose up -d
	@echo "✅ Runner started on http://localhost:8080"

docker-compose-down: ## Stop minimal variant docker-compose
	@docker-compose down

docker-compose-up-standard: ## Start standard variant (PostgreSQL) with docker-compose
	@echo "🐳 Starting standard variant with PostgreSQL..."
	@docker-compose -f docker-compose-standard.yml up -d
	@echo "✅ Runner started on http://localhost:8080 (with PostgreSQL)"

docker-compose-down-standard: ## Stop standard variant docker-compose
	@docker-compose -f docker-compose-standard.yml down

docker-compose-up-messaging: ## Start messaging variant (PostgreSQL + Kafka) with docker-compose
	@echo "🐳 Starting messaging variant with PostgreSQL + Kafka..."
	@docker-compose -f docker-compose-messaging.yml up -d
	@echo "✅ Runner started on http://localhost:8080 (with PostgreSQL + Kafka)"

docker-compose-down-messaging: ## Stop messaging variant docker-compose
	@docker-compose -f docker-compose-messaging.yml down

docker-compose-logs: ## Show logs from docker-compose (minimal)
	@docker-compose logs -f

docker-compose-logs-standard: ## Show logs from docker-compose (standard)
	@docker-compose -f docker-compose-standard.yml logs -f

docker-compose-logs-messaging: ## Show logs from docker-compose (messaging)
	@docker-compose -f docker-compose-messaging.yml logs -f

logs: ## Show logs from running example container
	@docker logs -f flow-runner-example 2>/dev/null || echo "❌ Container 'flow-runner-example' not running"

health: ## Check health of running example container
	@echo "🏥 Checking health..."
	@curl -s http://localhost:8080/q/health | jq '.' || echo "❌ Container not responding"

metrics: ## Show Prometheus metrics from running container
	@echo "📊 Fetching metrics..."
	@curl -s http://localhost:8080/q/metrics | head -50

push-minimal: ## Push minimal image to registry
	@docker push quay.io/quarkiverse/quarkus-flow-runner:latest-minimal
	@echo "✅ Pushed minimal image"

push-standard: ## Push standard image to registry
	@docker push quay.io/quarkiverse/quarkus-flow-runner:latest-standard
	@echo "✅ Pushed standard image"

push-messaging: ## Push messaging image to registry
	@docker push quay.io/quarkiverse/quarkus-flow-runner:latest-messaging
	@echo "✅ Pushed messaging image"

push-all: push-minimal push-standard push-messaging ## Push all images to registry
