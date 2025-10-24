# Quarkus Flow

[![Version](https://img.shields.io/maven-central/v/io.quarkiverse.flow/quarkus-flow?logo=apache-maven\&style=flat-square)](https://central.sonatype.com/artifact/io.quarkiverse.flow/quarkus-flow-parent)

**Quarkus Flow** is a lightweight, low-dependency, production-grade workflow engine for Quarkus, built on the CNCF [Serverless Workflow](https://serverlessworkflow.io/) specification.

Use it to model **classic workflows** *and* **Agentic AI orchestrations**, with first-class CDI/Quarkus ergonomics.

---

## Why Quarkus Flow?

* 🧩 **CNCF-compliant** workflows via a fluent Java DSL
* ⚡ **Fast start & low footprint** (Quarkus/native-friendly)
* 🔌 **CDI-first**: build-time discovery → CDI injection, no registries to wire
* 🧪 **Great DX**: inject your workflow class *or* the compiled `WorkflowDefinition`
* 🤝 **Agentic AI ready**: seamless LangChain4j integration

---

## Getting Started

Nothing beats a tiny example.

### 1) Add the dependency

```xml
<dependency>
  <groupId>io.quarkiverse.flow</groupId>
  <artifactId>quarkus-flow</artifactId>
  <version>0.1.0</version>
</dependency>
```

> If you’re returning POJOs from JAX-RS, also add `quarkus-rest-jackson` for JSON.

---

### 2) Define a workflow by extending `Flow`

```java
package org.acme;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.spec.WorkflowBuilder;

@ApplicationScoped
public class HelloWorkflow extends Flow {

  @Override
  public Workflow descriptor() {
    return WorkflowBuilder.workflow("hello")
        .tasks(t -> t.set("{ message: \"hello world!\" }"))
        .build();
  }
}
```

* You **extend `Flow`** and implement a single method: `descriptor()`, using the fluent DSL.
* CDI is available in your workflow class — feel free to `@Inject` collaborators and use them while building the descriptor.

---

### 3) Run it – two ergonomic options

#### A) Inject your workflow class and start it

```java
package org.acme;

import java.util.Map;
import java.util.concurrent.CompletionStage;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.jboss.resteasy.reactive.ResponseStatus;

@Path("/hello")
@ApplicationScoped
public class HelloResource {

  @Inject
  HelloWorkflow hello; // inject the Flow subclass

  @GET
  @ResponseStatus(200)
  public CompletionStage<Message> hello() {
    return hello
        .startInstance(Map.of())                // convenience on Flow
        .thenApply(w -> w.as(Message.class).orElseThrow());
  }
}
```

#### B) Inject the compiled `WorkflowDefinition` by **FQCN qualifier**

Every discovered workflow produces a CDI bean:

```java
import io.smallrye.common.annotation.Identifier;
import io.serverlessworkflow.impl.WorkflowDefinition;

@Inject
@Identifier("org.acme.HelloWorkflow") // FQCN of the workflow class
WorkflowDefinition helloDef;
```

> Tip: pick option A unless you specifically need the raw `WorkflowDefinition`.

---

## How discovery, naming & injection work

* At **build time**, Quarkus Flow finds every non-abstract subclass of `io.quarkiverse.flow.Flow`.
* For each, it compiles a `WorkflowDefinition` and publishes it as a CDI bean **qualified with `@Identifier("<workflow FQCN>")`**.
  Example: class `org.acme.HelloWorkflow` → qualifier `@Identifier("org.acme.HelloWorkflow")`.

---

## Agentic Workflows (LangChain4j)

Quarkus Flow integrates with [Quarkus LangChain4j](https://docs.quarkiverse.io/quarkus-langchain4j/dev/quickstart.html) so you can orchestrate **AI agents** as first-class tasks.

### 1) Add a LangChain4j implementation

```xml
<dependency>
  <groupId>io.quarkiverse.langchain4j</groupId>
  <artifactId>quarkus-langchain4j-ollama</artifactId>
  <version>YOUR_VERSION</version>
</dependency>
```

*(Use OpenAI/Vertex/etc. if you prefer.)*

### 2) Define your Agent interface

```java
package org.acme.agentic;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
@ApplicationScoped
@SystemMessage("""
  You are a minimal echo agent.
  - If the input is empty/blank/null → reply 'Your message is empty'
  - Otherwise reply with the same text
""")
public interface EchoAgent {
  @UserMessage("{{message}}")
  String echo(@V("message") String message);
}
```

### 3) Create an agentic workflow

```java
package org.acme.agentic;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.agentic.AgentWorkflowBuilder;

@ApplicationScoped
public class HelloAgenticWorkflow extends Flow {

  @Inject EchoAgent agent;

  @Override
  public Workflow descriptor() {
    return AgentWorkflowBuilder.workflow("hello-agentic")
        .tasks(t -> t.callFn(f -> f.function(agent::echo))) // call the agent
        .build();
  }
}
```

### 4) Use it

```java
package org.acme.agentic;

import java.util.concurrent.CompletionStage;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

@Path("/echo")
@ApplicationScoped
public class HelloAgenticResource {

  @Inject HelloAgenticWorkflow wf;

  @POST
  public CompletionStage<String> echo(String message) {
    return wf.startInstance(message)
             .thenApply(m -> m.asText().orElseThrow());
  }
}
```

## Messaging

To understand eventing support, please see the [Messaging README](https://github.com/quarkiverse/quarkus-flow/blob/main/messaging/README.md).

## Native compilation

Due to the limitation described in https://github.com/quarkiverse/quarkus-flow/issues/8, we recommend using either the `25.0.0.1-Final-java25` native image builder to generate native images or pass `--report-unsupported-elements-at-runtime` to the native image build command with `-Dquarkus.native.additional-build-args-append=
--report-unsupported-elements-at-runtime` when using the Mandrel 23.1 native image builder image (current default).

---

## FAQ

**Can my workflow class use CDI?**
Absolutely. `descriptor()` runs on a CDI bean; `@Inject` whatever you need.

**How do I inject the compiled definition?**
Use the workflow **class FQCN** as the qualifier:
`@Inject @Identifier("org.acme.HelloWorkflow") WorkflowDefinition def;`

**Dev mode & native?**
Fully supported. Definitions are built at startup, designed for Quarkus native friendliness.

---

## Roadmap

* Richer DSL coverage (events, retries, compensation, etc.)
* Dev-UI visualizations & tracers
* Prebuilt AI orchestration patterns
* Native build optimizations
* Deeper LangChain4j glue

---

## License

Apache 2.0
