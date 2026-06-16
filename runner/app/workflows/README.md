# Workflow Files Directory

This directory contains workflow definition files that will be automatically loaded when the Quarkus Flow Runner starts.

## Quick Start

1. **Add your workflow YAML files** to this directory:
   ```bash
   cp my-workflow.yaml workflows/
   ```

2. **Start the runner** using docker-compose:
   ```bash
   # From runner/app directory
   docker-compose up
   ```

3. **Verify workflows are loaded**:
   ```bash
   curl http://localhost:8080/q/flow/definitions | jq
   ```

## Example Workflow

See `example-workflow.yaml` for a simple hello-world workflow with input schema validation.

**Test it:**
```bash
# With object input
curl -X POST http://localhost:8080/q/flow/exec/examples/hello-world/1.0.0 \
  -H "Content-Type: application/json" \
  -d '{"name": "Alice"}' | jq

# With scalar input (number, string, boolean)
curl -X POST http://localhost:8080/q/flow/exec/examples/hello-world/1.0.0 \
  -H "Content-Type: application/json" \
  -d '"Alice"' | jq
```

## Workflow Format

Workflows must follow the [CNCF Serverless Workflow DSL 1.0.0](https://serverlessworkflow.io/) specification.

**Minimal example:**
```yaml
document:
  dsl: '1.0.0'
  namespace: my-namespace
  name: my-workflow
  version: 1.0.0
do:
  - myTask:
      set:
        result: ${ "Hello World" }
```

## Loading Workflows

### Option 1: Docker Compose (Recommended)

The docker-compose files automatically mount this directory:

```bash
# Minimal variant (default workflows directory)
docker-compose up

# Standard variant (PostgreSQL)
docker-compose -f docker-compose-standard.yml up

# Messaging variant (PostgreSQL + Kafka)
docker-compose -f docker-compose-messaging.yml up

# Use custom workflows directory
WORKFLOWS=/path/to/my/workflows docker-compose up
make docker-compose-up WORKFLOWS=/path/to/my/workflows
```

### Option 2: Manual Docker Run

```bash
docker run -p 8080:8080 \
  -v $(pwd)/workflows:/deployments/workflows:ro \
  quay.io/quarkiverse/quarkus-flow-runner:latest-minimal
```

### Option 3: Development Mode

When running in Quarkus dev mode, workflows are loaded from the project directory:

```bash
# From project root
make dev  # Loads workflows from runner/app/workflows

# Or from runner/app
cd runner/app
../../mvnw quarkus:dev
```

The dev mode configuration uses `${user.dir}/workflows` which resolves to the `workflows/` directory in the project root.

### Option 4: Run Example Script

```bash
./scripts/run-example.sh
```

## Hot Reload

**Note:** Workflow files are loaded on startup. To reload workflows:

```bash
# Restart the container
docker-compose restart runner

# Or stop and start
docker-compose down
docker-compose up
```

## File Naming

- Files must have `.yaml` or `.yml` extension
- File names don't matter - the workflow is identified by `namespace/name/version` in the YAML
- You can organize workflows in subdirectories if needed

## Workflow Input

Workflows can accept any JSON type as input:
- **Objects**: `{"name": "Alice", "age": 30}`
- **Arrays**: `[1, 2, 3]`
- **Strings**: `"hello"`
- **Numbers**: `123` or `45.67`
- **Booleans**: `true` or `false`

**Example workflows:**

```yaml
# Workflow accepting an object
do:
  - greet:
      set:
        message: ${ "Hello " + .name }

# Workflow accepting a number (petId)
do:
  - getPet:
      call: http
      with:
        uri: ${ "https://petstore.swagger.io/v2/pet/" + . }
        method: GET

# Workflow accepting a string
do:
  - process:
      set:
        result: ${ "Processing: " + . }
```

### Input Schema Validation

Define input schemas to validate workflow execution requests:

```yaml
input:
  schema:
    document:
      type: object
      required:
        - userId
      properties:
        userId:
          type: string
          description: User identifier
        options:
          type: object
```

This will:
- Generate proper OpenAPI documentation in Swagger UI
- Validate inputs at runtime
- Provide type hints in the API

**Note:** Input validation works for all JSON types (objects, arrays, scalars).

## See Also

- [DOCKER-COMPOSE.md](../DOCKER-COMPOSE.md) - Docker Compose deployment guide
- [README.md](../README.md) - Runner documentation
- [Quarkus Flow Documentation](https://docs.quarkiverse.io/quarkus-flow/dev/)
- [CNCF Serverless Workflow Spec](https://serverlessworkflow.io/)
