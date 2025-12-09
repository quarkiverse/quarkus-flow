# LangChain4j Agentic Workflow Example

This example shows how to use **Quarkus Flow** together with the **LangChain4j Agentic Workflow API**.

It demonstrates:

- Agentic workflows declared with annotations:
  - `@SequenceAgent` – a story creator workflow.
  - `@ParallelAgent` – an evening planner workflow.
- How `quarkus-flow-langchain4j` automatically turns these into **Quarkus Flow workflows**.
- How to **run and inspect** these workflows in the **Quarkus Flow Dev UI**:
  - Auto-generated input forms (from the agent method signatures).
  - Mermaid diagrams of the workflow topology.
  - Execution traces and output.

## Prerequisites

- JDK 17+
- Maven
- A configured LangChain4j provider (for example [Ollama](https://ollama.com/) running locally).

Make sure your `application.properties` (or `application.yml`) is pointing to a valid model
(e.g. `llama3.2` on Ollama).

## Running the example

From the root of the Quarkus Flow project:

```bash
cd examples/langchain4j-agentic-workflow
./mvnw quarkus:dev
```

Then open the Dev UI in your browser:

- URL: `http://localhost:8080/q/dev`
- Go to the **Quarkus Flow** card
- Look for workflows like:
  - `story-creator-with-configurable-style-editor`
  - `evening-planner-agent`

Click the **play** icon to:

- Fill in the generated form (for example `topic`, `style`, `audience`, `city`, `mood`).
- Execute the workflow and see the output.
- Explore the generated Mermaid diagram and task-level traces.

## Documentation

For full details on the integration between Quarkus Flow and LangChain4j, see:

- Quarkus Flow LangChain4j guide  
  👉 https://docs.quarkiverse.io/quarkus-flow/dev/langchain4j.html

- Concept: Agentic workflows with LangChain4j and Quarkus Flow  
  👉 https://docs.quarkiverse.io/quarkus-flow/dev/concept-agentic-langchain4j.html

- How-to: Use the LangChain4j Agentic Workflow API with Quarkus Flow  
  👉 https://docs.quarkiverse.io/quarkus-flow/dev/howto-langchain4j-agentic-workflows.html
