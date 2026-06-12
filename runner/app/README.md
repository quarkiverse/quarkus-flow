# Quarkus Flow Runner - Docker Images

This module builds pre-configured Quarkus Flow Runner applications for Docker deployment. It is **not released as a Maven artifact** - its purpose is to generate Docker images.

## Pre-built Image Variants

Three image variants are planned:

### Minimal (~120MB) - ✅ WORKING
```bash
docker pull quay.io/quarkiverse/quarkus-flow-runner:latest-minimal
```
- **Persistence:** MVStore (file-based)
- **Messaging:** None (no messaging dependencies)
- **Status:** ✅ Ready to use
- **Best for:** Local development, testing, simple production deployments without event emission

### Standard (~280MB) - ⚠️ NOT YET AVAILABLE
```bash
docker pull quay.io/quarkiverse/quarkus-flow-runner:latest-standard
```
- **Planned Persistence:** JPA + MVStore + Redis (runtime-selected)
- **Planned Messaging:** In-memory
- **Status:** ⚠️ Blocked by persistence selection issue
- **Best for:** Production with flexible persistence options

### Messaging (~330MB) - ⚠️ NOT YET AVAILABLE
```bash
docker pull quay.io/quarkiverse/quarkus-flow-runner:latest-messaging
```
- **Planned Persistence:** JPA + MVStore + Redis (runtime-selected)
- **Planned Messaging:** Kafka
- **Status:** ⚠️ Blocked by persistence selection issue
- **Best for:** Event-driven workflows with flexible persistence

## ⚠️ Current Limitation: Multiple Persistence Modules Not Supported

**The standard and messaging variants are currently blocked** due to a persistence layer design limitation.

**Problem:** Each persistence module produces a `PersistenceInstanceHandlers` CDI bean. Having multiple persistence modules in the classpath causes CDI ambiguity at startup.

**Current workaround:** Only the `image-minimal` variant works (single persistence module: MVStore).

**Required fix:** Implement runtime persistence selection mechanism:
- Add a `@Typed` qualifier or alternative strategy to avoid CDI ambiguity
- Introduce `quarkus.flow.persistence.type` configuration property
- Implement conditional bean production based on config

**Issue to create:** Track this as a separate enhancement issue before implementing standard/messaging variants.

## Building Locally

### Build specific variant
```bash
# Minimal (MVStore + in-memory) - DEFAULT - ✅ WORKS
./mvnw clean package -pl runner/app -am -DskipTests

# Standard (All persistence + PostgreSQL) - ⚠️ BROKEN - DO NOT USE YET
./mvnw clean package -pl runner/app -am -P image-standard -DskipTests

# Messaging (All persistence + Kafka) - ⚠️ BROKEN - DO NOT USE YET
./mvnw clean package -pl runner/app -am -P image-messaging -DskipTests
```

### Create Docker image
```bash
# After Maven build, from runner/app directory:
docker build --build-arg VARIANT=minimal -t my-runner:jvm -f Dockerfile .
```

**Note:** Only build with `image-minimal` profile until the persistence selection issue is resolved.

## Customization Options

### Swap JDBC Driver (via Docker layering)
```dockerfile
FROM quay.io/quarkiverse/quarkus-flow-runner:1.0.0-standard
USER root
RUN rm /deployments/lib/postgresql-*.jar
COPY mysql-connector-j-*.jar /deployments/lib/
USER 185
```

### Custom Build with Extensions
For more complex customization (different persistence modules, additional Quarkus extensions):

```bash
docker build \
  --build-arg PERSISTENCE_EXTENSIONS="quarkus-flow-persistence-redis,quarkus-redis-client" \
  --build-arg JDBC_DRIVER="quarkus-jdbc-mysql" \
  --build-arg MESSAGING_EXTENSIONS="quarkus-messaging-kafka" \
  -t my-runner:custom \
  -f Dockerfile.extensible \
  https://github.com/quarkiverse/quarkus-flow.git#main:runner/app
```

See `Dockerfile.extensible` for details.

## Configuration

Key environment variables:

```bash
# HTTP
QUARKUS_HTTP_PORT=8080

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
