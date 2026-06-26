# Examples

Directory of end-to-end use case examples.

## ▶️ Running an example

Each example is a **self-contained Quarkus application** with its own Maven wrapper (`./mvnw`),
its own `README.md`, and its own prerequisites (some need Docker/Podman, Ollama, etc. — check the
example's README first).

### 1. Make the Quarkus Flow artifacts resolvable

The examples depend on Quarkus Flow (`io.quarkiverse.flow`) and, for the agentic ones, Quarkus
LangChain4j. By default, the example POMs point at the in-development version
(`quarkus.flow.version` = `1.0.0-SNAPSHOT`), which is **not published to Maven Central**. Pick one
of the two options below.

**Option A — Use a published release (fastest, no full build).**
Edit the `quarkus.flow.version` property in the example's `pom.xml` to a released version so the
dependencies resolve straight from Maven Central:

```xml
<quarkus.flow.version>0.11.0</quarkus.flow.version>
```

> See the [released versions](https://github.com/quarkiverse/quarkus-flow/tags) and pick the latest.

**Option B — Build the SNAPSHOT locally.**
From the **repository root**, build and install the Quarkus Flow artifacts into your local Maven
repository (`~/.m2`) once, then run the example against the freshly built SNAPSHOT:

```bash
# from the repository root
mvn -DskipTests install
```

### 2. Run the example in dev mode

```bash
cd examples/<example-name>
./mvnw quarkus:dev
```

That's it — Quarkus **Dev Services** auto-starts any infrastructure the example needs (e.g. Kafka)
when Docker or Podman is available. Follow the individual example's `README.md` for the URLs to open
and how to interact with it.

> **Note:** the `quarkus-flow-deployment` / `quarkus-flow-langchain4j-deployment` dependencies
> declared with `<scope>test</scope>` exist only to support this multi-module repository and are
> **not required** when you copy an example into your own project. They can be safely removed.

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
