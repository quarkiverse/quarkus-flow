# Quarkus Flow Runner - Docker Images

This module builds pre-configured Quarkus Flow Runner applications for Docker deployment. It is **not released as a Maven artifact** - its purpose is to generate Docker images.

## Pre-built Image Variants

Three image variants are planned:

### Minimal (~120MB) - ✅ WORKING
```bash
docker pull quay.io/quarkiverse/quarkus-flow-runner:latest-minimal
```
- **Persistence:** MVStore (file-based, local storage)
- **Messaging:** None (no messaging dependencies)
- **HA/Failover:** ❌ Not supported (file-based persistence)
- **Durable Kubernetes:** ❌ Not included (requires shared persistence)
- **Status:** ✅ Ready to use
- **Best for:** 
  - Local development and testing
  - Single-replica deployments
  - Non-HA scenarios where pod restart is acceptable
  - Edge deployments with local storage

⚠️ **Limitations:**
- **No High Availability:** MVStore is file-based and cannot be shared across pods
- **No Failover:** If the pod dies, workflow instances are unavailable until restart
- **Single Replica Only:** Do not scale beyond 1 replica (file lock conflicts)
- **For Production HA:** Use `image-standard` or `image-messaging` with JPA/Redis + durable-kubernetes

### Standard (~280MB) - ✅ WORKING
```bash
docker pull quay.io/quarkiverse/quarkus-flow-runner:latest-standard
```
- **Persistence:** JPA with PostgreSQL
- **Messaging:** None (use messaging variant for Kafka)
- **HA/Failover:** ✅ Supported (with JPA + durable-kubernetes)
- **Durable Kubernetes:** ✅ Included
- **Status:** ✅ Ready to use
- **Best for:** Production deployments with high availability and shared database persistence

### Messaging (~330MB) - ✅ WORKING
```bash
docker pull quay.io/quarkiverse/quarkus-flow-runner:latest-messaging
```
- **Persistence:** JPA with PostgreSQL
- **Messaging:** Kafka (CloudEvents via SmallRye Reactive Messaging)
- **HA/Failover:** ✅ Supported (with JPA + durable-kubernetes)
- **Durable Kubernetes:** ✅ Included
- **Status:** ✅ Ready to use (requires messaging config at runtime, see #645)
- **Best for:** Event-driven workflows with high availability and Kafka integration

## High Availability & Failover Architecture

### How Durable Kubernetes Enables HA

The **durable-kubernetes** module (included in standard/messaging variants) enables production HA through:

1. **Shared Persistence** (JPA/Redis) - All pods access the same database
2. **Lease-based Coordination** - Each pod acquires a stable lease name (e.g., `flow-pool-member-00`)
3. **Workflow Sharding** - Workflows are sharded by `WorkflowApplication.id` (= lease name)
4. **Automatic Failover** - Pod dies → another pod acquires the lease → continues processing those workflows

**Example:**
```
Runner Pod A: lease=flow-pool-member-00 → processes workflows with app.id=flow-pool-member-00
Runner Pod B: lease=flow-pool-member-01 → processes workflows with app.id=flow-pool-member-01
Runner Pod C: lease=flow-pool-member-02 → processes workflows with app.id=flow-pool-member-02

Pod A crashes → Pod D acquires flow-pool-member-00 → resumes Pod A's workflows
```

### Why MVStore Doesn't Support HA

MVStore is **file-based** and fundamentally incompatible with this model:
- ❌ **Shared storage (ReadWriteMany PVC):** File lock conflicts between pods
- ❌ **Per-pod storage (StatefulSet):** No shared state, pods are isolated
- ❌ **No failover:** If pod dies, its workflows are stuck on its local file

**Production HA requires:**
- Shared persistence: JPA (PostgreSQL/MySQL) or Redis
- durable-kubernetes module for lease coordination
- Regular Kubernetes Deployment (not StatefulSet)

### Deployment Patterns

| Use Case | Image Variant | Persistence | Replicas | K8s Resource | HA |
|----------|---------------|-------------|----------|--------------|-----|
| Dev/Test | minimal | MVStore | 1 | Deployment | ❌ |
| Edge/IoT | minimal | MVStore | 1 | Deployment | ❌ |
| Production | standard/messaging | JPA/Redis | 3+ | Deployment | ✅ |
| Production (single node) | minimal | MVStore | 1 | Deployment | ❌ |

## Persistence Architecture

Each image variant includes a **single persistence module** to avoid CDI ambiguity:

| Variant | Persistence | Why |
|---------|-------------|-----|
| minimal | MVStore | File-based, no external dependencies |
| standard | JPA | Shared database for multi-replica HA |
| messaging | JPA | Shared database for multi-replica HA |

**Design Decision:** Each persistence module produces a `PersistenceInstanceHandlers` CDI bean. Including multiple persistence modules causes CDI ambiguity. We chose simplicity: **one persistence type per variant**.

**Need different persistence?**
- **Redis:** Build custom image with `quarkus-flow-redis` instead of JPA
- **Different JDBC driver:** Swap via Docker layering (see Customization section)

## Building Locally

### Quick Start with Makefile

The easiest way to build and run:

```bash
# Show all available targets
make help

# Build minimal variant (default)
make build-minimal

# Build all variants
make build-all

# Run example workflow
make run-example

# Start in development mode
make dev
```

See the [Makefile](Makefile) or run `make help` for all available targets.

### Using Build Scripts

Alternatively, use the build scripts directly:

```bash
cd runner/app

# Build minimal variant
./scripts/build-image.sh minimal

# Build standard variant
./scripts/build-image.sh standard

# Build messaging variant
./scripts/build-image.sh messaging

# Run example
./scripts/run-example.sh
```

### Manual Build

If you prefer full control:

```bash
# Step 1: Build with Maven (specify profile)
./mvnw clean package -pl runner/app -am -P image-minimal -DskipTests -Dquarkus.profile="container,minimal"

# Step 2: Build Docker image
cd runner/app
docker build --build-arg VARIANT=minimal -f Dockerfile -t quarkus-flow-runner:minimal .
```

### Test the Image

```bash
# Option 1: Make target (recommended)
make docker-compose-up                # Minimal variant (default workflows)
make docker-compose-up-standard       # Standard variant with PostgreSQL
make docker-compose-up-messaging      # Messaging variant with Kafka

# Option 2: Make target with custom workflows directory
make docker-compose-up WORKFLOWS=/path/to/my/workflows

# Option 3: Docker Compose directly
docker-compose up                                    # Minimal variant
docker-compose -f docker-compose-standard.yml up    # Standard variant
docker-compose -f docker-compose-messaging.yml up   # Messaging variant

# Option 4: Docker Compose with custom workflows
WORKFLOWS=/path/to/my/workflows docker-compose up

# Option 5: Quick test script
make run-example

# Verify it's running
curl http://localhost:8080/q/health/ready
curl http://localhost:8080              # Dashboard with live metrics
```

**Dashboard Features:**
- 📊 Real-time workflow execution metrics
- 📈 Live charts tracking workflow started/completed counts
- 📋 Workflow table showing Started, Completed, Running, Waiting, Suspended states
- ⏱️ Average duration per workflow
- 🔄 Auto-refreshes every 5 seconds

**See [DOCKER-COMPOSE.md](DOCKER-COMPOSE.md) for complete docker-compose documentation.**

### Build Other Variants

```bash
# Standard variant (JPA + PostgreSQL + durable-kubernetes) - ✅ WORKS
./build-image.sh standard

# Messaging variant (JPA + PostgreSQL + Kafka + durable-kubernetes) - ✅ WORKS
./build-image.sh messaging
```

Choose based on your needs:
- **minimal**: Single replica, file-based storage, no HA
- **standard**: Multi-replica HA with shared database
- **messaging**: Multi-replica HA with shared database + Kafka events

## Customization Options

### Swap JDBC Driver (via Docker layering)

If you need a different database driver (e.g., MySQL instead of PostgreSQL):

```dockerfile
FROM quay.io/quarkiverse/quarkus-flow-runner:1.0.0-standard
USER root
RUN rm /deployments/lib/postgresql-*.jar
COPY mysql-connector-j-*.jar /deployments/lib/
USER 185
```

### Custom Build with Different Extensions

For more complex customization (different persistence modules, additional Quarkus extensions), you'll need to build from source:

1. Clone the repository
2. Modify `runner/app/pom.xml` to add/remove dependencies
3. Build with your custom profile:
   ```bash
   ./mvnw clean package -pl runner/app -am -P image-standard -DskipTests
   cd runner/app
   docker build -t my-custom-runner:1.0.0 .
   ```

## Configuration

Key environment variables:

```bash
# HTTP
QUARKUS_HTTP_PORT=8080

# Workflow Loading
QUARKUS_FLOW_RUNNER_SOURCE_PATH=/deployments/workflows  # Path to workflow definition files

# Persistence - PostgreSQL
QUARKUS_DATASOURCE_DB_KIND=postgresql
QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://localhost:5432/quarkus_flow
QUARKUS_DATASOURCE_USERNAME=quarkus_flow
QUARKUS_DATASOURCE_PASSWORD=quarkus_flow

# Persistence - Redis
QUARKUS_REDIS_HOSTS=redis://localhost:6379

# Persistence - MVStore (default)
QUARKUS_FLOW_PERSISTENCE_MVSTORE_FILE_PATH=/data/flow-instances.mv

# Messaging - Kafka
MP_MESSAGING_INCOMING_FLOW_IN_CONNECTOR=smallrye-kafka
MP_MESSAGING_INCOMING_FLOW_IN_TOPIC=flow-workflows
MP_MESSAGING_OUTGOING_FLOW_OUT_CONNECTOR=smallrye-kafka
MP_MESSAGING_OUTGOING_FLOW_OUT_TOPIC=flow-results
KAFKA_BOOTSTRAP_SERVERS=localhost:9092

# Logging
QUARKUS_LOG_LEVEL=INFO
QUARKUS_LOG_CATEGORY_IO_QUARKIVERSE_FLOW_LEVEL=DEBUG
```

## Deployment Examples

### Docker Compose with PostgreSQL
```yaml
version: '3.8'
services:
  db:
    image: postgres:17-alpine
    environment:
      POSTGRES_DB: quarkus_flow
      POSTGRES_USER: quarkus_flow
      POSTGRES_PASSWORD: quarkus_flow
    volumes:
      - pgdata:/var/lib/postgresql/data

  runner:
    image: quay.io/quarkiverse/quarkus-flow-runner:1.0.0-standard
    ports:
      - "8080:8080"
    environment:
      QUARKUS_DATASOURCE_JDBC_URL: jdbc:postgresql://db:5432/quarkus_flow
      QUARKUS_DATASOURCE_USERNAME: quarkus_flow
      QUARKUS_DATASOURCE_PASSWORD: quarkus_flow
    depends_on:
      - db

volumes:
  pgdata:
```

### Kubernetes Deployment
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: quarkus-flow-runner
spec:
  replicas: 3
  selector:
    matchLabels:
      app: flow-runner
  template:
    metadata:
      labels:
        app: flow-runner
    spec:
      containers:
      - name: runner
        image: quay.io/quarkiverse/quarkus-flow-runner:1.0.0-standard
        ports:
        - containerPort: 8080
        env:
        - name: QUARKUS_DATASOURCE_JDBC_URL
          value: jdbc:postgresql://postgres:5432/quarkus_flow
        - name: QUARKUS_DATASOURCE_USERNAME
          valueFrom:
            secretKeyRef:
              name: db-credentials
              key: username
        - name: QUARKUS_DATASOURCE_PASSWORD
          valueFrom:
            secretKeyRef:
              name: db-credentials
              key: password
```

## Testing

```bash
# Run tests with default profile (standard)
./mvnw test -pl runner/app

# Run tests with specific variant
./mvnw test -pl runner/app -P image-minimal
```

## CI/CD

This module is built via GitHub Actions to produce Docker images. It is **not deployed to Maven Central**.

See `.github/workflows/publish-runner-images.yml` for the build pipeline.

## Architecture

- **PersistenceSelector**: CDI producer that selects persistence implementation at runtime
- **Profiles**: Maven profiles (`image-minimal`, `image-standard`, `image-messaging`) control dependencies
- **Multi-stage Dockerfiles**: Minimize image size and enable custom builds

## Related Documentation

- Runner REST API: https://docs.quarkiverse.io/quarkus-flow/dev/runner.html
- Persistence: https://docs.quarkiverse.io/quarkus-flow/dev/persistence.html
- Messaging: https://docs.quarkiverse.io/quarkus-flow/dev/messaging.html
