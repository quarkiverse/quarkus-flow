# HTTP OAuth2 + OIDC (Quarkus Flow)

This example shows how to call **OAuth2 / OIDC protected HTTP services** from a workflow using the
Quarkus Flow Java DSL. It covers the most common token scenarios you hit when orchestrating remote
APIs.

The example workflows live under `org.acme.http.workflows.oauth2`. In every scenario the workflow does
**not** have a token yet: Quarkus Flow negotiates one with an authorization server using an OAuth2 / OIDC
grant (Client Credentials, Password, Token Exchange, OIDC discovery), caches it, and then attaches
`Authorization: Bearer <token>` to the downstream call. Quarkus Flow is responsible for *obtaining* the
token.

Token negotiation is performed by the **`quarkus-flow-oidc`** extension, which delegates to
[`quarkus-oidc-client`](https://quarkus.io/guides/security-openid-connect-client-reference). You do not
depend on `quarkus-flow-oidc` directly — it is a conditional dependency of `quarkus-flow` and activates
automatically because `quarkus-oidc-client` is on the classpath.

All external services (authorization servers and downstream APIs) are **mocked with
[WireMock Dev Services](https://docs.quarkiverse.io/quarkus-wiremock/dev/)**, so the example runs
end-to-end with no real OAuth2 server.

> "Call OAuth2/OIDC protected HTTP and OpenAPI endpoints from a workflow, letting Quarkus Flow handle
> the token retrieval, and surface the result via REST."

---

## What you'll build

A REST API under `/quarkus-flow` that triggers one workflow per authentication scenario:

| Endpoint                                  | Workflow                  | Scenario                                                 |
|-------------------------------------------|---------------------------|----------------------------------------------------------|
| `GET /quarkus-flow/images`                | `client-credentials`      | OAuth2 **Client Credentials** grant on a plain HTTP call |
| `GET /quarkus-flow/oidc/images`           | `oidc-client`             | **OIDC** client (`oidc(...)`) with Client Credentials    |
| `GET /quarkus-flow/password/images`       | `grant-type-password`     | OAuth2 **Password** grant                                |
| `GET /quarkus-flow/token-exchange/images` | `token-exchange`          | OAuth2 **Token Exchange** grant                          |
| `GET /quarkus-flow/read-all-emails`       | `multiple-oauth2-clients` | **Multiple** OAuth2 clients/authorities in one workflow  |
| `GET /quarkus-flow/openapi/images`        | (OpenAPI)                 | **OpenAPI** operation secured with OAuth2/**OIDC**       |

---

## How a downstream call is authenticated

The workflow declares an OAuth2 / OIDC authentication and Quarkus Flow performs the token request
against the authorization server, caches the result, and attaches `Authorization: Bearer <token>`
automatically. The authentication can be attached **inline** on the call (`FlowDSL.oauth2(...)` /
`FlowDSL.oidc(...)`) or declared once under `use(...)` and referenced by name with `FlowDSL.use(...)`.

* `ClientCredentialsFlow` — Client Credentials grant attached inline.
* `OidcClientFlow` — same grant, but via the OIDC (`oidc(...)`) variant.
* `PasswordGrantTypeFlow` — Password grant.
* `TokenExchangeGrantTypeFlow` — Token Exchange grant (subject/actor tokens, audiences, scopes).
* `MultipleOAuth2ClientsFlow` — two authorities declared under `use(...)` and used in parallel.
* `OpenAPIWithOAuth2Flow` — an OpenAPI operation secured with an OAuth2/OIDC security scheme.

---

## Project layout

```text
examples/auth-oauth2-oidc/
├── src
│   ├── main
│   │   ├── java
│   │   │   └── org/acme/http
│   │   │       ├── api/FlowResource.java                       # REST API starting the workflows
│   │   │       └── workflows/oauth2                            # Quarkus Flow negotiates the token
│   │   │           ├── ClientCredentialsFlow.java             # Client Credentials grant (inline)
│   │   │           ├── OidcClientFlow.java                    # OIDC client (oidc(...))
│   │   │           ├── PasswordGrantTypeFlow.java             # Password grant
│   │   │           ├── TokenExchangeGrantTypeFlow.java        # Token Exchange grant
│   │   │           ├── MultipleOAuth2ClientsFlow.java         # Multiple clients via use(...)
│   │   │           └── OpenAPIWithOAuth2Flow.java             # OpenAPI operation with OAuth2/OIDC
│   │   └── resources
│   │       ├── application.properties                  # service + authorization-server URLs
│   │       ├── __files/openapi-oauth2.yaml             # OpenAPI document served by WireMock
│   │       └── mappings/                               # WireMock stubs (token + downstream services)
│   └── test
│       └── java
│           └── org/acme/http                           # RestAssured tests for each scenario
└── pom.xml
```

---

## Configuration (`application.properties`)

Client credentials are not embedded in the workflow code. Each flow declares the **secrets** it needs
with `use(u -> u.secrets("<name>"))` and then references the individual values through `${ $secret.* }`
JQ expressions. Secret values are plain config properties grouped under a prefix that matches the
secret name; authorization-server and downstream URLs are injected separately via `@ConfigProperty`.
All URLs point at the WireMock Dev Services port:

```properties
# WireMock loads stubs/files from src/main/resources (mappings/ and __files/)
quarkus.wiremock.devservices.files-mapping=src/main/resources
wiremock.url=http://localhost:${quarkus.wiremock.devservices.port}

image.service.url=http://localhost:${quarkus.wiremock.devservices.port}/image-service/images

# secret handler: clientCredentials (used by ClientCredentialsFlow)
clientCredentials.clientId=quarkus-flow
clientCredentials.clientSecret=dummy-client-secret
clientCredentials.baseUrl=http://localhost:${quarkus.wiremock.devservices.port}/auth/realms/test-realm/protocol/openid-connect/token

# used by OidcClientFlow
oidcClient.client-id=quarkus-flow
oidcClient.client-secret=dummy-client-secret
oidcClient.base-url=http://localhost:${quarkus.wiremock.devservices.port}/auth/realms/test-realm/protocol/openid-connect/token

# secret handler: password-grant (used by PasswordGrantTypeFlow)
password-grant.base-url=http://localhost:${quarkus.wiremock.devservices.port}/auth/realms/password-grant-type
password-grant.client-id=client-id
password-grant.client-secret=client-secret
password-grant.username=josh
password-grant.password=josh!password

# secret handler: exchangeSecrets (used by TokenExchangeGrantTypeFlow)
myRealm.base-url=http://localhost:${quarkus.wiremock.devservices.port}/auth/realms/my-realm
exchangeSecrets.subjectToken=UIZD1UK23MAGJRI6BI
exchangeSecrets.actorToken=FWM17KEW9302CRI9FQ

# used by MultipleOAuth2ClientsFlow (joogle + jahoo authorities)
joogle.clientId=quarkus-flow
joogle.clientSecret=joogle-client-secret
joogle.baseUrl=${wiremock.url}
jahoo.clientId=quarkus-flow
jahoo.clientSecret=jahoo-client-secret
jahoo.baseUrl=${wiremock.url}

# secret handler: openapi (used by OpenAPIWithOAuth2Flow)
openapi.client-id=quarkus-flow
openapi.client-secret=dummy-client-secret
openapi.base-url=http://localhost:${quarkus.wiremock.devservices.port}/auth/realms/test-realm/protocol/openid-connect/token
```

A secret name maps to a flat group of properties (e.g. `clientCredentials.*`), and the workflow reads
each value with a JQ path such as `${ $secret.clientCredentials.clientId }`. Keys containing a dash
must be quoted in the JQ path, e.g. `${ $secret.oidcClient."client-id" }`.

---

## 1. Client Credentials grant (`/quarkus-flow/images`)

The workflow asks the authorization server for a token using the **Client Credentials** grant and
uses it to call the image service. The OAuth2 configuration is attached **inline** to the HTTP call
via `FlowDSL.oauth2(...)`, and the client id/secret are resolved from secrets:

```java
@ApplicationScoped
public class ClientCredentialsFlow extends Flow {

    @ConfigProperty(name = "image.service.url")
    String imageService;

    @ConfigProperty(name = "clientCredentials.baseUrl")
    String baseUrl;

    @Override
    public Workflow descriptor() {
        return FlowWorkflowBuilder.workflow("client-credentials", "quarkus-flow")
                .use(u -> u.secrets("clientCredentials"))
                .tasks(
                        FlowDSL.call(
                                FlowDSL.http("listImages")
                                        .GET()
                                        .header("Accept", "application/json")
                                        .uri(URI.create(imageService),
                                                FlowDSL.oauth2(baseUrl, CLIENT_CREDENTIALS,
                                                        "${ $secret.clientCredentials.clientId }",
                                                        "${ $secret.clientCredentials.clientSecret }"))
                        ))
                .build();
    }
}
```

Quarkus Flow performs the token request against `base-url`, caches the result, and adds the
`Authorization: Bearer <token>` header to the downstream call automatically. The
`OidcClientFlow` is identical but swaps `FlowDSL.oauth2(...)` for `FlowDSL.oidc(...)`.

---

## 2. Multiple OAuth2 clients in one workflow (`/quarkus-flow/read-all-emails`)

When a workflow talks to several services, each with its own authorization server, declare the
authentications **once** under `use(...)` and reference them by name with `FlowDSL.use("name")`.
The two calls run in parallel via `fork`:

```java
return FlowWorkflowBuilder.workflow("multiple-oauth2-clients", "quarkus-flow")
        .use(use -> use.secrets("joogle", "jahoo")
                .authentications(auth -> {
                    auth.authentication("joogle", a -> a.oauth2(oauth2 ->
                            oauth2.endpoints(e -> e.token("/auth/joogle/token"))
                                  .client(c -> c.id("${ $secret.joogle.clientId }")
                                                .secret("${ $secret.joogle.clientSecret }")
                                                .authentication(CLIENT_SECRET_POST))
                                  .authority(joogleBaseUrl)
                                  .grant(CLIENT_CREDENTIALS)));
                    auth.authentication("jahoo", a -> a.oauth2(oauth2 ->
                            oauth2.endpoints(e -> e.token("/auth/jahoo/oidc/token"))
                                  .client(c -> c.id("${ $secret.jahoo.clientId }")
                                                .secret("${ $secret.jahoo.clientSecret }")
                                                .authentication(CLIENT_SECRET_POST))
                                  .authority(jahooBaseUrl)
                                  .grant(CLIENT_CREDENTIALS)));
                }))
        .tasks(
                FlowDSL.fork(
                        FlowDSL.http("getEmailsFromJoogle").GET()
                                .header("Accept", "application/json")
                                .uri(URI.create(wireMock + "/joogle/inbox"), FlowDSL.use("joogle")),
                        FlowDSL.http("getEmailsFromJahoo").GET()
                                .header("Accept", "application/json")
                                .uri(URI.create(wireMock + "/jahoo/inbox"), FlowDSL.use("jahoo"))),
                FlowDSL.function("merge", o -> { Log.info("Merging emails: " + o); return o; })
        )
        .build();
```

Each authentication can target a different token endpoint, authority, client authentication method
(`CLIENT_SECRET_POST` here), and grant type. The two clients' secrets are declared together with
`use.secrets("joogle", "jahoo")`.

---

## 3. Password grant (`/quarkus-flow/password/images`)

The **Resource Owner Password** grant uses the full `oauth2(...)` builder so the username and password
(also read from secrets) can be supplied alongside the client credentials:

```java
return FlowWorkflowBuilder.workflow("grant-type-password", "quarkus.flow")
        .use(u -> u.secrets("password-grant"))
        .tasks(
                FlowDSL.http()
                        .method("DELETE")
                        .uri(URI.create(imageService + "/attrs/dcb507bd-4dc4-46ba-a4ae-eb622b817d62"),
                                FlowDSL.oauth2(oauth2 -> oauth2
                                        .authority(baseUrl)
                                        .grant(PASSWORD)
                                        .client(c -> c.id("${ $secret.\"password-grant\".\"client-id\" }")
                                                      .secret("${ $secret.\"password-grant\".\"client-secret\" }"))
                                        .username("${ $secret.\"password-grant\".username }")
                                        .password("${ $secret.\"password-grant\".password }"))))
        .build();
```

---

## 4. Token Exchange grant (`/quarkus-flow/token-exchange/images`)

The **RFC 8693 Token Exchange** grant swaps a subject (and optional actor) token for a new one scoped
to a target audience. The builder exposes `subject(...)`, `actor(...)`, `scopes(...)` and
`audiences(...)`:

```java
return FlowWorkflowBuilder.workflow("token-exchange", "quarkus.flow")
        .use(use -> use.secrets("exchangeSecrets"))
        .tasks(
                FlowDSL.http()
                        .method("DELETE")
                        .uri(URI.create(imageService + "/attrs/dcb507bd-4dc4-46ba-a4ae-eb622b817d62"),
                                FlowDSL.oauth2(oauth2 -> oauth2
                                        .endpoints(token -> token.token("/oauth2/token"))
                                        .client(c -> c.id("my-client").secret("my-secret"))
                                        .subject(s -> s.token("${ $secret.exchangeSecrets.subjectToken }")
                                                       .type("urn:ietf:params:oauth:token-type:access_token"))
                                        .actor(a -> a.token("${ $secret.exchangeSecrets.actorToken }")
                                                     .type("urn:ietf:params:oauth:token-type:access_token"))
                                        .scopes("api")
                                        .audiences("target-service")
                                        .grant(URN_IETF_PARAMS_OAUTH_GRANT_TYPE_TOKEN_EXCHANGE)
                                        .authority(baseUrl))))
        .build();
```

---

## 5. OpenAPI operation with OAuth2/OIDC (`/quarkus-flow/openapi/images`)

Instead of a raw HTTP call, this workflow drives an **OpenAPI operation**. The OpenAPI document
(`__files/openapi-oauth2.yaml`, served by WireMock) declares an OAuth2/OIDC security scheme, and the
DSL supplies the credentials through `FlowDSL.oidc(...)`:

```java
return FlowWorkflowBuilder.workflow()
        .use(u -> u.secrets("openapi"))
        .tasks(t -> t.openapi("imageService", f ->
                // WireMock replaces the {{wireMockPort}} using response-template transformations
                f.document("http://localhost:" + wireMockPort + "/openapi/openapi-oauth2.yaml?wireMockPort=" + wireMockPort)
                 .operation("listImages")
                 .parameters(Map.of("Accept", "application/json"))
                 .authentication(FlowDSL.oidc(
                         baseUrl, CLIENT_CREDENTIALS,
                         "${ $secret.openapi.\"client-id\" }",
                         "${ $secret.openapi.\"client-secret\" }"))))
        .build();
```

`oidc(...)` mirrors `oauth2(...)` but is meant for OpenID Connect discovery / `openIdConnect`
security schemes.

---

## Mocked services (WireMock)

There is no real OAuth2 server in this example. WireMock Dev Services serves:

* **Token endpoints** — return a JSON `access_token` for the client-credentials requests
  (`mappings/client-credentials.json`, `mappings/joogle.json`, `mappings/jahoo.json`).
* **Downstream APIs** — the image service and the two email inboxes, each verifying the
  `Authorization` header (`mappings/list-images.json`, ...).
* **The OpenAPI document** — `__files/openapi-oauth2.yaml`, served via
  `mappings/openapi-provider.json` (the `{{wireMockPort}}` placeholder is replaced using WireMock
  response templating so URLs resolve to the random dev-services port).

Stubs and files are loaded from `src/main/resources` thanks to
`quarkus.wiremock.devservices.files-mapping=src/main/resources`.

---

## Testing with RestAssured

`AuthFlowResourceTest` exercises every scenario against the mocked services:

```java
@Test
@DisplayName("Should do a request using the access_token provided by the Authorization Server")
void should_list_images_authorized_with_client_credentials() {
    RestAssured.given().accept(ContentType.JSON)
            .get("/quarkus-flow/images")
            .then().statusCode(200);
}

@Test
@DisplayName("Should obtain a token via RFC 8693 token exchange (subject + actor) and call the downstream service")
void should_delete_image_with_token_exchange_grant() {
    RestAssured.given().accept(ContentType.JSON)
            .get("/quarkus-flow/token-exchange/images")
            .then().statusCode(200);
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

Quarkus starts WireMock Dev Services automatically. Then trigger the workflows:

```bash
# Client Credentials grant
curl http://localhost:8080/quarkus-flow/images | jq

# OIDC client (Client Credentials)
curl http://localhost:8080/quarkus-flow/oidc/images | jq

# Password grant
curl http://localhost:8080/quarkus-flow/password/images | jq

# Token Exchange grant
curl http://localhost:8080/quarkus-flow/token-exchange/images | jq

# Multiple OAuth2 clients
curl http://localhost:8080/quarkus-flow/read-all-emails | jq

# OpenAPI operation secured with OAuth2/OIDC
curl http://localhost:8080/quarkus-flow/openapi/images | jq
```

---

## Learn more

* Quarkus Flow documentation:
  [https://docs.quarkiverse.io/quarkus-flow/dev/](https://docs.quarkiverse.io/quarkus-flow/dev/)
* CNCF Serverless Workflow — Authentication:
  [https://github.com/serverlessworkflow/specification](https://github.com/serverlessworkflow/specification)
* Quarkus OIDC:
  [https://quarkus.io/guides/security-oidc-bearer-token-authentication](https://quarkus.io/guides/security-oidc-bearer-token-authentication)
* Quarkus WireMock Dev Services:
  [https://docs.quarkiverse.io/quarkus-wiremock/dev/](https://docs.quarkiverse.io/quarkus-wiremock/dev/)
</content>
</invoke>
