# Petstore Workflow (Quarkus + Quarkus Flow + OpenAPI)

A minimal example that calls a **public OpenAPI** at runtime and shows the result in a small web UI:

* **Quarkus** (hot-reload dev mode)
* **Quarkus Flow** (CNCF Workflow fluent Java DSL)
* **OpenAPI-based HTTP calls** (`openapi()` tasks)
* A tiny **web UI** that displays a random pet in a table

> The workflow reads the Petstore OpenAPI document, calls two operations, and exposes the final Pet JSON to the page.

---

## Quick Start

### Prerequisites

* Java 17+ and Maven
* Outbound internet access (the example talks to the public Petstore API)
* (Optional) A browser with dev tools enabled for inspecting JSON responses

### 1) Run the app

```bash
# from this example directory
mvn clean quarkus:dev
```

Open in your browser:

* **[http://localhost:8080](http://localhost:8080)**

> Quarkus dev mode gives you live reload for Java, resources, and the static UI.

---

## What you’ll see

* A **“Get me a pet”** button.
* When you click it:

    * The backend starts the `PetstoreFlow` workflow.
    * The workflow calls **Petstore** via `openapi()` tasks:

        * `findPetsByStatus(status="sold")`
        * `getPetById(petId=<id of first result>)`
    * The final JSON map is returned as a REST response.
* The UI renders the pet as a simple, readable table (id, name, status, category, tags).

If Petstore is temporarily down, you’ll see an error banner instead of a table.

---

## Architecture (high level)

```text
+-----------------+         REST          +---------------------------+
| Browser UI      |  GET /api/pets/random | Quarkus API (JAX-RS)     |
| (index.html)    +---------------------->+ calls PetstoreFlow       |
+-----------------+                       +------------+-------------+
                                                       |
                                                       | start()
                                                       v
                                         +-----------------------------+
                                         | PetstoreFlow (Func DSL)     |
                                         |   1) openapi().operation(..)|
                                         |   2) openapi().operation(..)|
                                         +-----------------------------+
                                                       |
                                                       v
                                           Public Petstore API
                                    https://petstore.swagger.io/v2/...
```

---

## The Workflow (visual)

Conceptually, the workflow looks like this:

```text
start
  │
  ▼
[openapi "findPetsByStatus" (status="sold")]
  │  outputAs("{ selectedPetId: .[0].id }")
  ▼
[openapi "getPetById" (petId = ${.selectedPetId})]
  │
  ▼
[end with full pet JSON in the workflow data]
```

The Java DSL version:

```java
@ApplicationScoped
public class PetstoreFlow extends Flow {

    @Override
    public Workflow descriptor() {
        final URI petstoreUri = URI.create("openapi/petstore.json");

        return FuncWorkflowBuilder
                .workflow("petstore")
                .tasks(
                        // 1) Find sold pets
                        openapi()
                                .document(petstoreUri)
                                .operation("findPetsByStatus")
                                .parameter("status", "sold")
                                .outputAs("${ { selectedPetId: .[0].id } }") ,
                        // 2) Fetch full details for one pet
                        openapi()
                                .document(petstoreUri)
                                .operation("getPetById")
                                .parameter("petId", "${.selectedPetId}")
                )
                .build();
    }
}
```

Key points:

* `document(URI)` points to `classpath://openapi/petstore.json`, which describes the Petstore API.
* `operation("findPetsByStatus")` and `operation("getPetById")` reference the OpenAPI `operationId` fields.
* `outputAs(...)` and `parameter(..., "${...}")` use JQ-style expressions over the workflow data.

---

## HTTP & UI

### REST Endpoint

The REST resource wires HTTP to the workflow:

```java
@Path("/pet")
@Produces(MediaType.APPLICATION_JSON)
public class PetstoreResource {

    @Inject
    PetstoreFlow petstoreFlow;

    @GET
    @Path("/random")
    public Map<String, Object> getPet() throws Exception {
        return petstoreFlow
                .instance(Map.of())
                .start()
                .get()
                .asMap()
                .orElseThrow();
    }
}
```

* `PetstoreFlow#instance(Map.of()).start().get()` starts a new workflow instance and waits for it to finish.
* `asMap()` returns the final workflow data as `Map<String, Object>` — perfect for JSON responses.

### UI Page

`src/main/resources/META-INF/resources/index.html` is a simple static page:

* A **button** that calls `GET /pet` via `fetch`.
* A **table** that renders the returned JSON (`id`, `name`, `status`, `category`, `tags`, etc.).
* A small error banner if the call fails.

---

## Testing & Hot-Reload

There is a Quarkus test that exercises the workflow end-to-end:

```java
@QuarkusTest
class PetstoreFlowIT {

    @Inject
    PetstoreFlow petstoreFlow;

    @Test
    void petstoreWorkflowShouldReturnPetDetails() throws Exception {
        Map<String, Object> pet =
                petstoreFlow.instance(Map.of())
                            .start()
                            .get()
                            .asMap()
                            .orElseThrow();

        assertThat(pet).isNotNull().isNotEmpty();
        assertThat(pet.get("id")).isNotNull();
    }
}
```

Run it with:

```bash
mvn test -Dtest=PetstoreFlowIT
```

⚠️ Because this example depends on the public Petstore service, it’s usually **not** ideal to run in strict CI pipelines (network flakiness, rate limits, etc.). Use it primarily for local experimentation.

---

## Debugging HTTP / OpenAPI Calls

To inspect the raw HTTP traffic between Quarkus Flow and the Petstore API, enable REST client logging:

```properties
# Log both request and response
quarkus.flow.http.client.logging.scope=request-response
quarkus.flow.http.client.logging.body-limit=1024

# Enable DEBUG logging for the RESTEasy Reactive client logger
quarkus.log.category."org.jboss.resteasy.reactive.client.logging".level=DEBUG
```

Then re-run in dev mode and watch the console: you’ll see request paths, status codes, and truncated bodies.

---

## Learn More

This example is intentionally small so you can use it as a starting point for your own OpenAPI workflows.

* Quarkus Flow docs:
  [https://docs.quarkiverse.io/quarkus-flow/dev/](https://docs.quarkiverse.io/quarkus-flow/dev/)
* Java DSL cheatsheet (FuncDSL): see **“Java DSL cheatsheet”** in the docs.
* HTTP / OpenAPI tasks: see the **HTTP & OpenAPI** section in the Quarkus Flow documentation.

You can:

* Swap the Petstore URL for your own OpenAPI document.
* Add more `openapi()` steps (or plain `http()` calls) in the workflow.
* Enrich the UI with filters, more fields, or multiple pets.

Have fun exploring OpenAPI workflows with Quarkus Flow!
