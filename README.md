# Quarkus Flow

[![Version](https://img.shields.io/maven-central/v/io.quarkiverse.flow/quarkus-flow?logo=apache-maven&style=flat-square)](https://central.sonatype.com/artifact/io.quarkiverse.flow/quarkus-flow-parent)

**Quarkus Flow** is a lightweight, low-dependency, production-grade workflow engine for Quarkus, built on the CNCF [Serverless Workflow](https://serverlessworkflow.io/) specification.

Use it to model **classic workflows** *and* **Agentic AI orchestrations**, with first-class CDI/Quarkus ergonomics.

> 📚 Docs: https://docs.quarkiverse.io/quarkus-flow/dev/
>
> 🤖 Agentic (LangChain4j):
> - https://docs.quarkiverse.io/quarkus-flow/dev/howto-langchain4j-agentic-workflows.html
> - https://docs.quarkiverse.io/quarkus-flow/dev/concepts-agentic-langchain4j.html

## Why Quarkus Flow?

* 🧩 **CNCF-compliant** workflows via a fluent Java DSL
* ⚡ **Fast start & low footprint** (Quarkus/native-friendly)
* 🔌 **CDI-first**: build-time discovery → CDI injection, no registries to wire
* 🧪 **Great DX**: inject your workflow class *or* the compiled `WorkflowDefinition`
* 🤝 **Agentic AI ready**: orchestrate LangChain4j agents as workflow **tasks** (with loops + human-in-the-loop)

## Quick start (classic workflow)

Add the dependency:

```xml
<dependency>
  <groupId>io.quarkiverse.flow</groupId>
  <artifactId>quarkus-flow</artifactId>
  <version>RELEASE</version>
</dependency>
````

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

## LangChain4j (choose your approach)

Quarkus Flow supports **three complementary ways** to use LangChain4j:

1. **Java DSL tasks** — call LangChain4j beans from Flow tasks via `function(…)`. Use this when you want full control of the workflow topology and to mix AI with HTTP, messaging, timers, long-running instances, etc.
2. **Annotations → generated workflows** — declare agentic workflow patterns with LangChain4j Agentic Workflow API annotations (`@SequenceAgent`, `@ParallelAgent`, …) and let Quarkus Flow generate/register workflows for you.
3. **Hybrid** — declare patterns with annotations, then call them from a larger Java DSL workflow via `function(…)`.

Docs:

* [https://docs.quarkiverse.io/quarkus-flow/dev/langchain4j.html#_choose_your_approach](https://docs.quarkiverse.io/quarkus-flow/dev/langchain4j.html#_choose_your_approach)
* [https://docs.quarkiverse.io/quarkus-flow/dev/howto-langchain4j-agentic-workflows.html](https://docs.quarkiverse.io/quarkus-flow/dev/howto-langchain4j-agentic-workflows.html)

### 1) Java DSL tasks (call LangChain4j beans)

**Dependencies**

```xml
<dependency>
  <groupId>io.quarkiverse.flow</groupId>
  <artifactId>quarkus-flow</artifactId>
  <version>RELEASE</version>
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
  <version>RELEASE</version>
</dependency>

<!-- Quarkus Flow ↔ LangChain4j agentic integration -->
<dependency>
  <groupId>io.quarkiverse.flow</groupId>
  <artifactId>quarkus-flow-langchain4j</artifactId>
  <version>RELEASE</version>
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
import dev.langchain4j.agentic.declarative.ParallelAgent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.declarative.SubAgent;

public final class Agents {

  // A generated workflow: chain sub-agents sequentially
  @SequenceAgent
  public interface StoryCreator {
    String write(String topic, String style, String audience);
  }

  // A generated workflow: fork-join across sub-agents
  @ParallelAgent
  public interface EveningPlanner {
    EveningPlan plan(String city, Mood mood);
  }

  @SubAgent interface DinnerAgent { String suggestDinner(String city, Mood mood); }
  @SubAgent interface DrinksAgent { String suggestDrinks(String city, Mood mood); }

  public enum Mood { ROMANTIC, CHILL, PARTY, FAMILY }
  public record EveningPlan(String city, Mood mood, String dinner, String drinks) {}
}
```

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

* `examples/newsletter-drafter` – agentic workflow (drafter + critic + HITL)
* `examples/langchain4j-agentic-workflow` – minimal agentic example
* Docs snippets under `docs/modules/ROOT/examples/`

## Contributing

Issues & PRs welcome! Please:

* run `./mvnw -q -DskipTests install` before opening PRs
* keep docs in `docs/` (Antora). Dev locally with:

```bash
./mvnw -pl docs -am quarkus:dev
# press 'w' when Quarkus starts to open the docs site
```

License: Apache-2.0
