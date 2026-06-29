# Examples

Directory of end-to-end use case examples.

## 🚀 Using These Examples

Each example is a **standalone Quarkus application** that can be copied and run independently.

### For End Users (Recommended)

**Use examples from tagged releases, not the main branch:**

```bash
# Clone a specific release tag
git clone -b 0.11.0 https://github.com/quarkiverse/quarkus-flow.git
cd quarkus-flow/examples/<example-name>

# Or download just the example directory from GitHub releases
# https://github.com/quarkiverse/quarkus-flow/releases
```

**Then run the example:**

```bash
./mvnw quarkus:dev
```

> **⚠️ Important:** Examples on the `main` branch use `SNAPSHOT` versions and require building the entire project first. For a better experience, always use examples from a tagged release.

### For Contributors

If you're working from the main branch during development:

```bash
# Build the entire project first to install SNAPSHOTs to local Maven repo
./mvnw clean install -DskipTests

# Then run any example
cd examples/<example-name>
./mvnw quarkus:dev
```

<!-- Please update this list when adding a new example / keep it in alphabetical order -->
- [Agentic + HTTP](agentic-http/README.md): Example of a workflow enriching an agent prompt from a remote HTTP request.
- [Durable Workflows on Kubernetes](durable-workflows-k8s/README.md): Example of leasing acquiring when deploying workflows on Kubernetes to enable durable workflows use cases.
- [Greeting Runner](greeting-runner/README.md): **Zero Java code** - A fully functional workflow microservice using only YAML definitions and the Runner extension. Demonstrates runtime workflow deployment via REST API.
- [HTTP Basic Auth](http-basic-auth/README.md): Simple workflow exemplifying calling an HTTP service secured by Basic Auth.
- [LangChain4j Agentic Workflow](langchain4j-agentic-workflow/README.md): Example of agentic workflows declared with LangChain4j annotations and executed as Quarkus Flow workflows with Dev UI support.
- [gRPC Client Routing](grpc-client-routing/README.md): Example demonstrating **Quarkus named gRPC client routing** for workflows with per-workflow and per-task channel overrides.
- [Micrometer Prometheus](micrometer-prometheus/README.md): Example showing case how to configure, export, and visualize **workflow and task metrics**.
- [Newsletter Drafter](newsletter-drafter/README.md): Human-in-the-loop Agentic Workflow example with LangChain4j.
- [Petstore OpenAPI](petstore-openapi/README.md): The famous Petstore Demo calling HTTP services via an OpenAPI specification file descriptor.
- [Resilient Task Orchestrator](resilient-task-orchestrator/README.md): Production-ready example demonstrating **event-driven task orchestration** with fault isolation, automatic retry, idempotent execution, and state reconciliation for safe resume after failures.
- [Suspend Resume and Abort](suspend-resume-abort/README.md): A minimal example that illustrate suspend, resume and cancel workflow capabilities, plus durability.


## How to add new examples

When contributing to this directory, the project must be self-contained. 
A user must be able to copy and paste the `pom.xml` file and be able to reuse it on his projects.

1. Create the sub module project via `mvn quarkus:create` and commit the project as it's generated.
2. Add the project as a sub module in the main `examples/pom.xml` file, but DO NOT add the examples project as parent.
3. Document your example with a detailed README.md file. DO NOT commit the default Quarkus readme.
4. Ensure that integration tests has `*IT` suffix and configure the failsafe plugin to run them by default.
5. Add your example to the list in the section above.
