# Quarkus Flow :: OIDC

Delegates OAuth2 / OIDC **token negotiation** in Quarkus Flow workflows to
[`quarkus-oidc-client`](https://quarkus.io/guides/security-openid-connect-client-reference).

When a workflow call declares an OAuth2/OIDC authentication — inline on the call
(`FuncDSL.oauth2(...)` / `FuncDSL.oidc(...)`) or once under
`use(use -> use.authentications("name", auth -> auth.oauth2(...)))` and referenced by name — this
extension obtains the access token through a Quarkus `OidcClient` and lets the Serverless Workflow
engine attach it as `Authorization: Bearer <token>` to the downstream HTTP/OpenAPI call. Supported
grants: **client credentials**, **password**, **refresh** and **token exchange** (the per-execution
subject/actor tokens are resolved from the workflow context and passed as dynamic grant parameters).

## Activation

The extension is **conditional**: `quarkus-flow` declares `quarkus-flow-oidc` as a conditional
dependency (the same pattern as `quarkus-flow-messaging`), so you do **not** add it explicitly. It is
pulled in and activated automatically as soon as `io.quarkus:quarkus-oidc-client` is on the classpath:

```xml
<dependency>
  <groupId>io.quarkus</groupId>
  <artifactId>quarkus-oidc-client</artifactId>
</dependency>
```

When active, a `WorkflowApplicationBuilderCustomizer` installs an `AuthProviderFactory` backed by
`OidcClient`. Basic, bearer and digest authentication — and OAuth2/OIDC policies the extension does not
yet handle — fall back to the Serverless Workflow SDK's default provider.

## Configuration

| Property | Default | Description |
|---|---|---|
| `quarkus.flow.oidc.enabled` | `true` | Delegate OAuth2/OIDC token negotiation to `quarkus-oidc-client`. When `false`, the SDK's own token negotiation is used. |
| `quarkus.flow.oidc.request-timeout` | `10s` | Maximum time to wait for an access token from the authorization server. |

## Notes

- The token endpoint is always taken from the policy; OIDC discovery
  (`.well-known/openid-configuration`) is **not** used. For an `oidc(...)` policy the `authority`
  *is* the token endpoint; for an `oauth2(...)` policy the endpoint is `authority` plus the token path
  (default `/oauth2/token`, or the value of
  `oauth2(authority, grant, id, secret, endpoints -> endpoints.token("/realm/token"))`).
- `OidcClient` instances are built lazily and cached per resolved authentication policy. The cache key
  covers every value baked into the client — authority, token endpoint, the OAuth2-vs-OIDC flag,
  client id, client-authentication method, grant, scopes, audiences and the credential material — so
  call sites reuse one client (and its token cache/refresh) only when all of those match.
