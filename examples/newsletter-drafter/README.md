# Newsletter Workflow (Quarkus + Serverless Workflow + Agents + CloudEvents)

A minimal-but-complete example that orchestrates **AI agents + human-in-the-loop review** using:

- **Quarkus** (hot-reload dev mode)
- **[CNCF Workflow Specification](https://serverlessworkflow.io/)** (function-first fluent DSL)
- **LangChain4j** agents (via **Ollama**)
- **CloudEvents** over Kafka (Quarkus Dev Services)
- A tiny **web UI** (compose + review) with live updates via **WebSocket**

> Run locally with hot reload and _no manual infra setup_ (Kafka is auto-started by Quarkus **Dev Services** if Docker/Podman is available).

---

## 🚀 Quick Start

### Prerequisites

- Java 17+ and Maven
- Docker **or** Podman (for Quarkus Dev Services: Kafka)
- [Ollama](https://ollama.com/) installed and running locally

### 1) Start Ollama & pull a model

> Any chat-capable model works. Examples below use `llama3.2`—swap for your favorite.

```bash
# start the Ollama server (default: http://localhost:11434)
ollama serve
# (in another terminal)
ollama pull llama3.2
```

### 2) Run the app

```bash
# from the project root
mvn quarkus:dev
```

Open: **http://localhost:8080**

> Quarkus **Dev Services** automatically starts a Kafka broker in a Docker/Podman container the first time you run dev mode (no manual config needed).

---

## 🧭 What you’ll see

- **Compose** page (`/index.html`): fill a small “newsletter prompt” and click **Generate Draft**.
- The workflow calls the **DrafterAgent** then the **CriticAgent**, and emits a `review-required` CloudEvent.
- **Review** page (`/review.html`): shows the latest critic result, lets you **approve** or **request changes**.
    - If you choose **NEEDS_REVISION**, the workflow loops back to the drafter → critic → review.
    - If you choose **DONE**, the workflow **sends the email** (via a mock `MailService`) and finishes.

---

## 🧩 Architecture (high level)

```
+-----------------+        REST            +-------------------------+
|  Compose UI     |  POST /api/newsletter  |  Quarkus API (JAX-RS)   |
|  (index.html)   +----------------------->+  starts workflow        |
+-----------------+                        +-----------+-------------+
                                                      |
                                                      v
                                           +----------+----------+
                                           |      Workflow       |
                                           |  (Func DSL runtime) |
                                           +----------+----------+
                                                      |
                                   +------------------+------------------+
                                   |                                     |
                                   v                                     v
                        +-------------------+                  +-------------------+
                        | DrafterAgent      |                  | CriticAgent       |
                        | (LangChain4j via  |                  | (LangChain4j via  |
                        |  Ollama)          |                  |  Ollama)          |
                        +---------+---------+                  +---------+---------+
                                  |                                      |
                                  |                        CloudEvent "review-required"
                                  |                         (Kafka: flow-out topic)
                                  |                                      |
                                  |      +------------------------------ v -----------------------------+
                                  |      |   Quarkus WebSocket endpoint subscribes to flow-out and     |
                                  |      |   pushes "review-required" to the browser (review.html)     |
                                  |      +--------------+----------------------------------------------+
                                  |                     |
                                  |                     |
                                  |              +------v------+
                                  |              | Review UI   |
                                  |   PUT /api/  | (review.html|
                                  +------------->|  form)      |
                                                 +------+------+
                                                        |
                                CloudEvent "org.acme.newsletter.review.done" (Kafka: flow-in topic)
                                                        |
                                                        v
                                           +------------+-----------+
                                           | Serverless Workflow    |
                                           |  switch(status)        |
                                           |  ├─ NEEDS_REVISION -> loop Draft->Critic->Review
                                           |  └─ DONE -> send via MailService
                                           +------------------------+
```

---

## 🔁 The Workflow (visual)

```
start
  │
  ▼
[Call drafterAgent::draft (with instanceId)]
  │
  ▼
[Call criticAgent::critique (with instanceId)]
  │
  ▼
[emitJson "org.acme.email.review.required" with CriticAgentReview]
  │
  ▼
[listen to "org.acme.newsletter.review.done"]
  │   outputAs(selectFirstStringify() OR typed -> HumanReview)
  │
  ▼
[switchWhenOrElse on review.status]
  ├─ NEEDS_REVISION → go to "draftAgent" (loop back up)
  └─ DONE          → go to "sendNewsletter"
                        │
                        ▼
               [consume: MailService.send(...)]
                        │
                        ▼
                      end
```

**Key points:**

- `agent(name, methodRef, Class<T>)` wraps your agent method and **injects the workflow instance id** as the first parameter (perfect for LLM memory).
- `emitJson(type, clazz)` emits a **CloudEvent** to Kafka (`flow-out`), which the **WebSocket** layer forwards to the UI.
- `listen(to().one(event(type)))` consumes from Kafka (`flow-in`) and resumes the workflow.
- `switchWhenOrElse(...)` branches between “loop” and “finish+send”.

---

## 🧠 Agents

Agents are regular **LangChain4j** service interfaces (annotated with `@RegisterAiService`), e.g.:

- `DrafterAgent#draft(String memoryId, String payloadJson) : String`
- `CriticAgent#critique(String memoryId, String payloadJson) : CriticAgentReview`

In the fluent DSL:

```java
tasks(
  agent("draftAgent", drafterAgent::draft, String.class),
  agent("criticAgent", criticAgent::critique, String.class),
  ...
)
```

The `agent(...)` DSL uses the **workflow instance id** as “memory id” automatically.

---

## 🌐 HTTP & UI

### Endpoints

- `POST /api/newsletter`  
  Starts a workflow run using your JSON input (the **compose** payload). Returns `{instanceId}`.

- `PUT /api/newsletter`  
  Sends a human review (JSON) as a **CloudEvent** to the engine:
  ```json
  { "draft": "...", "notes": "...", "status": "NEEDS_REVISION|DONE" }
  ```

### UI

- `/index.html` – Compose page (“Generate Draft”)
    - Uses `fetch(POST /api/newsletter)`
    - Shows a spinner during generation
    - Links to `review.html?instanceId=...`

- `/review.html` – Human Review page
    - Loads latest cached **review-required** event (so you don’t miss updates if the page was closed)
    - Keeps a **WebSocket** open to receive live critic updates
    - Shows a spinner while waiting for the next agent update
    - On **DONE**, disables the form, shows a **“completed”** banner, closes the socket, clears cache

> All UI is plain HTML + PicoCSS + tiny JS (no build toolchain).

---

## 📦 Events & Topics

- Outbound (engine → UI): `org.acme.email.review.required`
    - **Produced** on Kafka topic (e.g. `flow-out`) by the workflow step `emitJson(...)`.
    - **Consumed** by the server and **pushed over WebSocket** to the browser.

- Inbound (UI → engine): `org.acme.newsletter.review.done`
    - **Produced** by `PUT /api/newsletter` (JAX-RS resource) with `Emitter<byte[]>` to Kafka (e.g. `flow-in`).
    - **Consumed** by `listen(to().one(event("org.acme.newsletter.review.done")))` in the workflow.

> Topic names & channels are wired via MicroProfile Reactive Messaging; in dev mode Quarkus will create and manage them automatically (Dev Services + Docker/Podman).

---

## 🧪 Testing & Hot-Reload

- `mvn quarkus:dev` gives you **live reload** for Java, resources, and the UI.
- Example tests (Quarkus Test + Kafka Companion) demonstrate:
    - POST to start the workflow
    - Wait for `review-required`
    - Submit `NEEDS_REVISION` once (loop)
    - Submit `DONE` (finish)
    - Assert the `MailService.send(...)` was called

---

## 🛠 Customize & Extend

- **Swap models**: change your Ollama model (e.g., `mistral`, `qwen2.5`), tune system prompts, or add new agent methods.
- **Use your own events**: adjust the event types and payload classes in the DSL (`emitJson(...)` & `listen(...)`).
- **Enrich the UI**: add auth, persistence, or a dashboard for multi-run tracking.
- **No tight coupling**: the project `pom.xml` only uses published artifacts—copy **any** files into your project or clone this repo as a starter.

> The **fluent function DSL** (`FuncDSL`) includes helpers like `agent(...)`, `emitJson(...)`, `listen(...)`, `switchWhenOrElse(...)`, `forEach(...)`.

---

## 🔧 Troubleshooting

- **400 on `PUT /api/newsletter`**  
  Ensure the review form sends a non-empty `status` and `draft`. (The UI blocks invalid submit and shows a loader.)

- **No live updates**  
  Check the browser console and server logs. Make sure the **WebSocket** is connected and Ollama is running.

- **Kafka not starting**  
  Verify Docker/Podman is running. Quarkus will log Dev Services startup. You can also point to an external Kafka in `application.properties`.

- **Ollama errors**  
  Confirm `ollama serve` is running and the model is pulled. Check `http://localhost:11434` availability.

---

## 📚 Build your own

Use this repo as a template or copy the files you need. The `pom.xml` depends only on published artifacts—no coupling to this example. Extend the DSL, add agents, create new CloudEvents, or swap the transport entirely. Have fun! 🎉
