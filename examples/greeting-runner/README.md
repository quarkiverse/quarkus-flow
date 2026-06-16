# Greeting Runner Example

**A fully functional workflow microservice without writing a single line of Java code.**

This example demonstrates the "no-code" approach to deploying workflow-based microservices using the Quarkus Flow Runner extension. The entire service is defined through configuration and YAML workflow definitions.

## What This Demonstrates

- **Zero Java code required** - Workflow logic defined entirely in YAML
- Loading workflow definitions from filesystem at startup
- Executing workflows via REST API
- API key authentication
- OpenAPI/Swagger UI integration

## Project Structure

```
greeting-runner/
├── pom.xml
├── src/main/resources/
│   ├── application.properties
│   └── workflows/
│       └── greeting.sw.yaml
└── README.md
```

## Running the Example

### 1. Start the application

```bash
./mvnw quarkus:dev
```

The application will:
- Load `greeting.sw.yaml` workflow from `src/main/resources/workflows/`
- Start REST API on http://localhost:8080
- Expose Swagger UI at http://localhost:8080/q/swagger-ui

### 2. View the workflow catalog

Open http://localhost:8080/q/swagger-ui to see:
- The workflow execution endpoint
- Request/response schemas
- Interactive testing interface

### 3. Execute the workflow

```bash
curl -X POST http://localhost:8080/q/flow/exec/examples/greeting/1.0.0?wait=true \
  -H "Authorization: Bearer demo-secret-change-me" \
  -H "Content-Type: application/json" \
  -d '{"name": "World"}'
```

**Response:**

```json
{
  "instanceId": "550e8400-e29b-41d4-a716-446655440000",
  "namespace": "examples",
  "name": "greeting",
  "version": "1.0.0",
  "status": "COMPLETED",
  "workflowOutput": {
    "message": "Hello, World!"
  }
}
```

### 4. Execute without version (uses latest)

```bash
curl -X POST http://localhost:8080/q/flow/exec/examples/greeting?wait=true \
  -H "Authorization: Bearer demo-secret-change-me" \
  -H "Content-Type: application/json" \
  -d '{"name": "Runner"}'
```

## Configuration

### API Key

The example uses API key authentication with a demo key. **Change this for production:**

```bash
export FLOW_API_KEY=your-secure-key
./mvnw quarkus:dev
```

### Workflow Path

Workflows are loaded from:
```properties
quarkus.flow.runner.source.path=src/main/resources/workflows
```

In production (Kubernetes), mount workflows from ConfigMap:
```properties
quarkus.flow.runner.source.path=/deployments/workflows
```

## Key Takeaways

- ✅ **Zero Java code** - Complete microservice with only YAML + properties files
- ✅ **Runtime deployment** - Add/update workflows without rebuilding
- ✅ **REST API** - Execute workflows via HTTP
- ✅ **OpenAPI** - Auto-generated API documentation
- ✅ **Secure** - API key authentication included

This approach is ideal for:
- Rapid prototyping and development
- GitOps-based workflow deployment
- Non-developer workflow authoring (business analysts, DevOps teams)
- Multi-tenant workflow hosting

## Next Steps

1. **Add more workflows** - Drop `.yaml` files in `src/main/resources/workflows/`
2. **Try different tasks** - HTTP calls, conditionals, loops
3. **Deploy to Kubernetes** - Use ConfigMaps for workflow definitions
4. **Switch to OIDC** - For enterprise SSO integration

## Learn More

- [Quarkus Flow Runner Documentation](https://docs.quarkiverse.io/quarkus-flow/dev/runner.html)
- [CNCF Serverless Workflow Specification](https://serverlessworkflow.io/)
