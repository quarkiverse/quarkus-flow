# Quarkus Flow

[![Version](https://img.shields.io/maven-central/v/io.quarkiverse.quarkus.flow/quarkus-flow?logo=apache-maven\&style=flat-square)](https://central.sonatype.com/artifact/io.quarkiverse.quarkus.flow/quarkus-flow-parent)

**Quarkus Flow** is a lightweight, low‑dependency, fast, production‑grade workflow engine for Quarkus, built on the CNCF [Serverless Workflow](https://serverlessworkflow.io/) specification.

Use it to model classic workflows *and* Agentic AI orchestrations with first‑class CDI/Quarkus ergonomics.

---

## Getting Started

Nothing beats a tiny example.

### 1) Add the dependency

```xml
<dependency>
   <groupId>io.quarkiverse.quarkus.flow</groupId>
   <artifactId>quarkus-flow</artifactId>
   <version>999-SNAPSHOT</version>
</dependency>
```

> Tip: if you’re returning POJOs from JAX‑RS, also add `quarkus-rest-jackson` for JSON.

### 2) Define a workflow **descriptor** with the Java DSL

```java
package org.acme;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.FlowDescriptor;              // marker annotation
import io.serverlessworkflow.api.types.Workflow;        // SDK type
import io.serverlessworkflow.fluent.spec.WorkflowBuilder;

@ApplicationScoped
public class HelloWorkflow {

  @FlowDescriptor // id defaults to method name: "hello"
  public Workflow hello() {
    return WorkflowBuilder.workflow()                    // name is set for you at runtime
        .tasks(t -> t.set("{ message: \"hello world!\" }"))
        .build();
  }
}
```

> The DSL mirrors the CNCF spec. Deep dive: [DSL reference](https://github.com/serverlessworkflow/specification/blob/main/dsl-reference.md).

### 3) Inject a workflow **definition** and run it

```java
package org.acme;

import java.util.Map;
import java.util.concurrent.CompletionStage;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.jboss.resteasy.reactive.ResponseStatus;

import io.quarkiverse.flow.FlowDefinition;              // qualifier
import io.serverlessworkflow.impl.WorkflowDefinition;   // runtime definition

@Path("/hello")
@ApplicationScoped
public class HelloResource {

  @Inject
  @FlowDefinition("hello")
  WorkflowDefinition helloWorkflow;                     // CDI *is* your workflow catalog

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
* At runtime the recorder sets `document().name(id)` on the `Workflow`, so your SDK/Dev UI see the same id—no duplication needed in the DSL.

---

## What you get

* 🧩 **CNCF‑compliant** workflows via a simple Java DSL
* ⚡ **Fast start & low footprint** (Quarkus/native‑friendly)
* 🧪 **Great DX**: build‑time discovery → CDI injection of `WorkflowDefinition`
* 🔌 **CDI‑first**: no manual registries/runners—just inject and run

*(More docs, Dev UI, and examples coming—see roadmap.)*

---

## Current Status

This extension is under active development. APIs may change and bugs may exist. Feedback & issues are welcome.

---

## Roadmap

Please check our [Issues](https://github.com/quarkiverse/quarkus-flow/issues) and [Milestones](https://github.com/quarkiverse/quarkus-flow/milestones).

## License

Apache 2.0
