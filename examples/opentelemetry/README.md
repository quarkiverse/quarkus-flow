# OpenTelemetry showcase

This example demonstrates how to add OpenTelemetry instrumentation to your workflows.

## What This Demonstrates

- **How to enable OpenTelemetry** by only adding the `quarkus-flow-opentelemetry` extension to your project.

- Visualization of the OpenTelemetry information produced by the workflows.

## Prerequisites

- JDK 17 or later
- Maven
- Docker compose (required for executing Jaeger locally)

> **Note:**  
> If docker compose is not available in your installation, you can still execute the example and configure the otel exporter to access any collector of you choice.
---

## Project Structure

```
opentelemetry/
├── pom.xml
├── docker-compose/
│   ├── docker-compose.yaml
├── src/main/resources/
│   ├── application.properties
│   └── workflows/
│       └── otel-showcase.yaml
└── README.md
```

## OpenTelemetry Configuration

To enable OpenTelemetry, you must add the `quarkus-flow-opentelemetry` extension to your project, see [pom.xml](pom.xml).
For more information on that extension see the [Quarkus Flow OpenTelemetry Documentation](https://docs.quarkiverse.io/quarkus-flow/dev/opentelemetry.html)


## Running the Example

### 1. Start Jaeger

In a terminal window, run:

```bash
cd docker-compose
docker compose up
```

Once Jaeger is ready, you will see an output like:

```
jaeger  | 2026-08-04T10:12:30.531Z      info    service@v0.112.0/service.go:230 Everything is ready. Begin running and processing data.
```

Open the Jaeger UI by using the following url:  http://localhost:16686

When Jaeger starts and no workflows have been executed yet, only the `jaeger` service will appear in the service list. Once you start executing workflows, refresh the page to see the `opentelemetry` service.

![Jaeger Service Selector](images/service-selector.png)

### 2. Start the application

In a new terminal window, run:

```bash
./mvnw quarkus:dev
```

The application will:
- Load the example workflow from `src/main/resources/workflows/`
- Start REST API on http://localhost:8080
- Expose Swagger UI at http://localhost:8080/q/swagger-ui
- Begin exporting workflow OpenTelemetry tracing data to Jaeger 

### 3. Execute the workflow

Before executing requests, consider reviewing the workflow definition [otel-showcase.yaml](src/main/resources/workflows/otel-showcase.yaml) to better understand the resulting traces.

#### 3.1 Execute the `for` case

In a terminal window, run:

```bash
curl -X 'POST' \
'http://localhost:8080/q/flow/exec/otel/otel-showcase/1.0.0?wait=true' \
-H 'Authorization: Bearer demo-secret-change-me' \
-H 'accept: application/json' \
-H 'Content-Type: application/json' \
-d '{"selectedCase" : "for"}'
```

After executing, return to the Jaeger UI and click **Find Traces**. You will see the new execution trace:

![Find Traces For Case](images/find-traces1.png)

Click on the trace to inspect the details. You will see an output like this:

![For Case Trace](images/for-case-trace.png)

#### 3.2 Execute the `fork` case

In a terminal window, run:

```bash
curl -X 'POST' \
'http://localhost:8080/q/flow/exec/otel/otel-showcase/1.0.0?wait=true' \
-H 'Authorization: Bearer demo-secret-change-me' \
-H 'accept: application/json' \
-H 'Content-Type: application/json' \
-d '{"selectedCase" : "fork"}'
```

After executing, return to the Jaeger UI and click **Find Traces**. You will see the new execution trace:

![Find Traces Fork Case](images/find-traces2.png)

Click on the trace to inspect the details. You will see an output like this:

![Fork Case Trace](images/fork-case-trace.png)

#### 3.3 Execute the `try` case

In a terminal window, run:

```bash
curl -X 'POST' \
'http://localhost:8080/q/flow/exec/otel/otel-showcase/1.0.0?wait=true' \
-H 'Authorization: Bearer demo-secret-change-me' \
-H 'accept: application/json' \
-H 'Content-Type: application/json' \
-d '{"selectedCase" : "try"}'
```

After executing, return to the Jaeger UI and click **Find Traces**. You will see the new execution trace:

![Find Traces Try Case](images/find-traces3.png)

Click on the trace to inspect the details. You will see an output like this:

![Try Case Trace](images/try-case-trace.png)

#### 3.4 Execute the `unknown` case

```bash
curl -X 'POST' \
'http://localhost:8080/q/flow/exec/otel/otel-showcase/1.0.0?wait=true' \
-H 'Authorization: Bearer demo-secret-change-me' \
-H 'accept: application/json' \
-H 'Content-Type: application/json' \
-d '{}'
```

After executing, return to the Jaeger UI and click **Find Traces**. You will see the new execution trace:

![Find Traces Unknown Case](images/find-traces4.png)

Click on the trace to inspect the details. You will see an output like this:

![Unknown Case Trace](images/unknown-case-trace.png)

### 4. Stop Jaeger

When you are finished, stop the Jaeger container:
```bash
cd docker-compose
docker compose down
```
After stopping, you'll see an output like this:
```
 ✔ Container jaeger               Removed                                                                                                                                                                                        0.9s 
 ⠼ Network docker-compose_default Removing 
```

## Key Takeaways

- ✅ **Seamless Integration** — Simply adding `quarkus-flow-opentelemetry` automatically instruments workflow executions without boilerplate code
- ✅ **Consistent Runtime Support** — Works identically whether you are using `quarkus-flow` or `quarkus-flow-runner`.

## Next Steps

1. **Add more workflows** - Drop `.yaml` files in `src/main/resources/workflows/`
2. **Try other tasks**

## Learn More

- [Quarkus Flow OpenTelemetry Documentation](https://docs.quarkiverse.io/quarkus-flow/dev/opentelemetry.html)
- [Open Workflow Specification](https://open-workflow-specification.org)
