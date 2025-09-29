# Quarkus Flow

[![Version](https://img.shields.io/maven-central/v/io.quarkiverse.flow/quarkus-flow?logo=apache-maven\&style=flat-square)](https://central.sonatype.com/artifact/io.quarkiverse.flow/quarkus-flow-parent)

**Quarkus Flow** is a lightweight, low-dependency, production-grade workflow engine for Quarkus, built on the CNCF [Serverless Workflow](https://serverlessworkflow.io/) specification.

Use it to model **classic workflows** *and* **Agentic AI orchestrations**, with first-class CDI/Quarkus ergonomics.

---

## Why Quarkus Flow?

* 🧩 **CNCF-compliant** workflows via a fluent Java DSL
* ⚡ **Fast start & low footprint** (Quarkus/native-friendly)
* 🧪 **Great DX**: build-time discovery → CDI injection of `WorkflowDefinition`
* 🤝 **Agentic AI ready**: out-of-the-box LangChain4j integration
* 🔌 **CDI-first**: no manual registries/runners—just inject and run

---

## Getting Started

Nothing beats a tiny example.

### 1. Add the dependency

```xml
<dependency>
   <groupId>io.quarkiverse.flow</groupId>
   <artifactId>quarkus-flow</artifactId>
   <version>999-SNAPSHOT</version>
</dependency>
```

> [!TIP]
> If you’re returning POJOs from JAX-RS, also add `quarkus-rest-jackson` for JSON.

---

### 2. Define a workflow **descriptor** with the Java DSL

```java
package org.acme;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.FlowDescriptor;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.spec.WorkflowBuilder;

@ApplicationScoped
public class HelloWorkflow {

  @FlowDescriptor // id defaults to method name: "hello"
  public Workflow hello() {
    return WorkflowBuilder.workflow()
        .tasks(t -> t.set("{ message: \"hello world!\" }"))
        .build();
  }
}
```

> [!NOTE]
> The DSL mirrors the CNCF spec. Deep dive: [DSL reference](https://github.com/serverlessworkflow/specification/blob/main/dsl-reference.md).

---

### 3. Inject a workflow **definition** and run it

```java
package org.acme;

import java.util.Map;
import java.util.concurrent.CompletionStage;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.jboss.resteasy.reactive.ResponseStatus;

import io.quarkiverse.flow.FlowDefinition;
import io.serverlessworkflow.impl.WorkflowDefinition;

@Path("/hello")
@ApplicationScoped
public class HelloResource {

  @Inject
  @FlowDefinition("hello")
  WorkflowDefinition helloWorkflow;

  @ResponseStatus(200)
  @GET
  public CompletionStage<Message> hello() {
    return helloWorkflow
        .instance(Map.of())
        .start()
        .thenApply(w -> w.as(Message.class).orElseThrow());
  }
}
```

**How IDs are chosen**

* If you specify `@FlowDescriptor("my-id")`, that id is used.
* Otherwise, the **method name** is used (e.g., `hello`).
* At runtime, the recorder sets `document().name(id)` on the `Workflow`, so your SDK/Dev UI see the same id—no duplication needed in the DSL.

---

## Agentic Workflows

Quarkus Flow integrates seamlessly with [Quarkus LangChain4j](https://docs.quarkiverse.io/quarkus-langchain4j/dev/quickstart.html), so you can orchestrate **AI agents** as first-class workflow tasks.

### 1. Add the LangChain4j dependency

```xml
<dependency>
    <groupId>io.quarkiverse.langchain4j</groupId>
    <artifactId>quarkus-langchain4j-ollama</artifactId>
    <version>YOUR VERSION</version>
</dependency>
```

> [!NOTE]
> You may pick a different model (e.g., OpenAI, Vertex AI). See the [Quarkus LangChain4j docs](https://docs.quarkiverse.io/quarkus-langchain4j/dev/quickstart.html).

---

### 2. Define your **Agent Interface**

```java
package org.acme.agentic.ai;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
@SystemMessage("""
        You are a minimal echo agent.

        - If the user's message is empty, missing, "null", or whitespace, respond: Your message is empty
        - Otherwise, respond with exactly the user's message content.
        """)
@ApplicationScoped
public interface HelloAgent {

    @UserMessage("{{message}}")
    String helloWorld(@V("message") String message);
}
```

---

### 3. Define a workflow descriptor

```java
package org.acme.agentic.ai;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.flow.FlowDescriptor;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.agentic.AgentWorkflowBuilder;

@ApplicationScoped
public class HelloAgenticWorkflow {

    @Inject
    HelloAgent helloAgent;

    @FlowDescriptor
    public Workflow helloAgenticWorkflow() {
        return FuncWorkflowBuilder.workflow()
                .tasks(t -> t.callFn(f -> f.function(helloAgent::helloWorld)))
                .build();
    }
}
```

> [!IMPORTANT]
> You can chain multiple agents together or mix with REST calls, events, timers, etc.

---

### 4. Inject and run it

```java
package org.acme.agentic.ai;

import java.util.concurrent.CompletionStage;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

import io.quarkiverse.flow.FlowDefinition;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.smallrye.common.annotation.Blocking;

@Path("/hello")
@ApplicationScoped
public class HelloAgenticResource {

    @Inject
    @FlowDefinition("helloAgenticWorkflow")
    WorkflowDefinition helloAgenticWorkflow;

    @POST
    @Blocking
    public CompletionStage<String> hello(String message) {
        return helloAgenticWorkflow
                .instance(message)
                .start()
                .thenApply(w -> w.asText().orElseThrow());
    }
}
```

---

## Status

This extension is under active development. APIs may change and bugs may exist. Feedback & issues are welcome.

---

## Roadmap

* More workflow DSL coverage
* Dev UI visualizations
* Pre-built AI orchestration patterns
* Native build optimizations
* Tight LangChain4j integration

See [Issues](https://github.com/quarkiverse/quarkus-flow/issues) and [Milestones](https://github.com/quarkiverse/quarkus-flow/milestones).

---

## License

Apache 2.0
