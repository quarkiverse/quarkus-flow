# ADR: Kubernetes ServiceAccount Authentication for the Flow Runner

- **Date:** 2026-11-25
- **Status:** Proposed
- **Issue:** https://github.com/quarkiverse/quarkus-flow/issues/1160

## Context

Internal Kubernetes services such as the Logic Operator need to query Runner metadata
(definitions and instances) without storing a static API key or receiving the broader
`flow-invoker` role. Today the operator's runtime registration probe assumes
`quarkus.flow.runner.security.type=NONE`, i.e. an unauthenticated internal endpoint.

The Runner already supports three exclusive security modes (`NONE`, `API_KEY`, `OIDC`)
selected via `quarkus.flow.runner.security.type`, plus namespace-level ABAC
(`NamespaceAuthorizationService`) enforced by `NamespaceAuthorizationFilter`. All
resources are annotated class-level `@RolesAllowed({flow-admin, flow-invoker})`.

In a Kubernetes deployment, however, every pod already carries a cryptographic
identity: its ServiceAccount token. Services in the cluster should be able to
authenticate to the Runner with that token, **composably** — an external user calling
the same endpoint with an OIDC JWT must keep flowing through OIDC exactly as today.

## Decision

Add a composable Kubernetes ServiceAccount authentication mechanism to the Runner,
auto-activated only when running in-cluster, validating presented tokens via the
Kubernetes `TokenReview` API, and granting a new read-only `flow-service-reader` role.

### 1. Composable mechanism, not a security mode

Kubernetes auth is **not** a new `Security.Type` value. It is an additional
`HttpAuthenticationMechanism` that engages only when:

- the Runner runs in-cluster (presence of `KUBERNETES_SERVICE_HOST` and a readable
  `/var/run/secrets/kubernetes.io/serviceaccount/token`), and
- it is not explicitly disabled
  (`quarkus.flow.runner.security.service.kubernetes.enabled=false`).

The bean is always registered but deactivates at runtime when not in-cluster (or
when disabled), so it abstains on every request — effectively zero overhead and
zero behavior change for non-Kubernetes deployments. The mechanism is compatible with
any configured `type`: an external user's OIDC JWT continues through OIDC; a cluster
service's SA token is handled by the Kubernetes path.

### 2. Token routing and validation

- **Routing (unverified):** the mechanism peeks at the JWT payload *without
  verification*. Only tokens carrying Kubernetes ServiceAccount claims
  (`kubernetes.io/serviceaccount`) are engaged; all others are ignored (abstain), so
  OIDC/API_KEY mechanisms proceed without delay. The unverified peek is used strictly
  to route, never to trust.
- **Validation (authoritative):** engaged tokens are submitted to the kube-apiserver
  via `POST /apis/authentication.k8s.io/v1/tokenreviews`, authenticated as the Runner
  itself using its own pod-mounted SA credentials (standard in-cluster client config).
  The API server performs cryptographic verification, expiry checks, and identity
  extraction.
- **API access via fabric8 (`quarkus-kubernetes-client`):** the TokenReview call is
  made through the fabric8 Kubernetes client provided by the Quarkus extension
  (`KubernetesClient.tokenReviews()`), not a hand-rolled HTTP client. The extension
  resolves in-cluster configuration (API server URL, CA certificate, SA token with
  bound-token rotation) and is native-image supported. `TokenReviewClient` is a thin
  adapter: it builds a `TokenReview` resource, creates it, and maps the verified
  response. Requests carry a timeout and the blocking call runs off the event loop
  (see Consequences).
- **Dependency weight (evidence-based outcome):** fabric8 transitively pulls a
  large set of `kubernetes-model-*` artifacts, most of which the Runner never
  touches. Individual model-jar exclusions were attempted first and abandoned —
  fabric8's `KubernetesClientImpl` eagerly constructs API-group clients at build
  time, and excluding `kubernetes-model-batch` alone fails client creation with
  `NoClassDefFoundError`. The working approach follows the Quarkus Kubernetes
  Client guide ("depend only on the modules you need"): all `io.fabric8`
  transitives are wildcard-excluded from `quarkus-kubernetes-client` and
  `kubernetes-client-api`, and only the hand-picked modules the TokenReview
  path needs are re-added — `kubernetes-client-api`, `kubernetes-client`
  (impl), `kubernetes-model-core`, `kubernetes-model-common`,
  `kubernetes-model-admissionregistration` (hosts
  `io.fabric8.kubernetes.api.model.authentication.TokenReview`), the group
  models `KubernetesClientImpl`'s default handler registration eagerly
  references (`apps`, `batch`, `certificates`, `extensions`), and
  `kubernetes-httpclient-vertx` (the HTTP transport the extension normally
  provides) — roughly half of fabric8's default transitive set. All versions
  remain quarkus-bom-managed. The fabric8 7.9.0 generated-class
  code-injection advisory is acknowledged and accepted as low-risk here — the
  Runner processes no CRD schemas — pending the Quarkus BOM picking up the
  fixed version.
- **Per-module closure ownership (lesson from the kind CI):** a wildcard
  exclusion in one library module resolves nearest for every downstream
  consumer and can silently strip jars another module needs — the runner
  images lost `kubernetes-model-coordination` and
  `kubernetes-model-autoscaling` this way, killing the durable-kubernetes
  member-lease acquisition with an invisible `NoClassDefFoundError`. Every
  module using the fabric8 client now declares its own hand-picked module
  set (durable-kubernetes includes `coordination` and `autoscaling` for its
  lease paths), images resolve the union of the declared sets, and each
  module's tests guard its own closure.
- **Audiences:** no audience restriction is configured by default. Pods using the
  default SA token mounted at `/var/run/secrets/kubernetes.io/serviceaccount/token`
  carry the API server's own audience, which `TokenReview` accepts without
  `spec.audiences`. A stricter `quarkus.flow.runner.security.kubernetes.audience`
  option may be added later if purpose-scoped tokens (via `TokenRequest`) become a
  requirement; it is a non-goal for v1.
- **Caching:** TokenReview results (positive and negative) are cached with a short
  TTL keyed on a hash of the token, bounded by the token's expiry, so per-request
  traffic does not translate into per-request API-server calls and invalid tokens do
  not hammer the API server. The cache is bounded (maximum number of entries, e.g.
  an LRU with a configurable `cache-max-entries`, default on the order of a few
  hundred) so a burst of distinct tokens cannot exhaust Runner memory; evicted or
  expired entries simply trigger a fresh TokenReview.

The verified identity comes from the TokenReview response
(`status.authenticated`, `status.user.username` of the form
`system:serviceaccount:<namespace>:<name>`), never from the unverified token claims.

### 3. Trust model

Trust-by-default, scoped by namespace:

- **Any valid SA token** (authenticated by TokenReview) receives the
  `flow-service-reader` role and is authorized for **only its own namespace** (the
  namespace in the verified TokenReview username).
- **Trusted service accounts**, listed in:

  ```properties
  quarkus.flow.runner.security.service.kubernetes.trusted-service-accounts=system:serviceaccount:infrans:gateway
  ```

  receive the same role but are authorized for **all namespaces** (`*`). This covers
  gateway/operator-style services that must read cluster-wide workflow metadata.

Rationale: workflow metadata read from inside the cluster is considered safe from the
platform's point of view, so valid cluster identities are trusted by default. The
allowlist exists to grant cross-namespace breadth, not to gate entry. A future
iteration may tighten this to an explicit whitelist per the issue's stricter reading;
that is a config-only change and is intentionally deferred.

### 4. The `flow-service-reader` role

A new role in `AuthzConsts`, granted exclusively by the Kubernetes mechanism:

- Allowed on **GET methods only** of the definitions, instances, and discovery
  endpoints (`GET /q/flow/definitions`, definition lookup, `GET /q/flow/instances`,
  per-workflow instance endpoints, discovery).
- Denied workflow execution, instance lifecycle actions
  (suspend/resume/cancel/retry), and every mutating operation.
- Does **not** bypass namespace ABAC (only `flow-admin` does). A non-trusted SA
  reading another namespace's data receives 403 even for GETs.

Class-level `@RolesAllowed({flow-admin, flow-invoker})` annotations on
`DefinitionResource`, `InstancesResource`, and `DiscoveryResource` are refined to
method-level so read methods additionally accept `flow-service-reader`.
`RunnerExecResource` and all mutating/lifecycle endpoints remain
admin/invoker-only and are untouched.

### 5. Namespace integration

The Kubernetes mechanism builds its `SecurityIdentity` with the standard
`namespaces` attribute (`*` for trusted SAs, the SA's own namespace otherwise).
`NamespaceAuthorizationService` already reads that attribute first, so the existing
ABAC layer applies unchanged.

### 6. Client story

A Go client (e.g. the operator's gateway) uses the standard client-go in-cluster
pattern:

- read the pod's SA token (`rest.InClusterConfig().BearerToken`, or a
  `TokenRequest`-minted short-lived token),
- send it as `Authorization: Bearer <token>` to the Runner's read endpoints.

No secret sharing, no API key distribution, no OIDC client credentials in the
operator.

## Configuration namespace

All properties for this mechanism live under
`quarkus.flow.runner.security.service.kubernetes.*` (e.g. `enabled`,
`trusted-service-accounts`, `cache-max-entries`). The `service` segment marks it as a
service-to-service mechanism layered on top of the configured `security.type`, and reserves
the `quarkus.flow.runner.security.kubernetes.*` namespace for a potential future
exclusive `KUBERNETES` security type without forcing a breaking property rename.

## Required Kubernetes setup (to be documented)

- The **Runner's** ServiceAccount must be allowed to create TokenReviews:
  bind `system:auth-delegator` (or an equivalent rule on
  `authentication.k8s.io/tokenreviews`) to the Runner's SA.
- No setup is required on the client side beyond running as a pod with a SA.

## Consequences

- In-cluster Runners gain service-to-service authentication with no client-side
  secret management; the Logic Operator can drop its `NONE` assumption.
- The kube-apiserver becomes a dependency for authenticating SA tokens (mitigated by
  result caching and a bounded request timeout); if the API server is unreachable or
  slow, SA-token requests fail closed with 401 while other auth paths are unaffected.
- The runner gains a runtime dependency on `quarkus-kubernetes-client` (fabric8).
  This departs from the original "no new runtime dependencies" constraint, accepted
  because the extension already backs the `durable-kubernetes` module in this
  repository and replaces fragile hand-rolled in-cluster TLS/token plumbing with
  maintained, native-image-supported machinery. Transitive `kubernetes-model-*`
  artifacts unused by the TokenReview path are excluded at the POM level to keep
  runner images thin (see API access, above); both modules share the extension's
  single client and configuration, which is the desired behavior (same cluster).
- Any valid SA token in the cluster can read workflow metadata in its own namespace —
  an accepted trade-off for v1 (platform-level trust), revisitable via an explicit
  whitelist without breaking the mechanism.
- Existing `NONE`, `API_KEY`, and `OIDC` behavior is unchanged: the mechanism
  abstains on any token that is not SA-shaped and deactivates at runtime
  off-cluster (or when disabled).

## Alternatives considered

- **New `Security.Type.KUBERNETES` mode** — rejected: security modes are exclusive;
  a cluster deployment serving both external OIDC users and internal SA services
  could not use both.
- **Local JWT validation via `quarkus-oidc`** against the kube-apiserver's OIDC
  discovery endpoints — rejected for v1: requires the `quarkus-oidc` extension in the
  host app (currently optional), is sensitive to `service-account-issuer`
  configuration, and adds issuer/JWKS reachability concerns. TokenReview delegates
  all of that to the API server. May be revisited if per-request API-server latency
  ever matters.
- **Hand-rolled JDK `HttpClient` (initial implementation)** — implemented first to
  avoid new dependencies, then replaced by fabric8: it required manually building the
  in-cluster `SSLContext` from the mounted CA certificate, reading the rotating SA
  token file per request, and hand-parsing the TokenReview JSON, with no
  native-image guarantee. fabric8 already backs `durable-kubernetes` and solves all
  of this in maintained code.
- **Quarkus/MicroProfile REST Client** — rejected: it is designed for typed
  third-party APIs with static configuration; the in-cluster trust setup (PEM CA →
  truststore, rotating token injection) would remain as manual as the hand-rolled
  client, and it adds a runtime dependency without removing any of the plumbing.
- **Explicit SA whitelist as the default trust model** — rejected for v1 in favor of
  trust-by-default with namespace scoping (see Trust model); the whitelist exists
  only to grant cross-namespace breadth.

## Testing strategy

- Unit tests: token routing (SA-shaped vs not), identity mapping, trusted-vs-scoped
  namespace resolution, cache behavior.
- Integration tests: mocked kube-apiserver (WireMock serving TokenReview responses,
  or `quarkus-test-kubernetes-client`'s mock server)
  — valid/expired/garbage tokens; role enforcement across all endpoints (reader
  allowed on GETs, denied on exec/lifecycle/mutations); namespace scoping (403 for
  foreign namespace, `*` for trusted); coexistence with API_KEY and OIDC modes
  (external tokens unaffected); mechanism deactivated off-cluster.
- No real cluster interaction in CI; mocked LLM/API rules apply as usual.

## Documentation

- Runner security page: new section on Kubernetes ServiceAccount authentication,
  configuration properties, required `system:auth-delegator` RBAC for the Runner's
  SA, and the client-side usage pattern.
- Config javadoc on `FlowRunnerConfig` mirroring the new properties.
