# Docker Compose Deployment Guide

This directory contains Docker Compose configurations for all three Quarkus Flow Runner variants.

## Available Variants

### 1. Minimal (docker-compose.yml)

**File:** `docker-compose.yml`

**Components:**
- Quarkus Flow Runner (minimal variant)
- MVStore persistence (file-based)

**Use Cases:**
- Local development and testing
- Single-replica deployments
- Non-HA scenarios

**Start:**
```bash
docker-compose up -d
# or
make docker-compose-up
```

**Stop:**
```bash
docker-compose down
# or
make docker-compose-down
```

**Limitations:**
- ❌ No High Availability
- ❌ Single replica only
- ❌ No failover support

---

### 2. Standard (docker-compose-standard.yml)

**File:** `docker-compose-standard.yml`

**Components:**
- Quarkus Flow Runner (standard variant)
- PostgreSQL 17 (Alpine - lightweight)
- JPA persistence with durable-kubernetes support

**Use Cases:**
- Production deployments requiring HA
- Multi-replica scenarios
- Shared database persistence

**Start:**
```bash
docker-compose -f docker-compose-standard.yml up -d
# or
make docker-compose-up-standard
```

**Stop:**
```bash
docker-compose -f docker-compose-standard.yml down
# or
make docker-compose-down-standard
```

**Features:**
- ✅ High Availability support
- ✅ Shared database persistence
- ✅ Automatic failover with durable-kubernetes
- ✅ Multi-replica ready

**Database Configuration:**
- Host: `postgres` (internal) / `localhost:5432` (external)
- Database: `flowdb`
- User: `flowuser`
- Password: `flowpass`

---

### 3. Messaging (docker-compose-messaging.yml)

**File:** `docker-compose-messaging.yml`

**Components:**
- Quarkus Flow Runner (messaging variant)
- PostgreSQL 17 (Alpine - lightweight)
- Apache Kafka (KRaft mode - no Zookeeper)
- JPA persistence with durable-kubernetes support

**Use Cases:**
- Event-driven workflows
- Kafka integration
- Production HA deployments with messaging

**Start:**
```bash
docker-compose -f docker-compose-messaging.yml up -d
# or
make docker-compose-up-messaging
```

**Stop:**
```bash
docker-compose -f docker-compose-messaging.yml down
# or
make docker-compose-down-messaging
```

**Features:**
- ✅ High Availability support
- ✅ Shared database persistence
- ✅ Kafka messaging integration
- ✅ Automatic failover with durable-kubernetes
- ✅ Multi-replica ready

**Database Configuration:**
Same as Standard variant

**Kafka Configuration:**
- Bootstrap servers: `kafka:9092` (internal) / `localhost:9092` (external)
- KRaft mode (no Zookeeper required)
- 3 partitions by default

---

## Quick Start Examples

### Minimal Variant (Local Development)
```bash
# Start the runner
make docker-compose-up

# Access the dashboard
open http://localhost:8080

# View logs
docker-compose logs -f

# Stop
make docker-compose-down
```

### Standard Variant (Production-like with PostgreSQL)
```bash
# Start PostgreSQL + Runner
make docker-compose-up-standard

# Check services are healthy
docker-compose -f docker-compose-standard.yml ps

# Access the dashboard
open http://localhost:8080

# Connect to PostgreSQL (optional)
psql -h localhost -U flowuser -d flowdb

# View logs
make docker-compose-logs-standard

# Stop
make docker-compose-down-standard
```

### Messaging Variant (Event-driven with Kafka)
```bash
# Start PostgreSQL + Kafka + Runner
make docker-compose-up-messaging

# Wait for all services to be healthy (Kafka takes ~30s)
docker-compose -f docker-compose-messaging.yml ps

# Access the dashboard
open http://localhost:8080

# View logs
make docker-compose-logs-messaging

# Stop
make docker-compose-down-messaging
```

## Service Endpoints

Once started, all variants expose:

- **Dashboard:** http://localhost:8080
- **API Documentation:** http://localhost:8080/q/swagger-ui
- **OpenAPI Spec:** http://localhost:8080/q/openapi
- **Health Check:** http://localhost:8080/q/health
- **Metrics:** http://localhost:8080/q/metrics

### Additional Endpoints (Standard & Messaging)

- **PostgreSQL:** `localhost:5432`
  - Database: `flowdb`
  - User: `flowuser` / Password: `flowpass`

### Additional Endpoints (Messaging Only)

- **Kafka:** `localhost:9092`

## Mounting Workflow Files

All variants automatically mount workflow files from the host.

### Default Directory (./workflows)

By default, the `./workflows/` directory is mounted:

1. Add your workflow YAML files:
   ```bash
   cp my-workflow.yaml ./workflows/
   ```

2. Start the services:
   ```bash
   docker-compose up -d
   # or
   make docker-compose-up
   ```

**Example workflow** is already provided in `./workflows/example-workflow.yaml` - test it:
```bash
curl -X POST http://localhost:8080/q/flow/exec/examples/hello-world/1.0.0 \
  -H "Content-Type: application/json" \
  -d '{"name": "Alice"}' | jq
```

### Custom Workflow Directory

Use the `WORKFLOWS` environment variable to mount a different directory:

**With Makefile:**
```bash
make docker-compose-up WORKFLOWS=/path/to/my/workflows
make docker-compose-up-standard WORKFLOWS=/path/to/my/workflows
make docker-compose-up-messaging WORKFLOWS=/path/to/my/workflows
```

**With docker-compose directly:**
```bash
WORKFLOWS=/path/to/my/workflows docker-compose up -d
WORKFLOWS=/path/to/my/workflows docker-compose -f docker-compose-standard.yml up -d
WORKFLOWS=/path/to/my/workflows docker-compose -f docker-compose-messaging.yml up -d
```

**Examples:**
```bash
# Mount workflows from a different project
make docker-compose-up WORKFLOWS=~/projects/my-app/workflows

# Mount workflows from temporary directory
make docker-compose-up-standard WORKFLOWS=/tmp/test-workflows

# Use absolute path
WORKFLOWS=/var/lib/workflows docker-compose up
```

See `workflows/README.md` for more details on creating workflows.

## Environment Variables

### Standard & Messaging Variants

Override database/Kafka settings via environment variables:

```yaml
environment:
  # Database
  QUARKUS_DATASOURCE_JDBC_URL: jdbc:postgresql://postgres:5432/flowdb
  QUARKUS_DATASOURCE_USERNAME: flowuser
  QUARKUS_DATASOURCE_PASSWORD: flowpass
  
  # Kafka (messaging variant only)
  KAFKA_BOOTSTRAP_SERVERS: kafka:9092
```

## Persistence & Data Volumes

### Minimal Variant
- Volume: `flow-data` → `/deployments/data`
- Contains: MVStore database file

### Standard & Messaging Variants
- Volume: `postgres-data` → `/var/lib/postgresql/data`
- Contains: PostgreSQL data directory

**Cleanup volumes:**
```bash
docker-compose down -v  # Removes volumes (data will be lost!)
```

## Health Checks

All services include health checks:

- **Runner:** Queries `/q/health/ready`
- **PostgreSQL:** Uses `pg_isready`
- **Kafka:** Uses `kafka-broker-api-versions.sh`

Check service health:
```bash
docker-compose ps
# or
docker-compose -f docker-compose-standard.yml ps
# or
docker-compose -f docker-compose-messaging.yml ps
```

## Networking

All variants use a dedicated network (`flow-network`) for service discovery.

**Service Resolution:**
- Minimal: `runner` only
- Standard: `postgres` → `runner`
- Messaging: `postgres` + `kafka` → `runner`

## Troubleshooting

### Runner fails to start (Standard/Messaging)
```bash
# Check if PostgreSQL is healthy
docker-compose -f docker-compose-standard.yml ps postgres

# View PostgreSQL logs
docker-compose -f docker-compose-standard.yml logs postgres

# Check runner logs
docker-compose -f docker-compose-standard.yml logs runner
```

### Kafka takes long to start (Messaging)
This is normal. Kafka in KRaft mode takes 30-40 seconds to initialize. The runner waits for Kafka to be healthy before starting.

```bash
# Watch services starting up
docker-compose -f docker-compose-messaging.yml ps

# View Kafka logs
docker-compose -f docker-compose-messaging.yml logs kafka
```

### Port conflicts
If port 8080, 5432, or 9092 is already in use:

```yaml
ports:
  - "9080:8080"  # Change host port
```

### Database connection issues
Verify PostgreSQL is accepting connections:
```bash
docker exec -it flow-postgres pg_isready -U flowuser
```

### Reset everything
```bash
# Stop all services and remove volumes
docker-compose down -v
docker-compose -f docker-compose-standard.yml down -v
docker-compose -f docker-compose-messaging.yml down -v

# Remove all flow-related containers and volumes
docker ps -a | grep flow | awk '{print $1}' | xargs docker rm -f
docker volume ls | grep flow | awk '{print $2}' | xargs docker volume rm
```

## Production Deployment

For production deployments:

1. **Use Standard or Messaging variants** (not minimal)
2. **Secure credentials:** Use secrets management instead of hardcoded passwords
3. **External database:** Point to managed PostgreSQL (RDS, Cloud SQL, etc.)
4. **External Kafka:** Use managed Kafka (MSK, Confluent Cloud, etc.)
5. **Resource limits:** Add resource constraints:
   ```yaml
   deploy:
     resources:
       limits:
         cpus: '2'
         memory: 2G
   ```
6. **Monitoring:** Scrape metrics endpoint with Prometheus
7. **Backup:** Regular PostgreSQL backups

## See Also

- [Runner README](README.md) - Image variants and architecture
- [Build Scripts](scripts/README.md) - Building custom images
- [Makefile](Makefile) - All available make targets
