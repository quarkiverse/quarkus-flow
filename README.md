# Quarkus Flow

[![Version](https://img.shields.io/maven-central/v/io.quarkiverse.flow/quarkus-flow?logo=apache-maven&style=flat-square)](https://central.sonatype.com/artifact/io.quarkiverse.flow/quarkus-flow-parent)

[![Native Compilation Nightly](https://github.com/quarkiverse/quarkus-flow/actions/workflows/native-nigthly-ci.yaml/badge.svg)](https://github.com/quarkiverse/quarkus-flow/actions/workflows/native-nigthly-ci.yaml)
[![Quarkus ecosystem CI](https://github.com/quarkiverse/quarkus-flow/actions/workflows/quarkus-snapshot.yaml/badge.svg)](https://github.com/quarkiverse/quarkus-flow/actions/workflows/quarkus-snapshot.yaml)
[![Quarkus Platform Nightly](https://github.com/quarkiverse/quarkus-flow/actions/workflows/quarkus-platform-nightly.yml/badge.svg)](https://github.com/quarkiverse/quarkus-flow/actions/workflows/quarkus-platform-nightly.yml)
[![Build with Integration Tests](https://github.com/quarkiverse/quarkus-flow/actions/workflows/build-it.yml/badge.svg?branch=main&event=push)](https://github.com/quarkiverse/quarkus-flow/actions/workflows/build-it.yml)
[![Build](https://github.com/quarkiverse/quarkus-flow/actions/workflows/build.yml/badge.svg?branch=main&event=push)](https://github.com/quarkiverse/quarkus-flow/actions/workflows/build.yml)

**Quarkus Flow** is a lightweight, low-dependency, production-grade workflow engine for Quarkus, built on the [Serverless Workflow](https://serverlessworkflow.io/) specification CNCF sandbox project.

Use it to model **classic workflows** *and* **Agentic AI orchestrations**, with first-class CDI/Quarkus ergonomics.

> 📚 Docs: https://docs.quarkiverse.io/quarkus-flow/dev/
>
> 🤖 Agentic (LangChain4j):
> - https://docs.quarkiverse.io/quarkus-flow/dev/langchain4j.html
> - https://docs.quarkiverse.io/quarkus-flow/dev/concepts-agentic-langchain4j.html

## Why Quarkus Flow?

* 🧩 **Specification-compliant** workflows via a fluent Java DSL
* ⚡ **Fast start & low footprint** (Quarkus/native-friendly)
* 🔌 **CDI-first**: build-time discovery → CDI injection, no registries to wire
* 🧪 **Great DX**: inject your workflow class *or* the compiled `WorkflowDefinition`
* 🤝 **Agentic AI ready**: orchestrate LangChain4j agents as workflow **tasks** (with loops + human-in-the-loop)

## 🚀 Try it First (Optional)

Want to see Quarkus Flow in action before adding it to your project? Use our pre-built Docker runner to explore workflows without writing any code:

```bash
curl -fsSL https://raw.githubusercontent.com/quarkiverse/quarkus-flow/main/runner/app/quickstart.sh | bash
```

> 💡 **This is just for exploration!** The Docker runner lets you try Quarkus Flow features quickly, but the real power comes from integrating it into your Quarkus application below.

<details>
<summary>Or use Docker directly</summary>

```bash
# 1. Create a workflow directory with an example
mkdir -p ~/quarkus-flow-quickstart/workflows
cat > ~/quarkus-flow-quickstart/workflows/hello.yaml << 'EOF'
document:
  dsl: '1.0.0'
  namespace: demo
  name: hello-world
  version: '1.0.0'
do:
  - greet:
      set:
        message: '${ "Hello, " + .name + "!" }'
EOF

# 2. Run Quarkus Flow (foreground, Ctrl+C to stop)
docker run --rm \
  -p 8080:8080 \
  -v ~/quarkus-flow-quickstart/workflows:/deployments/workflows:ro \
  quay.io/quarkiverse/quarkus-flow-runner:latest-minimal

# 3. In another terminal, try the workflow
curl -X POST "http://localhost:8080/q/flow/exec/demo/hello-world/1.0.0?wait=true" \
  -H "Content-Type: application/json" \
  -d '{"name": "Alice"}' | jq

# Visit dashboard: http://localhost:8080
# Stop: Ctrl+C in the Docker terminal
```

**Note:** Workflows are loaded at startup. To load modified/new workflows, restart the container.

</details>

<details>
<summary>More runner options (docker-compose, production variants)</summary>

**Using docker-compose:**

```bash
git clone https://github.com/quarkiverse/quarkus-flow.git
cd quarkus-flow/runner/app
docker-compose up
```

**Production variants:**
- PostgreSQL + HA: [`latest-standard`](https://quay.io/repository/quarkiverse/quarkus-flow-runner?tab=tags)
- Kafka messaging: [`latest-messaging`](https://quay.io/repository/quarkiverse/quarkus-flow-runner?tab=tags)

See [`runner/app/`](runner/app/) for complete documentation.

</details>

## Quick Start

Add Quarkus Flow to your Quarkus application:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>io.quarkiverse.flow</groupId>
      <artifactId>quarkus-flow-bom</artifactId>
      <version>RELEASE</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

Then add the dependency (version managed by BOM):

```xml
<dependencies>
  <dependency>
    <groupId>io.quarkiverse.flow</groupId>
    <artifactId>quarkus-flow</artifactId>
  </dependency>
</dependencies>
```

Create a workflow (extend `io.quarkiverse.flow.Flow`):

```java
import jakarta.enterprise.context.ApplicationScoped;
import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;

import static io.quarkiverse.flow.dsl.FlowDSL.*;

@ApplicationScoped
public class HelloWorkflow extends Flow {
  @Override
  public Workflow descriptor() {
    return FlowWorkflowBuilder.workflow("hello")
      .tasks(set("{ message: \"hello world!\" }"))
      .build();
  }
}
```

Run:

```bash
./mvnw quarkus:dev
```

**Next steps:**
- **Load YAML workflows:** See [Workflow Definitions](https://docs.quarkiverse.io/quarkus-flow/dev/workflow-definitions.html) to load [CNCF Workflow DSL](https://github.com/serverlessworkflow/specification/blob/main/dsl-reference.md) files
- **Add persistence:** See [Persistence](https://docs.quarkiverse.io/quarkus-flow/dev/persistence.html) for MVStore, JPA, or Redis backends
- **Add messaging:** See [Messaging](https://docs.quarkiverse.io/quarkus-flow/dev/messaging.html) for Kafka/AMQP integration
- **Explore examples:** Check out [`examples/`](examples/) for complete sample applications

---

## LangChain4j Integration (Agentic AI Workflows)

Quarkus Flow supports **three complementary ways** to use LangChain4j:

1. **Java DSL tasks** — call LangChain4j beans from Flow tasks via `function(…)`. Use this when you want full control of the workflow topology and to mix AI with HTTP, messaging, timers, long-running instances, etc.
2. **Annotations → generated workflows** — declare agentic workflow patterns with LangChain4j Agentic Workflow API annotations (`@SequenceAgent`, `@ParallelAgent`, …) and let Quarkus Flow generate/register workflows for you.
3. **Hybrid** — declare patterns with annotations, then call them from a larger Java DSL workflow via `function(…)`.

Docs:

* [https://docs.quarkiverse.io/quarkus-flow/dev/langchain4j.html](https://docs.quarkiverse.io/quarkus-flow/dev/langchain4j.html)

### 1) Java DSL tasks (call LangChain4j beans)

**Dependencies**

```xml
<dependency>
  <groupId>io.quarkiverse.flow</groupId>
  <artifactId>quarkus-flow</artifactId>
</dependency>

<!-- Choose ONE LangChain4j provider (Ollama, OpenAI, …) -->
<dependency>
  <groupId>io.quarkiverse.langchain4j</groupId>
  <artifactId>quarkus-langchain4j-ollama</artifactId>
</dependency>
```

**LangChain4j annotations you’ll use here** are the classic AI-service ones, e.g. `@RegisterAiService`, `@SystemMessage`, `@UserMessage`, `@MemoryId`, `@V`:

```java
import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
@ApplicationScoped
@SystemMessage("You draft a short, friendly newsletter paragraph. Return ONLY the final draft text.")
public interface DrafterAgent {
  @UserMessage("Brief:
{{brief}}")
  String draft(@MemoryId String memoryId, @V("brief") String brief);
}
```

Then orchestrate it from a Flow using regular tasks:

```java
// pseudo-snippet: call any CDI bean method with function(…)
// function("draft", drafterAgent::draft, String.class)
```

### 2) Annotations → generated workflows (LangChain4j Agentic Workflow API)

> `quarkus-flow-langchain4j` is only required when you use LangChain4j’s **agentic** module (`langchain4j-agentic` / `quarkus-langchain4j-agentic`).
> See: [https://docs.langchain4j.dev/tutorials/agents](https://docs.langchain4j.dev/tutorials/agents)

**Dependencies**

```xml
<dependency>
  <groupId>io.quarkiverse.flow</groupId>
  <artifactId>quarkus-flow</artifactId>
</dependency>

<!-- Quarkus Flow ↔ LangChain4j agentic integration -->
<dependency>
  <groupId>io.quarkiverse.flow</groupId>
  <artifactId>quarkus-flow-langchain4j</artifactId>
</dependency>

<!-- LangChain4j Agentic (workflow API + annotations like @SequenceAgent/@ParallelAgent) -->
<dependency>
  <groupId>io.quarkiverse.langchain4j</groupId>
  <artifactId>quarkus-langchain4j-agentic</artifactId>
</dependency>

<!-- Choose ONE LangChain4j provider -->
<dependency>
  <groupId>io.quarkiverse.langchain4j</groupId>
  <artifactId>quarkus-langchain4j-ollama</artifactId>
</dependency>
```

**Annotations you’ll use here** come from the Agentic Workflow API, e.g. `@SequenceAgent`, `@ParallelAgent`, `@LoopAgent`, `@ConditionalAgent`.
Quarkus Flow discovers these methods at build time and registers generated workflows automatically.

```java
import dev.langchain4j.agentic.declarative.SequenceAgent;

public final class Agents {

  // A generated workflow: chain sub-agents sequentially
  public interface StoryCreatorWithConfigurableStyleEditor {
    @SequenceAgent(outputKey = "story", subAgents = { CreativeWriter.class, AudienceEditor.class, StyleEditor.class })
      String write(@V("topic") String topic, @V("style") String style, @V("audience") String audience);
  }

  // pseudo-snippet
  public interface CreativeWriter {

  }

  // pseudo-snippet
  public interface AudienceEditor {

  }

  // pseudo-snippet
  public interface StyleEditor {

  }
}
```

> **Content supressed on purpose**, see the complete example here: https://github.com/quarkiverse/quarkus-flow/tree/main/examples/langchain4j-agentic-workflow.

### 3) Hybrid (call a generated agentic workflow from a larger Flow)

Define the agentic topology with annotations (as above), then inject the generated bean and call it from your main workflow using `function(…)`, continuing with non-AI tasks (HTTP, messaging, timers, HITL, …).

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

See the [Messaging doc](https://docs.quarkiverse.io/quarkus-flow/dev/messaging.html) for full details.

## Examples

This repository contains a growing list of end-to-end examples in the `examples/` directory,
covering various use cases and integrations.
To see the full list, check the [examples/](examples/README.md) directory.

* Docs snippets under `docs/modules/ROOT/examples/`

## Using SNAPSHOT versions

Want to try the latest unreleased changes? SNAPSHOT artifacts are published to the
Maven Central snapshots repository. Register it in your `pom.xml`:

```xml
<repositories>
  <repository>
    <id>central-snapshots</id>
    <url>https://central.sonatype.com/repository/maven-snapshots/</url>
    <releases>
      <enabled>false</enabled>
    </releases>
    <snapshots>
      <enabled>true</enabled>
    </snapshots>
  </repository>
</repositories>
```

Then import the BOM using the `1.0.0-SNAPSHOT` version:

```xml
<dependency>
  <groupId>io.quarkiverse.flow</groupId>
  <artifactId>quarkus-flow-bom</artifactId>
  <version>1.0.0-SNAPSHOT</version>
  <type>pom</type>
  <scope>import</scope>
</dependency>
```

## Contributing

Issues & PRs welcome! Please:

* run `./mvnw -q -DskipTests install` before opening PRs
* keep docs in `docs/` (Antora). Dev locally with:

```bash
./mvnw -pl docs -am quarkus:dev
# press 'w' when Quarkus starts to open the docs site
```

License: Apache-2.0
