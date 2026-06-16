# Runner Scripts

This directory contains helper scripts for building and running the Quarkus Flow Runner.

## Scripts

### `build-image.sh`

Builds Docker images for the Flow Runner.

**Usage:**
```bash
./build-image.sh [variant]
```

**Variants:**
- `minimal` (default) - MVStore persistence, single replica
- `standard` - JPA + PostgreSQL + durable-kubernetes for HA
- `messaging` - JPA + PostgreSQL + Kafka + durable-kubernetes for HA

**Examples:**
```bash
# Build minimal variant
./build-image.sh minimal

# Build standard variant
./build-image.sh standard

# Build messaging variant with Kafka
./build-image.sh messaging
```

The script can be run from anywhere - it automatically finds the project root.

**Output:**
- Docker image: `quay.io/quarkiverse/quarkus-flow-runner:${VERSION}-${VARIANT}`
- Also tagged as: `quay.io/quarkiverse/quarkus-flow-runner:latest-${VARIANT}`

### `run-example.sh`

**Quick test script** for the minimal variant with example workflows.

**Usage:**
```bash
./run-example.sh
```

This will:
1. Start a container with the minimal image using `docker run`
2. Mount the `../workflows/` directory into `/deployments/workflows`
3. Wait for the application to be ready
4. Display usage instructions

**When to use:**
- ✅ Quick testing without docker-compose
- ✅ CI/CD image validation
- ✅ One-time demos

**For regular development, use docker-compose instead:**
```bash
# From runner/app directory
docker-compose up                                    # Minimal variant
docker-compose -f docker-compose-standard.yml up    # PostgreSQL variant
docker-compose -f docker-compose-messaging.yml up   # Kafka variant
```

See [DOCKER-COMPOSE.md](../DOCKER-COMPOSE.md) for complete docker-compose documentation.

**Example output:**
```
✅ Application is ready!
📋 Available workflows:
  - examples/hello-world/1.0.0

🧪 Test the workflow:
  curl -X POST http://localhost:8080/q/flow/exec/examples/hello-world/1.0.0 \
    -H "Content-Type: application/json" \
    -d '{"name": "World"}' | jq
```

**Stop the example:**
```bash
docker stop flow-runner-example && docker rm flow-runner-example
```

## Makefile

For convenience, use the Makefile in the parent directory (`runner/app/`).

See available targets with:
```bash
make help
```

Common commands:
```bash
# Build images
make build-minimal
make build-standard
make build-messaging
make build-all

# Run examples
make run-example
make stop-example

# Development
make dev              # Start Quarkus dev mode
make test             # Run tests
make clean            # Clean build artifacts

# Docker Compose
make docker-compose-up
make docker-compose-down

# Monitoring
make health           # Check health
make metrics          # Show metrics
make logs             # Follow logs

# Push to registry
make push-minimal
make push-standard
make push-messaging
make push-all
```
