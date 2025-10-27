# Quarkus Flow

[![Version](https://img.shields.io/maven-central/v/io.quarkiverse.flow/quarkus-flow?logo=apache-maven\&style=flat-square)](https://central.sonatype.com/artifact/io.quarkiverse.flow/quarkus-flow-parent)

**Quarkus Flow** is a lightweight, low-dependency, production-grade workflow engine for Quarkus, built on the CNCF [Serverless Workflow](https://serverlessworkflow.io/) specification.

Use it to model **classic workflows** *and* **Agentic AI orchestrations**, with first-class CDI/Quarkus ergonomics.
> 📚 Full docs: https://docs.quarkiverse.io/quarkus-flow/dev/

## Why Quarkus Flow?

* 🧩 **CNCF-compliant** workflows via a fluent Java DSL
* ⚡ **Fast start & low footprint** (Quarkus/native-friendly)
* 🔌 **CDI-first**: build-time discovery → CDI injection, no registries to wire
* 🧪 **Great DX**: inject your workflow class *or* the compiled `WorkflowDefinition`
* 🤝 **Agentic AI ready**: seamless LangChain4j integration

## Quick start

Add the dependency:

```xml
<dependency>
  <groupId>io.quarkiverse.flow</groupId>
  <artifactId>quarkus-flow</artifactId>
  <version>RELEASE</version>
</dependency>
```

Create a workflow (extend `io.quarkiverse.flow.Flow`):

```java
import jakarta.enterprise.context.ApplicationScoped;
import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.spec.FuncWorkflowBuilder;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.*;

@ApplicationScoped
public class HelloWorkflow extends Flow {
  @Override
  public Workflow descriptor() {
    return FuncWorkflowBuilder.workflow("hello")
      .tasks(set("{ message: \"hello world!\" }"))
      .build();
  }
}
```

Run:

```bash
./mvnw quarkus:dev
```

## Messaging (TL;DR)

No extra artifact needed—**auto-activates** if you add a connector like Kafka:

```xml
<dependency>
  <groupId>io.quarkus</groupId>
  <artifactId>quarkus-messaging-kafka</artifactId>
</dependency>
```

Then configure:

```properties
quarkus.flow.messaging.defaults-enabled=true
mp.messaging.incoming.flow-in.connector=smallrye-kafka
mp.messaging.outgoing.flow-out.connector=smallrye-kafka
```

See the [**Messaging** doc](https://docs.quarkiverse.io/quarkus-flow/dev/messaging.html) for full details.

## Examples

- `examples/newsletter-drafter` – agentic workflow (drafter + critic + HITL)
- Docs snippets under `docs/modules/ROOT/examples/`

## Contributing

Issues & PRs welcome! Please:
- run `./mvnw -q -DskipTests install` before opening PRs
- keep docs in `docs/` (Antora). Dev locally with:

```bash
./mvnw -pl docs -am quarkus:dev
# press 'w' when Quarkus starts to open the docs site
```

License: Apache-2.0
