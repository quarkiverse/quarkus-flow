# HTTP Basic Auth + Secrets Demo (Quarkus Flow)

This example shows how to orchestrate **plain HTTP calls** from a workflow using the Quarkus Flow Java DSL, with:

* **Quarkus HTTP Basic auth** protecting a backend endpoint
* **File-based users/roles realm** (`users.properties` / `roles.properties`)
* **Secrets** resolved from `application.properties` and injected into the workflow
* A simple **REST API** that triggers the workflow and exposes its result

It’s meant as a small, focused playground for:

> “Call a secure HTTP endpoint from a workflow, using credentials resolved as secrets, and surface the result via REST.”

---

## What you’ll build

The story:

1. A **protected endpoint**: `GET /secure/profile`

    * Secured by Quarkus HTTP Basic auth
    * Returns a JSON “customer profile” for the authenticated user

2. A **workflow**: `secure-customer-profile`

    * Calls `GET /secure/profile` via HTTP
    * Uses **Basic auth** with credentials resolved from a **secret** (`demo.*`)

3. A **public API**: `GET /api/profile`

    * Starts the workflow
    * Returns the final workflow context as JSON

---

## Project layout

```text
examples/http-auth-basic/
├── src
│   ├── main
│   │   ├── java
│   │   │   └── org/acme/http
│   │   │       ├── CustomerProfileFlow.java           # Workflow definition
│   │   │       ├── SecureCustomerProfileResource.java # Protected endpoint (/secure/profile)
│   │   │       └── CustomerProfileResource.java       # Public API (/api/profile) using the workflow
│   │   └── resources
│   │       ├── application.properties                 # Quarkus config + demo.* “secret” entries
│   │       ├── users.properties                       # Basic-auth users
│   │       └── roles.properties                       # Roles per user
│   └── test
│       └── java
│           └── org/acme/http
│               └── CustomerProfileResourceTest.java   # RestAssured test
└── pom.xml
```

---

## The secured endpoint (`/secure/profile`)

This endpoint is protected by Quarkus Security and only accessible to authenticated users with the proper role.

### Configuration (`application.properties`)

```properties
# --- Quarkus HTTP Basic auth wired to users/roles files ---
quarkus.http.auth.basic=true

quarkus.security.users.file.enabled=true
quarkus.security.users.file.users=users.properties
quarkus.security.users.file.roles=roles.properties
quarkus.security.users.file.realm-name=quarkus-flow
quarkus.security.users.file.plain-text=true

# Protect /secure/*: must be authenticated
quarkus.http.auth.permission.secure.policy=authenticated
quarkus.http.auth.permission.secure.paths=/secure/*

# Demo service account credentials (used as "secrets" in the workflow)
demo.username=alice
demo.password=secret

# Base URL for the secured server
demo.server=http://localhost:${quarkus.http.port}

# HTTP client logging: handy for debugging
quarkus.flow.http.client.logging.scope=request-response
quarkus.flow.http.client.logging.body-limit=1024
quarkus.log.category."org.jboss.resteasy.reactive.client.logging".level=DEBUG
```

### Users / roles

`users.properties`:

```properties
alice=secret
```

`roles.properties`:

```properties
alice=user
```

### Secured resource

```java
package org.acme.http;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.Map;

@Path("/secure/profile")
@Produces(MediaType.APPLICATION_JSON)
public class SecureCustomerProfileResource {

    @GET
    @RolesAllowed("user")
    public Map<String, Object> profile() {
        // In a real app you'd look up the user from a DB / service
        return Map.of(
                "username", "alice",
                "fullName", "Alice Demo",
                "tier", "GOLD",
                "email", "alice@example.com"
        );
    }
}
```

If you `curl` this endpoint without credentials you’ll get `401`.
With `Basic alice:secret`, you’ll see the JSON profile.

---

## The workflow: `secure-customer-profile`

We use the **Java Fluent DSL** to define a workflow that calls the secure endpoint using Basic auth and secrets.

```java
package org.acme.http;

import java.net.URI;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import jakarta.enterprise.context.ApplicationScoped;

import static io.serverlessworkflow.fluent.func.FuncWorkflowBuilder.workflow;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.get;
import static io.serverlessworkflow.fluent.spec.dsl.DSL.basic;
import static io.serverlessworkflow.fluent.spec.dsl.DSL.secret;
import static io.serverlessworkflow.fluent.spec.dsl.DSL.use;

@ApplicationScoped
public class CustomerProfileFlow extends Flow {

    @ConfigProperty(name = "demo.server")
    String securedServer;

    @Override
    public Workflow descriptor() {
        URI endpoint = URI.create(securedServer + "/secure/profile");

        return workflow("secure-customer-profile")
                // Make "demo.*" properties available under $secret.demo.*
                .use(secret("demo"))
                .tasks(
                    // GET /secure/profile with Basic auth using secret-backed credentials
                    get(endpoint, basic(
                            "${ $secret.demo.username }",
                            "${ $secret.demo.password }"
                    ))
                )
                .build();
    }
}
```

Key points:

* `use(secret("demo"))` exposes `demo.*` config properties as `$secret.demo.*` in the workflow.
* `basic("${ $secret.demo.username }", "${ $secret.demo.password }")` configures HTTP Basic auth.
* `get(endpoint, ...)` is a plain HTTP call task provider; the response body becomes the workflow data.

---

## Public API: `/api/profile`

The public REST resource starts the workflow and returns the final workflow context as JSON.

```java
package org.acme.http;

import java.util.Map;
import io.smallrye.mutiny.Uni;

package org.acme.example;

import java.util.Map;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/profile")
@Produces(MediaType.APPLICATION_JSON)
public class CustomerProfileResource {

    @Inject
    CustomerProfileFlow customerProfileFlow;

    @GET
    public Uni<Map<String, Object>> getProfileViaWorkflow() {
        return customerProfileFlow.startInstance().onItem().transform(r -> r.asMap().orElseThrow());
    }
}
```

* The user doesn’t need to pass any input; the workflow uses the configured secrets.
* On success, the JSON profile from `/secure/profile` bubbles up as the response body from `/api/profile`.

---

## Testing with RestAssured

A simple integration test that hits the public endpoint and checks the JSON payload.

```java
package org.acme.http;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.Test;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class CustomerProfileResourceTest {

    @Test
    void profileEndpointShouldReturnProfileFromWorkflow() {
        given()
          .when().get("/api/profile")
          .then()
            .statusCode(200)
            .body("username", equalTo("alice"))
            .body("fullName", notNullValue())
            .body("tier", notNullValue());
    }
}
```

Run:

```bash
./mvnw test
```

---

## Running the example

From the example directory:

```bash
./mvnw quarkus:dev
```

Then call the public API:

```bash
curl http://localhost:8080/api/profile | jq
```

You should see the JSON profile returned by the workflow, which in turn called the **secured** `GET /secure/profile` endpoint using **Basic auth** credentials resolved from **secrets**.

---

## Learn more

* Quarkus Flow documentation:
  [https://docs.quarkiverse.io/quarkus-flow/dev/](https://docs.quarkiverse.io/quarkus-flow/dev/)
* Quarkus Security (Basic auth & file realm):
  [https://quarkus.io/guides/security-authentication](https://quarkus.io/guides/security-authentication)

