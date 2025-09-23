# Quarkus Flow

[![Version](https://img.shields.io/maven-central/v/io.quarkiverse.quarkus-flow/quarkus-flow?logo=apache-maven\&style=flat-square)](https://central.sonatype.com/artifact/io.quarkiverse.quarkus-flow/quarkus-flow-parent)

**Quarkus Flow** is a lightweight, low‑dependency, fast, production‑grade workflow engine for Quarkus, built on the CNCF [Serverless Workflow](https://serverlessworkflow.io/) specification.

Use it to model classic workflows *and* Agentic AI orchestrations with first‑class CDI/Quarkus ergonomics.

---

## Getting Started

Nothing beats a tiny example.

### 1) Add the dependency

```xml
<dependency>
  <groupId>io.quarkiverse.quarkus-flow</groupId>
  <artifactId>quarkus-flow</artifactId>
  <version>999-SNAPSHOT</version>
</dependency>
```

> Tip: if you’re returning POJOs from JAX‑RS, also add `quarkus-rest-jackson` for JSON.

### 2) Define a workflow with the Java DSL

```java
package org.acme;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.FlowDefinition;            // marker annotation
import io.serverlessworkflow.api.types.Workflow;       // SDK type
import io.serverlessworkflow.fluent.spec.WorkflowBuilder;

@ApplicationScoped
public class HelloWorkflow {

  @FlowDefinition
  public Workflow hello() {
    return WorkflowBuilder.workflow("hello")
        .tasks(t -> t.set("{ message: \"hello world!\" }"))
        .build();
  }
}
```

> The DSL mirrors the CNCF spec. Deep dive: [DSL reference](https://github.com/serverlessworkflow/specification/blob/main/dsl-reference.md).

### 3) Run it with `FlowRunner`

```java
package org.acme;

import java.util.Map;
import java.util.concurrent.CompletionStage;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.jboss.resteasy.reactive.ResponseStatus;

import io.quarkiverse.flow.FlowRunner;

@Path("/hello")
@ApplicationScoped
public class HelloResource {

  @Inject
  FlowRunner workflowRunner;

  @ResponseStatus(200)
  @GET
  public CompletionStage<Message> hello() {
    return workflowRunner
        .start(HelloWorkflow::hello, Map.of())          // method ref = traceable linkage
        .thenApply(w -> w.as(Message.class).orElseThrow());
  }
}
```

**Other ways to reference a workflow**

1. **By name** (from the DSL `document().name()`):

   ```java
   workflowRunner.start("hello", Map.of());
   ```
2. **By method reference** *(recommended for traceability)*:

   ```java
   workflowRunner.start(HelloWorkflow::hello, Map.of());
   ```
3. **By `Workflow` instance** (less common):

   ```java
   workflowRunner.start(helloWorkflow.hello(), Map.of());
   ```

---

## What you get

* 🧩 **CNCF‑compliant** workflows via a simple Java DSL
* ⚡ **Fast start & low footprint** (Quarkus native‑friendly)
* 🧪 **Great DX**: method‑ref linkage (`HelloWorkflow::hello`), build‑time discovery, Dev UI hooks
* 🔌 **CDI‑first** engine + registries you can inject anywhere

*(More docs, Dev UI, and examples coming—see roadmap.)*

---

## Current Status

This extension is under active development. APIs may change and bugs may exist. Feedback & issues are welcome.

---

## Roadmap

Please check our [Issues](issues) and [Milestones](milestones).

## License

Apache 2.0
