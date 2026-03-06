# Intelligent Newsletter Drafter (Quarkus Flow + LangChain4j + Human-in-the-Loop)

A minimal-but-complete example that orchestrates a **Dual-Loop AI Workflow** (AI-to-AI iteration + Human-in-the-Loop review) using:

- **Quarkus** (hot-reload dev mode)
- **[Quarkus Flow](https://docs.quarkiverse.io/quarkus-flow/dev/)** (function-first fluent DSL for Serverless Workflow)
- **LangChain4j** (Declarative `@SequenceAgent` and Structured Outputs via **Ollama**)
- **CloudEvents** over Kafka (Quarkus Dev Services)
- A **Single Page Application (SPA)** UI with live updates via **WebSocket**

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

The UI is a clean, responsive Single Page Application built with Bootstrap. It flows through four states:

1. **Compose**: Fill out a form capturing Market Mood, Tone, Length, and Macro Data. Click **Generate Draft**.
2. **AI Loading**: The system runs a high-speed LangChain4j AI sequence (`Drafter` → `Critic` → `AI Editor`) entirely in the background.
3. **Human Review**: A WebSocket pushes the finalized AI draft to your screen. You can edit the text directly, add Manager Notes, and click **Needs AI Revision** (loops back to the AI Editor) or **Approve & Send Email**.
4. **Success**: The workflow completes, logs the sent email, and resets for the next run.

---

## 🧩 Architecture: The Two Loops

This project separates concerns by utilizing two distinct loops:
1. **The Fast Inner Loop (LangChain4j):** An automated AI sequence where agents debate and refine the draft until it passes compliance.
2. **The Durable Outer Loop (Quarkus Flow):** The overarching business process that pauses, waits for asynchronous Human CloudEvents via Kafka, and finalizes the task.

```text
                                           +-----------------------------------+
                                           |        QUARKUS FLOW (Outer Loop)  |
                                           |                                   |
+-----------------+                        |     +-----------------------+     |
|  SPA Web UI     |  POST /api/newsletter  |     | 1. AutoCriticSequence |     |
|  (index.html)   +----------------------------->+    (Draft -> Critic ->|     |
+-----------------+                        |     |     CriticEditor)     |     |
        ^                                  |     +-----------+-----------+     |
        |                                  |                 |                 |
        | CloudEvent "review-required"     |                 v                 |
        | (WebSocket)                      |     +-----------------------+     |
        +----------------------------------------+ 2. Emit "reviewReady" |     |
        |                                  |     +-----------+-----------+     |
        |                                  |                 |                 |
        | PUT /api/newsletter              |                 v                 |
        | (Kafka: flow-in)                 |     +-----------------------+     |
        +--------------------------------------->+ 3. Listen for Human   |     |
                                           |     |    Review Event       |     |
                                           |     +-----------+-----------+     |
                                           |                 |                 |
                                           |           [Approved?]             |
                                           |          /           \            |
                                           |       NO               YES        |
                                           |      /                   \        |
                                           |     v                     v       |
                                           | +----------------+   +----------+ |
                                           | | 4. HumanEditor |   | 5. Send  | |
                                           | |    Agent       |   |    Email | |
                                           | +-------+--------+   +----------+ |
                                           |         |                         |
                                           +---------|-------------------------+
                                                     |
                                                     +-- (Loops back to Step 2)
```

---

## 🧠 The Magic: Structured Outputs & Pure Java Records

Gone are the days of begging the LLM to output valid JSON in the system prompt! This example heavily leverages **LangChain4j Structured Outputs**.

All data passed between the UI, the Flow engine, and the AI Agents are strictly typed **Java Records** and **Enums**. LangChain4j automatically infers the JSON schema from your Records and maps the LLM response natively.

```java
// Pure Java Domain Objects
public record NewsletterRequest(MarketMood mood, List<String> topMovers, String macroData, Tone tone, Length length) {}
public record NewsletterDraft(String title, String lead, String body) {}
```

### Declarative AI Sequences
The AI Inner loop is defined using LangChain4j's powerful `@SequenceAgent`. With a single annotation, Quarkus wires together multiple specialized agents:

```java
@RegisterAiService
public interface AutoDraftCriticAgent {
    @SequenceAgent(
        outputKey = "draft", 
        subAgents = { DrafterAgent.class, CriticAgent.class, CriticEditorAgent.class }
    )
    NewsletterDraft write(@MemoryId String memoryId, @V("request") NewsletterRequest request);
}
```

---

## 🔁 The Quarkus Flow Definition

The Serverless Workflow is orchestrated using the fluent `FuncWorkflowBuilder`. Notice how business logic is completely decoupled from AI prompt engineering:

```java
public Workflow descriptor() {
    return FuncWorkflowBuilder
        .workflow("intelligent-newsletter")
        .tasks(
            // 1. Run the LangChain4j Agent Sequence
            agent("draftAgent", draftAgent::write, NewsletterRequest.class),
            
            // 2. Ask Human for Review via CloudEvent
            emitJson("draftReady", "org.acme.email.review.required", NewsletterDraft.class),
            listen("waitHumanReview", to().one(event("org.acme.newsletter.review.done")))
                    .outputAs((Collection<Object> c) -> c.iterator().next()),
            
            // 3. Conditional Routing based on Human Enum decision
            switchWhenOrElse(
                    h -> HumanReview.ReviewStatus.NEEDS_REVISION.equals(h.status()), 
                    "humanEditorAgent", 
                    "sendNewsletter", 
                    HumanReview.class)
                .andThen(a -> a.function("humanEditorAgent", fn(humanEditorAgent::edit, HumanReview.class)
                .andThen(f -> f.then("draftReady")))), // Loop back to review
            
            // 4. Terminate and Send
            consume("sendNewsletter", draft -> mailService.send("subscribers@acme", draft), NewsletterDraft.class)
                .inputFrom(input -> input.get("draft"), Map.class)
        )
        .build();
}
```

---

## 🛠 Troubleshooting

- **400 Bad Request on "Generate Draft" or "Approve"** Ensure the UI Dropdowns match the Java Enums exactly (e.g., `BULLISH`, `DONE`, `NEEDS_REVISION`). Jackson will reject unknown string values.

- **`OutputParsingException` in Server Logs** If the LLM generates a partial JSON response, it may have run out of tokens. Ensure `quarkus.langchain4j.ollama.chat-model.num-predict` in your `application.properties` is set high enough (e.g., `2048`) for long newsletters.

- **No live updates in UI** Check the browser console. Make sure the **WebSocket** is connected (`ws://localhost:8080/ws/newsletter`).

- **Kafka not starting** Verify Docker/Podman is running. Quarkus will log Dev Services startup. You can also point to an external Kafka in `application.properties`.

---

## 📚 Build your own

Use this repo as a template or copy the files you need. The `pom.xml` depends only on published artifacts—no coupling to this example. Extend the DSL, add new AI agents to the sequence, create new CloudEvents, or swap the UI framework. Have fun! 🎉