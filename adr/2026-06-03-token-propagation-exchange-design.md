# Token Propagation and Exchange for Quarkus Flow

**Date**: 2026-06-08  
**Status**: Proposed  
**Context**: ADR for implementing token propagation and exchange feature  

## Context

Quarkus Flow needs token propagation and exchange capabilities for authenticating workflow tasks against external services, similar to SonataFlow's implementation documented at https://sonataflow.org/serverlessworkflow/main/security/token-exchange-for-openapi-services.html.

This feature addresses:
- **Long-running workflows** where tokens may expire before workflow completion
- **Security isolation** to avoid forwarding original user tokens to third-party services
- **Multi-tenant scenarios** requiring different tokens/scopes per downstream service
- **Service-to-service authentication** using OAuth2 client credentials

### Key Requirements

1. Support both **token propagation** (forward original token) and **token exchange** (swap for service-specific token)
2. Work with **Serverless Workflow 1.0.0 specification** OAuth2 definitions
3. Integrate seamlessly with **OpenAPI** task calls
4. Support **multiple OIDC servers** (named OIDC clients per auth scheme)
5. Implement **token caching** with proper lifecycle management
6. Provide **proactive token refresh** to prevent expiration during workflow execution
7. Leverage **existing persistence modules** (JPA, Redis, Infinispan, MVStore) for Phase 2

### Security Boundaries

**Critical distinction**: Application security vs. task authentication are separate concerns:

- **Application Security** (Quarkus OIDC/Security): Authenticates/authorizes the workflow caller - "who can start/manage workflows?"
- **Task Authentication** (Token Propagation/Exchange): Authenticates workflow tasks calling external services - "how does the workflow authenticate to third-party APIs?"

These layers are independent:
```
User → [App Security: Is user allowed?] → Workflow Engine → [Task Auth: How to call external service?] → Third-party API
```

## Decision

Implement a **hybrid approach** using Serverless Workflow 1.0.0 OAuth2 definitions with Quarkus-specific authentication provider layer and smart defaults.

### Phased Implementation

**Phase 0** (Validation/Proof of Concept):
- Validate current architecture integrates with `quarkus-oidc-client`
- Build working examples of OAuth2/OIDC authentication against third-party services
- Test OAuth2 client credentials flow (service-to-service, no user token)
- Test OAuth2 with user context (manual token passing via workflow input)
- Identify gaps/issues in current SDK OAuth2/OIDC handling
- Document findings and integration patterns
- **Contingency**: If basic OIDC support missing, add to `core` (conditionally activated when `quarkus-oidc-client` present)
- **Deliverable**: Working examples + integration assessment before proceeding to Phase 1

**Phase 1** (Token Propagation & Exchange):
- Token propagation (forward user tokens)
- Token exchange with RFC 8693
- In-memory caching
- Proactive token refresh
- Named OIDC client support
- Subject token extraction (SecurityIdentity, input, expression)
- **Note**: Builds on basic OIDC support in `core` (from Phase 0 or pre-existing)

**Phase 2** (Persistence):
- Persistent token caching via separate optional modules
- JPA, Redis, Infinispan, MVStore implementations
- Enhanced proactive refresh with encrypted subject token storage
- Performance testing and optimization

## Architecture Overview

### High-Level Flow

```
User → [Quarkus Security] → Workflow Engine
                               ↓
                          Extract subject token
                          (from SecurityIdentity or input)
                               ↓
                          Task with OAuth2 auth
                               ↓
                     [AuthenticationProvider]
                     ↓                    ↓
            TokenPropagation      TokenExchange
            (forward token)       (swap token + cache)
                     ↓                    ↓
                          External Service
```

### Core Components

1. **AuthenticationProvider** - Interface for authentication strategies
2. **TokenPropagationProvider** - Forwards user token as-is
3. **TokenExchangeProvider** - Performs RFC 8693 token exchange with caching
4. **ClientCredentialsProvider** - Service-to-service auth (no user context)
5. **TokenCacheRepository** - Abstraction for token storage (in-memory Phase 1, persistent Phase 2)
6. **AuthenticationRegistry** - Routes to appropriate provider
7. **SubjectTokenExtractor** - Extracts user token from SecurityIdentity/input/expression
8. **AuthenticationRequestDecorator** - ServiceLoader-registered HTTP request decorator
9. **OidcClientProvider** - Resolves named OIDC clients per auth scheme
10. **TokenRefreshMonitor** - Background proactive token refresh

## Detailed Design

### 1. OpenAPI Integration

**Integration Point**: SDK's `HttpRequestDecorator` (ServiceLoader)

The Serverless Workflow SDK's `HttpExecutor` loads `HttpRequestDecorator` implementations via ServiceLoader, allowing us to intercept HTTP requests before execution.

**Authentication Resolution Flow**:
```
Workflow DSL (oauth2(...))
    ↓
SDK parses & creates OAuth2AuthenticationData
    ↓
Available in TaskContext.task().endpoint().authentication()
    ↓
Our AuthenticationRequestDecorator reads it
    ↓
Apply token (propagate or exchange)
```

**Implementation**:

```java
public class AuthenticationRequestDecorator implements HttpRequestDecorator {
    
    private final AuthenticationRegistry registry;
    
    @Override
    public void decorate(
        Invocation.Builder request, 
        WorkflowContext workflow, 
        TaskContext task
    ) {
        Optional<Authentication> auth = extractAuthentication(task);
        
        auth.flatMap(a -> registry.authenticate(workflow, task, a))
            .ifPresent(token -> request.header("Authorization", token));
    }
    
    @Override
    public int priority() {
        return 100; // After metadata propagation (50), before custom decorators (1000+)
    }
}
```

**Priority Ordering**:
- `MetadataPropagationRequestDecorator`: 50 (runs first, adds correlation headers)
- `AuthenticationRequestDecorator`: 100 (adds Authorization header)
- Custom decorators: 1000+ (user extensions)

**OpenAPI Specifics**:
- OpenAPI specs declare `securitySchemes` → SDK maps to `Authentication` objects
- We consume already-parsed `Authentication` from `TaskContext`
- No need to parse OpenAPI specs ourselves
- Works for both inline DSL auth and OpenAPI-declared schemes

### 2. AuthenticationProvider Interface

Central abstraction for all authentication strategies:

```java
public interface AuthenticationProvider {
    
    /**
     * Authenticate and return the token to use in Authorization header.
     * 
     * @param workflowContext Current workflow execution context
     * @param taskContext Current task context (contains auth config)
     * @param authentication The authentication definition from the workflow DSL
     * @return Token string (e.g., "Bearer eyJ...") or empty if no auth needed
     */
    Optional<String> authenticate(
        WorkflowContext workflowContext, 
        TaskContext taskContext,
        Authentication authentication
    );
    
    /**
     * Check if this provider supports the given authentication type.
     */
    boolean supports(Authentication authentication);
}
```

**Provider Implementations**:

**TokenPropagationProvider**:
- Supports: OAuth2/Bearer/OIDC when propagation enabled via config
- Extracts subject token from `SubjectTokenExtractor`
- Returns token as-is with proper prefix (`Bearer ...`)
- No caching, no exchange

**TokenExchangeProvider**:
- Supports: OAuth2/OIDC with `subject` token defined OR exchange enabled via config
- Calls OIDC token endpoint with grant type `urn:ietf:params:oauth:grant-type:token-exchange`
- Caches exchanged tokens via `TokenCacheRepository`
- Implements proactive refresh logic

**ClientCredentialsProvider**:
- Supports: OAuth2 with `client_credentials` grant
- No subject token needed (service-to-service)
- Caches tokens until expiry
- Simpler than exchange (no user context)

### 3. AuthenticationRegistry

CDI bean that routes to the right provider:

```java
@ApplicationScoped
public class AuthenticationRegistry {
    
    @Inject
    Instance<AuthenticationProvider> providers;
    
    public Optional<String> authenticate(
        WorkflowContext workflow, 
        TaskContext task,
        Authentication auth
    ) {
        return providers.stream()
            .filter(p -> p.supports(auth))
            .findFirst()
            .flatMap(p -> p.authenticate(workflow, task, auth));
    }
}
```

CDI `Instance<>` provides dynamic provider discovery, making it easy to add new authentication strategies.

### 4. Subject Token Extraction

Extracts the user's token for propagation/exchange from multiple sources:

```java
@ApplicationScoped
public class SubjectTokenExtractor {
    
    @Inject
    Instance<SecurityIdentity> securityIdentity;
    
    @Inject
    FlowOidcConfig config;
    
    /**
     * Extract subject token from available sources.
     * Priority: expression-based (DSL) > explicit workflow input > SecurityIdentity
     */
    public Optional<String> extract(
        WorkflowContext workflow,
        Authentication auth
    ) {
        // 1. Check if auth definition has subject token (expression-based, DSL-driven)
        if (auth instanceof OAuth2Authentication oauth2) {
            if (oauth2.subject() != null && oauth2.subject().token() != null) {
                return Optional.of(oauth2.subject().token());
            }
        }
        
        // 2. Check workflow input for explicit token (programmatic workflows)
        String inputKey = config.subjectToken().inputKey(); // default: "subjectToken"
        Optional<String> inputToken = workflow.instance()
            .input()
            .get(inputKey)
            .map(Object::toString);
        if (inputToken.isPresent()) {
            return inputToken;
        }
        
        // 3. Extract from SecurityIdentity (HTTP-triggered workflows)
        if (securityIdentity.isResolvable()) {
            String attribute = config.subjectToken().securityIdentityAttribute();
            return securityIdentity.get()
                .getAttribute(attribute) // default: "access_token"
                .map(Object::toString);
        }
        
        return Optional.empty();
    }
}
```

**Token Sources** (in priority order):
1. **Expression-based (DSL)**: `oauth2(..., subject("${ $context.securityIdentity.token }"))`
   - Power-user control, explicit in workflow definition
2. **Explicit workflow input**: `instance(Map.of("subjectToken", "eyJ...")).start()`
   - Useful for non-HTTP triggers (scheduled, messaging, programmatic)
3. **SecurityIdentity (automatic)**: Auto-extracted when workflow triggered via REST
   - Requires Quarkus OIDC/JWT extension present

This design keeps task authentication separate from app security while allowing workflows to leverage authenticated user identity when needed.

### 5. Token Caching

#### 5.1 TokenCacheRepository Interface

Abstraction for token storage (Phase 1: in-memory, Phase 2: persistent):

```java
public interface TokenCacheRepository {
    
    /**
     * Store an exchanged token with metadata.
     */
    void store(CachedToken token);
    
    /**
     * Retrieve a valid token if exists.
     * Returns empty if token not found or expired.
     */
    Optional<CachedToken> get(TokenCacheKey key);
    
    /**
     * Remove token from cache.
     */
    void evict(TokenCacheKey key);
    
    /**
     * Get all tokens nearing expiration for proactive refresh.
     */
    Collection<CachedToken> getTokensNearingExpiry(Duration threshold);
    
    /**
     * Link a workflow instance to a token.
     */
    void linkInstance(String instanceId, TokenCacheKey tokenKey);
    
    /**
     * Unlink instance and cleanup orphaned tokens.
     */
    void unlinkInstance(String instanceId);
}
```

#### 5.2 Token Cache Key

Composite key for token lookup:

```java
public record TokenCacheKey(
    String authSchemeName,      // e.g., "my-api"
    String subjectTokenHash,    // SHA-256 hash of subject token
    String audience             // requested audience/scope
) {
    public static TokenCacheKey from(
        String authSchemeName,
        String subjectToken,
        Optional<String> audience
    ) {
        String hash = sha256(subjectToken);
        return new TokenCacheKey(
            authSchemeName, 
            hash, 
            audience.orElse("")
        );
    }
}
```

**Design decisions**:
- Hash subject token for privacy (not stored in plaintext)
- Composite key allows same user to have different tokens per service/audience
- Auth scheme name prevents collision across different services

#### 5.3 Cached Token

Token with metadata and lifecycle tracking:

```java
public record CachedToken(
    TokenCacheKey key,
    String exchangedToken,      // The actual token value
    Instant expiresAt,
    Instant createdAt,
    Set<String> linkedInstances // Workflow instances using this token
) {
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
    
    public boolean isNearingExpiry(Duration threshold) {
        return Instant.now()
            .plus(threshold)
            .isAfter(expiresAt);
    }
    
    public CachedToken withNewToken(String newToken, Instant newExpiry) {
        return new CachedToken(
            key,
            newToken,
            newExpiry,
            Instant.now(),
            linkedInstances
        );
    }
}
```

#### 5.4 In-Memory Implementation (Phase 1)

```java
@ApplicationScoped
public class InMemoryTokenCacheRepository implements TokenCacheRepository {
    
    private final ConcurrentHashMap<TokenCacheKey, CachedToken> tokenCache;
    private final ConcurrentHashMap<String, Set<TokenCacheKey>> instanceTokenLinks;
    
    @PostConstruct
    void init() {
        // Start background cleanup of expired tokens
        scheduler.scheduleAtFixedRate(this::cleanupExpired, 60, 60, SECONDS);
    }
    
    @Override
    public void linkInstance(String instanceId, TokenCacheKey tokenKey) {
        instanceTokenLinks
            .computeIfAbsent(instanceId, k -> ConcurrentHashMap.newKeySet())
            .add(tokenKey);
            
        // Add instance to token's linked set
        tokenCache.computeIfPresent(tokenKey, (k, token) -> 
            new CachedToken(
                token.key(),
                token.exchangedToken(),
                token.expiresAt(),
                token.createdAt(),
                addToSet(token.linkedInstances(), instanceId)
            )
        );
    }
    
    @Override
    public void unlinkInstance(String instanceId) {
        Set<TokenCacheKey> tokens = instanceTokenLinks.remove(instanceId);
        if (tokens == null) return;
        
        // Remove instance from each token's linked set
        for (TokenCacheKey key : tokens) {
            tokenCache.computeIfPresent(key, (k, token) -> {
                Set<String> updated = removeFromSet(token.linkedInstances(), instanceId);
                if (updated.isEmpty()) {
                    // Orphaned token, evict
                    return null;
                }
                return new CachedToken(
                    token.key(),
                    token.exchangedToken(),
                    token.expiresAt(),
                    token.createdAt(),
                    updated
                );
            });
        }
    }
    
    private void cleanupExpired() {
        tokenCache.entrySet()
            .removeIf(entry -> entry.getValue().isExpired());
    }
}
```

**Key features**:
- **Per-subject token sharing**: Same user's token reused across workflow instances
- **Junction table pattern**: `instanceTokenLinks` enables orphan detection
- **Automatic cleanup**: Expired tokens removed, orphaned tokens evicted
- **Thread-safe**: All operations use concurrent collections and atomic updates

### 6. OIDC Client Configuration

#### 6.1 Named OIDC Clients

Support multiple OIDC servers via named clients (similar to named HTTP clients):

```properties
# Named OIDC clients for token exchange
quarkus.oidc-client.auth-api-a.auth-server-url=https://idp-a.example.com/realms/acme
quarkus.oidc-client.auth-api-a.client-id=workflow-runtime
quarkus.oidc-client.auth-api-a.credentials.secret=secret-a
quarkus.oidc-client.auth-api-a.grant.type=urn:ietf:params:oauth:grant-type:token-exchange

quarkus.oidc-client.auth-api-b.auth-server-url=https://idp-b.example.com/realms/other
quarkus.oidc-client.auth-api-b.client-id=workflow-client
quarkus.oidc-client.auth-api-b.credentials.secret=secret-b
quarkus.oidc-client.auth-api-b.grant.type=urn:ietf:params:oauth:grant-type:token-exchange

# Map auth schemes to OIDC clients
quarkus.flow.oidc.auth.my-service-a.oidc-client-name=auth-api-a
quarkus.flow.oidc.auth.my-service-b.oidc-client-name=auth-api-b
```

#### 6.2 OIDC Client Resolution

```java
@ApplicationScoped
public class OidcClientProvider {
    
    @Inject
    OidcClients oidcClients; // Quarkus built-in
    
    @Inject
    FlowOidcConfig config;
    
    /**
     * Resolve OIDC client for an auth scheme.
     * Priority: explicit mapping > auth scheme name > default
     */
    public OidcClient resolve(String authSchemeName) {
        // 1. Check explicit mapping in config
        String clientName = config.auth()
            .get(authSchemeName)
            .flatMap(AuthSchemeConfig::oidcClientName)
            .orElse(authSchemeName); // 2. Fall back to auth scheme name
        
        // 3. Get named client from Quarkus OidcClients
        return oidcClients.getClient(clientName)
            .orElseThrow(() -> new IllegalStateException(
                "OIDC client not configured: " + clientName + 
                ". Configure quarkus.oidc-client." + clientName + ".*"));
    }
}
```

**Resolution Strategy**:
1. Check explicit mapping: `quarkus.flow.oidc.auth.<auth-name>.oidc-client-name`
2. Fall back to auth scheme name as client name
3. Fail fast if client not configured (clear error message)

**Benefits**:
- Each workflow task can target different OIDC servers
- Reuses Quarkus OIDC client infrastructure (retries, timeouts, circuit breakers)
- Convention over configuration: auth scheme name = OIDC client name by default
- Explicit override when needed

#### 6.3 Token Exchange Client

```java
@ApplicationScoped
public class TokenExchangeClient {
    
    @Inject
    OidcClientProvider oidcClientProvider;
    
    public Tokens exchange(
        String authSchemeName,
        String subjectToken,
        Optional<String> audience,
        Optional<List<String>> scopes
    ) {
        OidcClient client = oidcClientProvider.resolve(authSchemeName);
        
        // Build token exchange request per RFC 8693
        Map<String, String> params = new HashMap<>();
        params.put("grant_type", "urn:ietf:params:oauth:grant-type:token-exchange");
        params.put("subject_token", subjectToken);
        params.put("subject_token_type", "urn:ietf:params:oauth:token-type:access_token");
        
        audience.ifPresent(aud -> params.put("audience", aud));
        scopes.ifPresent(s -> params.put("scope", String.join(" ", s)));
        
        // Perform exchange via Quarkus OIDC client
        return client.getTokens(params).await().indefinitely();
    }
}
```

### 7. Configuration

#### 7.1 Configuration Structure

```properties
# Global token exchange settings
quarkus.flow.oidc.token-exchange.enabled=true
quarkus.flow.oidc.token-exchange.proactive-refresh-seconds=300
quarkus.flow.oidc.token-exchange.monitor-rate-seconds=60

# Subject token extraction
quarkus.flow.oidc.subject-token.input-key=subjectToken
quarkus.flow.oidc.subject-token.security-identity-attribute=access_token

# Per-auth-scheme configuration
quarkus.flow.oidc.auth.<auth-name>.oidc-client-name=<oidc-client-name>
quarkus.flow.oidc.auth.<auth-name>.token-exchange.enabled=true
quarkus.flow.oidc.auth.<auth-name>.token-exchange.proactive-refresh-seconds=180
quarkus.flow.oidc.auth.<auth-name>.token-propagation.enabled=false
```

#### 7.2 Config Classes

```java
@ConfigMapping(prefix = "quarkus.flow.oidc")
public interface FlowOidcConfig {
    
    TokenExchangeConfig tokenExchange();
    SubjectTokenConfig subjectToken();
    Map<String, AuthSchemeConfig> auth();
    
    interface TokenExchangeConfig {
        @WithDefault("true")
        boolean enabled();
        
        @WithDefault("300")
        int proactiveRefreshSeconds();
        
        @WithDefault("60")
        int monitorRateSeconds();
    }
    
    interface SubjectTokenConfig {
        @WithDefault("subjectToken")
        String inputKey();
        
        @WithDefault("access_token")
        String securityIdentityAttribute();
    }
    
    interface AuthSchemeConfig {
        Optional<String> oidcClientName();
        Optional<Boolean> tokenExchangeEnabled();
        Optional<Integer> proactiveRefreshSeconds();
        Optional<Boolean> tokenPropagationEnabled();
    }
}
```

#### 7.3 Configuration Resolution

**Precedence**: Per-auth-scheme > Global > Smart defaults

```java
public class AuthConfigResolver {
    
    /**
     * Determine if token exchange is enabled for this auth scheme.
     */
    public boolean isTokenExchangeEnabled(
        String authSchemeName,
        Authentication auth
    ) {
        // 1. Check scheme-specific config (highest priority)
        Optional<Boolean> schemeConfig = config.auth()
            .get(authSchemeName)
            .flatMap(AuthSchemeConfig::tokenExchangeEnabled);
        if (schemeConfig.isPresent()) {
            return schemeConfig.get();
        }
        
        // 2. Check global config
        if (!config.tokenExchange().enabled()) {
            return false;
        }
        
        // 3. Smart default: if subject token present in auth DSL, enable exchange
        if (auth instanceof OAuth2Authentication oauth2) {
            return oauth2.subject() != null;
        }
        
        return false;
    }
    
    /**
     * Determine if token propagation is enabled.
     * Note: Propagation takes precedence over exchange if both enabled.
     */
    public boolean isTokenPropagationEnabled(String authSchemeName) {
        return config.auth()
            .get(authSchemeName)
            .flatMap(AuthSchemeConfig::tokenPropagationEnabled)
            .orElse(false);
    }
}
```

**Smart Defaults**:
- If OAuth2 definition includes `subject` token → auto-enable exchange
- If config explicitly enables propagation → use that (takes precedence)
- Otherwise follow global `token-exchange.enabled` setting

### 8. Proactive Token Refresh

Background monitor that refreshes tokens before expiration:

```java
@ApplicationScoped
public class TokenRefreshMonitor {
    
    @Inject
    FlowOidcConfig config;
    
    @Inject
    TokenCacheRepository repository;
    
    @Inject
    TokenExchangeClient exchangeClient;
    
    private ScheduledExecutorService scheduler;
    
    @PostConstruct
    void start() {
        if (!config.tokenExchange().enabled()) {
            return;
        }
        
        int rateSeconds = config.tokenExchange().monitorRateSeconds();
        scheduler = Executors.newSingleThreadScheduledExecutor(
            new ThreadFactoryBuilder()
                .setNameFormat("flow-token-refresh-%d")
                .setDaemon(true)
                .build()
        );
        
        scheduler.scheduleAtFixedRate(
            this::refreshNearingExpiry,
            rateSeconds,
            rateSeconds,
            TimeUnit.SECONDS
        );
    }
    
    @PreDestroy
    void stop() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }
    
    void refreshNearingExpiry() {
        Duration threshold = Duration.ofSeconds(
            config.tokenExchange().proactiveRefreshSeconds()
        );
        
        Collection<CachedToken> tokens = repository
            .getTokensNearingExpiry(threshold);
        
        tokens.forEach(this::refreshToken);
    }
    
    private void refreshToken(CachedToken token) {
        try {
            // Note: Need to store original subject token to refresh
            // This is a Phase 2 enhancement - for Phase 1, tokens expire naturally
            String newAccessToken = exchangeClient.exchange(
                token.key().authSchemeName(),
                /* original subject token */,
                Optional.ofNullable(token.key().audience()),
                Optional.empty()
            ).getAccessToken();
            
            // Parse expiry from new token
            Instant newExpiry = parseExpiry(newAccessToken);
            
            // Update cache
            repository.store(token.withNewToken(newAccessToken, newExpiry));
            
            LOG.debug("Refreshed token for auth scheme: {}", token.key().authSchemeName());
            
        } catch (Exception e) {
            LOG.warn("Failed to refresh token for {}: {}", 
                token.key().authSchemeName(), e.getMessage());
            // Keep existing token, will retry next monitor cycle
        }
    }
}
```

**Design decisions**:
- Single background thread (lightweight, won't exhaust resources)
- Configurable refresh threshold (default: 5 minutes before expiry)
- Graceful failure: Refresh failures logged but don't break workflow execution
- Daemon thread: Won't block JVM shutdown

**Phase 1 Limitation**: For Phase 1, we won't store the original subject token (security concern), so proactive refresh will use OIDC refresh tokens when available. If refresh tokens not supported, tokens will expire naturally and tasks will re-exchange on 401 response. Phase 2 will address this with proper encrypted storage of subject tokens for re-exchange.

### 9. Workflow Lifecycle Integration

#### 9.1 Token Lifecycle Manager

```java
@ApplicationScoped
public class TokenLifecycleManager {
    
    @Inject
    TokenCacheRepository repository;
    
    /**
     * Called when workflow instance terminates.
     * Unlinks instance from tokens and cleans up orphans.
     */
    public void onWorkflowTerminate(String instanceId) {
        repository.unlinkInstance(instanceId);
        // Repository internally cleans up orphaned tokens
    }
}
```

#### 9.2 Workflow Event Listener

```java
@ApplicationScoped
public class TokenCleanupEventListener implements WorkflowEventListener {
    
    @Inject
    TokenLifecycleManager lifecycleManager;
    
    @Override
    public void onEvent(WorkflowEvent event) {
        if (event instanceof WorkflowCompletedEvent ||
            event instanceof WorkflowCancelledEvent ||
            event instanceof WorkflowFaultedEvent) {
            
            lifecycleManager.onWorkflowTerminate(event.instanceId());
        }
    }
}
```

**Lifecycle Flow**:
1. Workflow starts → tokens linked lazily when first requested
2. Workflow terminates (complete/cancel/fault) → unlink instance
3. Repository checks if token orphaned (no linked instances)
4. Orphaned tokens evicted from cache

### 10. Error Handling

#### 10.1 Token Extraction Failures

**Strategy**: Graceful degradation

- If subject token not found and propagation/exchange configured → log warning, proceed without auth
- Let downstream service return 401/403 (clearer error for users)
- Don't fail workflow execution on auth setup

```java
Optional<String> token = extractor.extract(workflow, auth);
if (token.isEmpty()) {
    LOG.warn("Subject token not found for auth scheme: {}. " +
             "Request will be sent without Authorization header.",
             authSchemeName);
    return Optional.empty();
}
```

#### 10.2 Token Exchange Failures

**Strategy**: Fail fast with retries

- First exchange attempt fails → immediate retry once
- Second failure → throw exception, fail task
- Task can be retried with workflow's retry policy
- Log detailed error (OIDC endpoint, grant type, HTTP status, response body)

```java
try {
    return exchangeClient.exchange(...);
} catch (Exception e) {
    LOG.warn("Token exchange failed, retrying once: {}", e.getMessage());
    try {
        return exchangeClient.exchange(...);
    } catch (Exception retry) {
        LOG.error("Token exchange failed after retry for auth scheme {}: {}", 
                  authSchemeName, retry.getMessage(), retry);
        throw new WorkflowRuntimeException(
            "Token exchange failed: " + retry.getMessage(), retry);
    }
}
```

#### 10.3 Cache Failures (Phase 2)

**Strategy**: Degrade to uncached mode

- Cache unavailable → fall back to exchange on every request (performance hit, not failure)
- Cache write failure → log warning, continue (token still usable, just not cached)
- Cache read failure → treat as cache miss, exchange new token

#### 10.4 Proactive Refresh Failures

**Strategy**: Best-effort, non-blocking

- Refresh fails → keep existing token, retry next monitor cycle
- Token expires before refresh succeeds → next task request triggers on-demand exchange
- Log warning for observability, don't throw exceptions

### 11. Security Considerations

#### 11.1 Token Storage

**Subject tokens**:
- Hashed (SHA-256) before storage
- Never stored in plaintext
- Hash used only for cache key lookup

**Exchanged tokens**:
- Stored in plaintext (required for Authorization header)
- Phase 1: In-memory only (cleared on application restart)
- Phase 2: Consider encryption at rest via Quarkus datasource config

#### 11.2 Token Sharing

**Scope**: Per-subject-token across instances (default)

- Same user's token reused across their workflow instances (efficiency)
- Link tracking prevents leakage between users
- Instance A cannot use tokens linked to instance B after B terminates
- Hash-based keys prevent reverse lookup of original tokens

#### 11.3 Configuration Security

**Client secrets**:
- Should reference Quarkus secrets/vault, not plaintext
- Example: `quarkus.oidc-client.my-api.credentials.secret=${vault:oidc/my-api/secret}`

**Logging**:
- Never log tokens (mask in logs)
- Error messages include metadata only (endpoint, grant type), not token values

**Memory safety**:
- Tokens cleared from memory on cache eviction
- No token retention after workflow/instance terminates

#### 11.4 Thread Safety

**Concurrent access**:
- All cache operations use `ConcurrentHashMap`
- Token refresh under lock (prevent duplicate exchanges)
- Instance unlinking uses atomic operations
- Repository methods are thread-safe by design

## Phase 0: Validation & Proof of Concept

### Goals

Before implementing token propagation/exchange infrastructure, validate that:
1. Quarkus Flow's current architecture can integrate with `quarkus-oidc-client`
2. SW SDK's OAuth2 handling works with real OIDC providers
3. Named OIDC clients work correctly with workflow tasks
4. No blockers exist in the SDK's HTTP execution path

### Scope

**Examples to Build**:

1. **OAuth2 Client Credentials** (service-to-service, no user token):
   ```java
   @ApplicationScoped
   public class ServiceToServiceFlow extends Flow {
       @Override
       public Workflow descriptor() {
           return workflow("oauth2-client-credentials")
               .tasks(
                   call("getProtectedResource",
                       http()
                           .GET()
                           .uri("https://api.example.com/resource",
                               oauth2(
                                   "https://idp.example.com/realms/demo",
                                   CLIENT_CREDENTIALS,
                                   "workflow-client-id",
                                   "workflow-client-secret"
                               ))
                   )
               ).build();
       }
   }
   ```

2. **OAuth2 with User Token** (manual token passing):
   ```java
   // Workflow receives token as input, passes to downstream service
   workflow("oauth2-user-context")
       .tasks(
           call("callSecureAPI",
               http()
                   .GET()
                   .uri("https://api.example.com/user-data",
                       bearer("${ .userToken }")) // Token from workflow input
           )
       ).build();
   ```

3. **Multiple OIDC Clients**:
   ```properties
   # Test named OIDC clients
   quarkus.oidc-client.service-a.auth-server-url=https://idp-a.example.com
   quarkus.oidc-client.service-a.client-id=client-a
   quarkus.oidc-client.service-a.credentials.secret=secret-a
   
   quarkus.oidc-client.service-b.auth-server-url=https://idp-b.example.com
   quarkus.oidc-client.service-b.client-id=client-b
   quarkus.oidc-client.service-b.credentials.secret=secret-b
   ```

4. **OpenAPI with OAuth2 Security Scheme**:
   ```yaml
   # OpenAPI spec with oauth2 security
   components:
     securitySchemes:
       petstore-oauth:
         type: oauth2
         flows:
           clientCredentials:
             tokenUrl: https://idp.example.com/token
             scopes:
               read:pets: Read access to pets
   paths:
     /pets:
       get:
         security:
           - petstore-oauth: [read:pets]
   ```

**Test Infrastructure**:
- WireMock for OIDC token endpoints
- Mock protected services returning 401 without valid token
- Integration tests covering success and failure scenarios

**Questions to Answer**:
1. Does SDK's `HttpExecutor` properly pass authentication to JAX-RS client?
2. Can we inject `OidcClient` instances and use them in decorators?
3. How does SDK resolve auth scheme names from OpenAPI specs?
4. Are there SDK limitations with OAuth2 grants (client_credentials, password, etc.)?
5. Does the current `HttpRequestDecorator` pattern support auth header injection?

**Deliverables**:
- [ ] Working example: OAuth2 client credentials flow
- [ ] Working example: Bearer token propagation (manual)
- [ ] Working example: Multiple named OIDC clients
- [ ] Working example: OpenAPI with OAuth2 security scheme
- [ ] Integration tests for each example
- [ ] Documentation: Findings and integration patterns
- [ ] Go/No-Go decision for Phase 1 based on findings

**Success Criteria**:
- All examples work with real OIDC flows (mocked via WireMock)
- No blockers identified in SDK or Quarkus Flow architecture
- Clear integration points identified for Phase 1 implementation

**Risk Mitigation**:
If Phase 0 reveals blockers:
- Document workarounds or SDK changes needed
- Propose alternative architecture if current approach won't work
- Engage with SW SDK maintainers if SDK changes required

## Module Structure

### Basic OIDC Support in Core (Phase 0 Contingency)

If Phase 0 reveals that basic OIDC/OAuth2 support is missing:

```
core/runtime/
└── src/main/java/io/quarkiverse/flow/
    └── oidc/ (new, conditionally activated)
        ├── BasicOidcAuthHandler.java
        ├── OidcClientIntegration.java
        └── config/
            └── BasicOidcConfig.java
```

**Key principles**:
- Lives in `core/runtime` (part of core Quarkus Flow)
- No new dependencies in `core` POM
- Only activates when `quarkus-oidc-client` detected on classpath
- Implements SW spec OAuth2/OIDC authentication definitions
- Foundation for Phase 1 advanced features

### Phase 0 Modules (Examples)

```
examples/oidc/
├── pom.xml
├── README.md (explains all scenarios)
├── src/main/java/org/acme/oidc/
│   ├── clientcredentials/
│   │   ├── ClientCredentialsFlow.java
│   │   └── ProtectedResourceEndpoint.java (mocked secure endpoint)
│   ├── usertoken/
│   │   ├── UserTokenFlow.java
│   │   └── SecureAPIEndpoint.java
│   ├── multiclient/
│   │   ├── MultiClientFlow.java
│   │   ├── ServiceAEndpoint.java
│   │   └── ServiceBEndpoint.java
│   └── openapi/
│       └── PetStoreOAuth2Flow.java
├── src/main/resources/
│   ├── application.properties (all OIDC client configs)
│   └── openapi/
│       └── petstore-oauth.json
└── src/test/java/org/acme/oidc/
    ├── clientcredentials/
    │   └── ClientCredentialsFlowTest.java
    ├── usertoken/
    │   └── UserTokenFlowTest.java
    ├── multiclient/
    │   └── MultiClientFlowTest.java
    ├── openapi/
    │   └── PetStoreOAuth2FlowTest.java
    └── OidcWireMockResource.java (shared test infrastructure)
```

**Benefits**:
- Single shared configuration (`application.properties`)
- Shared test infrastructure (WireMock, test utilities)
- Easier to run all scenarios together
- Single README documenting all OAuth2 patterns
- Reduced duplication

### Phase 1 Modules (Advanced OIDC Features)

**Note**: Builds on basic OIDC support in `core` (added in Phase 0 if needed)

```
oidc/ (parent POM: quarkus-flow-oidc-parent)
├── pom.xml
└── core/ (artifact: quarkus-flow-oidc)
    ├── pom.xml
    ├── runtime/
    │   ├── pom.xml
    │   ├── src/main/java/io/quarkiverse/flow/oidc/
    │   │   ├── AuthenticationProvider.java
    │   │   ├── AuthenticationRegistry.java
    │   │   ├── AuthenticationRequestDecorator.java
    │   │   ├── SubjectTokenExtractor.java
    │   │   ├── TokenCacheRepository.java
    │   │   ├── TokenLifecycleManager.java
    │   │   ├── TokenCleanupEventListener.java
    │   │   ├── providers/
    │   │   │   ├── TokenPropagationProvider.java
    │   │   │   ├── TokenExchangeProvider.java
    │   │   │   └── ClientCredentialsProvider.java
    │   │   ├── cache/
    │   │   │   ├── InMemoryTokenCacheRepository.java
    │   │   │   ├── TokenCacheKey.java
    │   │   │   ├── CachedToken.java
    │   │   │   └── TokenRefreshMonitor.java
    │   │   ├── config/
    │   │   │   ├── FlowOidcConfig.java
    │   │   │   └── AuthConfigResolver.java
    │   │   └── client/
    │   │       ├── OidcClientProvider.java
    │   │       └── TokenExchangeClient.java
    │   └── src/main/resources/
    │       └── META-INF/services/
    │           └── io.serverlessworkflow.impl.executors.http.HttpRequestDecorator
    ├── deployment/
    │   ├── pom.xml
    │   └── src/main/java/io/quarkiverse/flow/oidc/deployment/
    │       └── FlowOidcProcessor.java
    └── integration-tests/
        ├── pom.xml
        └── src/test/java/io/quarkiverse/flow/oidc/it/
            ├── TokenPropagationTest.java
            ├── TokenExchangeTest.java
            ├── TokenCacheTest.java
            └── ProactiveRefreshTest.java
```

### Phase 2 - Persistent Token Cache Modules

```
oidc/ (parent POM: quarkus-flow-oidc-parent)
├── pom.xml
├── core/ (from Phase 1)
└── persistence/ (parent POM: quarkus-flow-oidc-persistence-parent)
    ├── pom.xml
    ├── jpa/ (artifact: quarkus-flow-oidc-persistence-jpa)
    │   ├── pom.xml (depends on: persistence/jpa)
    │   └── src/main/java/io/quarkiverse/flow/oidc/persistence/jpa/
    │       ├── TokenCacheEntity.java
    │       ├── InstanceTokenEntity.java
    │       └── JpaTokenCacheRepository.java (implements TokenCacheRepository)
    ├── redis/ (artifact: quarkus-flow-oidc-persistence-redis)
    │   ├── pom.xml (depends on: persistence/redis)
    │   └── src/main/java/io/quarkiverse/flow/oidc/persistence/redis/
    │       └── RedisTokenCacheRepository.java
    ├── infinispan/ (artifact: quarkus-flow-oidc-persistence-infinispan)
    │   ├── pom.xml (depends on: persistence/infinispan)
    │   └── src/main/java/io/quarkiverse/flow/oidc/persistence/infinispan/
    │       └── InfinispanTokenCacheRepository.java
    └── mvstore/ (artifact: quarkus-flow-oidc-persistence-mvstore)
        ├── pom.xml (depends on: persistence/mvstore)
        └── src/main/java/io/quarkiverse/flow/oidc/persistence/mvstore/
            └── MVStoreTokenCacheRepository.java
```

**Notes on Persistence Modules**:
- **Runtime-only libraries** (no deployment modules) if no build-time processing needed
- **Depend on counterpart persistence modules**: 
  - `oidc/persistence/jpa` → depends on `persistence/jpa`
  - `oidc/persistence/redis` → depends on `persistence/redis`
  - etc.
- **Automatic CDI discovery**: When present on classpath, their `TokenCacheRepository` implementation takes precedence over in-memory
- **Optional extensions**: Only add if persistent token cache needed

**Dependency Structure**:

```xml
<!-- User opts-in to OAuth2 support -->
<dependency>
    <groupId>io.quarkiverse.flow</groupId>
    <artifactId>quarkus-flow-oidc</artifactId>  <!-- Core OAuth2 support -->
</dependency>

<!-- Optional: Add persistent token cache (Phase 2) -->
<dependency>
    <groupId>io.quarkiverse.flow</groupId>
    <artifactId>quarkus-flow-oidc-persistence-jpa</artifactId>
</dependency>
<!-- OR -->
<dependency>
    <groupId>io.quarkiverse.flow</groupId>
    <artifactId>quarkus-flow-oidc-persistence-redis</artifactId>
</dependency>
```

**Benefits**:
- **No coupling**: Core persistence modules don't know about OAuth2
- **Opt-in**: Users only add token cache persistence if they need it
- **Independent lifecycle**: Can update token cache persistence without touching core persistence
- **Clear separation**: OAuth2 domain stays separate from workflow persistence domain

**Database Schema (JPA example)**:

```sql
CREATE TABLE flow_oidc_token_cache (
    id VARCHAR(255) PRIMARY KEY,
    auth_scheme VARCHAR(100) NOT NULL,
    subject_token_hash VARCHAR(64) NOT NULL,
    audience VARCHAR(255),
    exchanged_token TEXT NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    INDEX idx_auth_subject (auth_scheme, subject_token_hash),
    INDEX idx_expires_at (expires_at)
);

CREATE TABLE flow_oidc_instance_tokens (
    instance_id VARCHAR(255) NOT NULL,
    token_cache_id VARCHAR(255) NOT NULL,
    PRIMARY KEY (instance_id, token_cache_id),
    FOREIGN KEY (token_cache_id) REFERENCES flow_oidc_token_cache(id) ON DELETE CASCADE
);
```

**Configuration**:

```properties
# OAuth2 token cache uses separate persistence
# Automatically enabled when oauth2-token-cache-* extension present

# Optional: Configure datasource for token cache (defaults to default datasource)
quarkus.flow.oidc.token-cache.datasource=oauth2
```

## Testing Strategy

### Unit Tests

- Each provider in isolation (mock dependencies)
- Token cache operations (concurrent access, orphan cleanup)
- Config resolution logic (precedence rules)
- Subject token extraction from various sources
- OIDC client resolution (explicit mapping, fallback, error cases)

### Integration Tests

**End-to-end scenarios**:

```java
@QuarkusTest
@QuarkusTestResource(OidcWireMockResource.class)
class TokenExchangeIntegrationTest {
    
    @Test
    void shouldExchangeTokenAndCache() {
        // Mock OIDC token endpoint with WireMock
        stubOidcTokenExchange("auth-api-a", "exchanged-token-123");
        
        // Start workflow with subject token
        WorkflowInstance instance1 = workflow.instance(Map.of(
            "subjectToken", "original-user-token"
        ));
        instance1.start().join();
        
        // Verify token exchange request sent
        verifyTokenExchangeRequest("original-user-token", "my-service-a");
        
        // Start second instance with same subject token
        WorkflowInstance instance2 = workflow.instance(Map.of(
            "subjectToken", "original-user-token"
        ));
        instance2.start().join();
        
        // Verify cache hit (no second exchange)
        verifyTokenExchangeRequestCount(1);
        
        // Verify both instances linked to same token
        assertTokenLinkedToInstances("auth-api-a", instance1.id(), instance2.id());
        
        // Terminate first instance
        instance1.cancel();
        
        // Verify token still cached (instance2 still using it)
        assertTokenCached("auth-api-a");
        
        // Terminate second instance
        instance2.cancel();
        
        // Verify token evicted (orphaned)
        assertTokenNotCached("auth-api-a");
    }
    
    @Test
    void shouldPropagateTokenWhenConfigured() {
        // Configure propagation for this auth scheme
        // Start workflow
        // Verify Authorization header contains original token (not exchanged)
    }
    
    @Test
    void shouldRefreshTokenProactively() {
        // Mock token with 5-minute expiry
        // Wait for refresh threshold
        // Verify refresh request sent before expiry
        // Verify cached token updated
    }
}
```

### Performance Tests

**Phase 2 persistence tests**:
- Token cache write throughput
- Token cache read latency
- Concurrent workflow instances sharing tokens
- Orphan cleanup performance with large instance counts

## Documentation

### New Documentation Page

`docs/modules/ROOT/pages/security-token-propagation.adoc`

**Sections**:
1. **Overview**: Token propagation vs. exchange, when to use each
2. **Quick Start**: Minimal configuration example
3. **Configuration Guide**: All config options explained
4. **Usage Examples**:
   - Token propagation (Java DSL + YAML)
   - Token exchange (Java DSL + YAML)
   - OpenAPI integration
   - Multiple OIDC servers
5. **Advanced Topics**:
   - Token caching and lifecycle
   - Proactive refresh
   - Subject token sources
   - Per-auth-scheme configuration
6. **Security Considerations**: Token storage, sharing scope, secrets management
7. **Troubleshooting**: Common errors and solutions

### Updates to Existing Pages

- `http-openapi-tasks.adoc`: Add token propagation/exchange examples
- `secrets.adoc`: Reference token exchange for OAuth2 scenarios
- `quarkus-flow-cookbook.adoc`: Add OAuth2 token exchange recipe

## Migration from SonataFlow

For users migrating from SonataFlow:

**Config mapping**:

```properties
# SonataFlow → Quarkus Flow
sonataflow.oauth2.auth.<name>.token-exchange.enabled
  → quarkus.flow.oidc.auth.<name>.token-exchange.enabled

sonataflow.oauth2.auth.<name>.token-exchange.proactive-refresh-seconds
  → quarkus.flow.oidc.auth.<name>.token-exchange.proactive-refresh-seconds

quarkus.openapi-generator.<service>.auth.<name>.token-propagation
  → quarkus.flow.oidc.auth.<name>.token-propagation.enabled
```

**Workflow DSL**: No changes needed (uses standard SW 1.0.0 OAuth2)

**Persistence**: Phase 2 will support JDBC like SonataFlow's `kogito-quarkus-serverless-workflow-jdbc-token-persistence`

## Consequences

### Positive

1. **Spec-compatible**: Leverages SW 1.0.0 OAuth2 definitions
2. **Quarkus-native**: Follows Quarkus patterns (CDI, config, extensions)
3. **Flexible**: Supports propagation, exchange, client credentials
4. **Secure**: Token hashing, proper lifecycle, isolation
5. **Extensible**: Easy to add new auth providers, persistence backends
6. **Multi-tenant**: Different OIDC servers per task/workflow
7. **Efficient**: Token caching reduces exchange overhead
8. **Production-ready**: Proactive refresh prevents mid-workflow expiration
9. **Phased**: Core features now, persistence later (no big-bang)
10. **Testable**: Clean abstractions, mockable dependencies

### Negative

1. **Complexity**: More moving parts than simple token propagation
2. **Phase 1 limitations**: In-memory cache only, no proactive refresh for exchanged tokens (requires storing subject token)
3. **Configuration**: Multiple config properties may overwhelm new users
4. **OIDC dependency**: Requires `quarkus-oidc-client` for token exchange
5. **Memory usage**: In-memory cache grows with active workflows (Phase 1)

### Mitigations

- Comprehensive documentation with clear examples
- Smart defaults reduce config boilerplate
- Optional features (can disable proactive refresh, use propagation only)
- Phase 2 persistence addresses memory concerns
- Clear error messages guide users to correct configuration

## Alternatives Considered

### Alternative 1: Minimal Extension (Config-Only)

**Approach**: Use SW OAuth2 as-is, behavior controlled entirely by config

**Rejected because**:
- Behavior split between DSL and config is confusing
- Limited expressiveness
- Harder to support multiple OIDC servers cleanly

### Alternative 2: Full SonataFlow Parity (Day 1)

**Approach**: Implement everything including JDBC persistence in Phase 1

**Rejected because**:
- Large scope increases risk
- Many users won't need persistence immediately
- Phased approach allows feedback before committing to persistence design

### Alternative 3: Pure DSL-Driven

**Approach**: New DSL elements for propagation/exchange, minimal config

**Rejected because**:
- Breaks from SW spec patterns
- Workflow definitions become cluttered with infrastructure concerns
- Harder to change behavior without modifying workflow code

## Open Questions

1. **Phase 1 proactive refresh**: Should we store subject tokens (encrypted) to enable refresh, or defer to Phase 2?
   - **Decision**: Defer to Phase 2. Phase 1 tokens expire naturally, tasks retry on 401.

2. **Token revocation**: Should we support OIDC token revocation endpoint on workflow cancel?
   - **Decision**: Out of scope for Phase 1. Can add as opt-in feature later.

3. **Metrics**: What token-related metrics should we expose (cache hits, exchange failures, refresh count)?
   - **Decision**: Basic metrics in Phase 1 (exchange count, cache hit rate), detailed in Phase 2.

4. **Actor tokens**: SW spec supports `actor` token for delegation scenarios. Support in Phase 1?
   - **Decision**: Out of scope for Phase 1. Can add when user demand emerges.

## References

- [SonataFlow Token Exchange Documentation](https://sonataflow.org/serverlessworkflow/main/security/token-exchange-for-openapi-services.html)
- [Serverless Workflow 1.0.0 Specification](https://github.com/serverlessworkflow/specification/blob/main/dsl-reference.md)
- [OAuth 2.0 Token Exchange (RFC 8693)](https://datatracker.ietf.org/doc/html/rfc8693)
- [Quarkus OIDC Client Guide](https://quarkus.io/guides/security-openid-connect-client)
- [Quarkus Flow HttpClientProvider](core/runtime/src/main/java/io/quarkiverse/flow/providers/HttpClientProvider.java)

## Implementation Phases Breakdown

### Phase 0: Validation & Proof of Concept

**GitHub Issues**:

1. **Issue #XXX: Build OAuth2 Client Credentials Example**
   - **Description**: Create working example using OAuth2 client credentials flow
   - **Acceptance Criteria**:
     - Example application in `examples/oauth2-client-credentials`
     - Integration test with WireMock OIDC server
     - Workflow successfully calls protected endpoint with OAuth2 token
     - Documentation in example README
   - **Dependencies**: None
   - **Assignee**: TBD

2. **Issue #XXX: Build OAuth2 User Token Example**
   - **Description**: Create example with manual user token passing
   - **Acceptance Criteria**:
     - Example application in `examples/oauth2-user-token`
     - Workflow accepts token as input
     - Token properly forwarded to downstream service
     - Integration tests cover success and 401 scenarios
   - **Dependencies**: None
   - **Assignee**: TBD

3. **Issue #XXX: Build Multiple Named OIDC Clients Example**
   - **Description**: Test multiple OIDC clients in single workflow
   - **Acceptance Criteria**:
     - Example application in `examples/oauth2-multi-client`
     - Workflow calls two different services with different OIDC clients
     - Named client configuration validated
     - Integration tests verify correct client used per task
   - **Dependencies**: #XXX (Client Credentials Example)
   - **Assignee**: TBD

4. **Issue #XXX: Build OpenAPI OAuth2 Security Scheme Example**
   - **Description**: Test OpenAPI integration with OAuth2 security schemes
   - **Acceptance Criteria**:
     - Example application in `examples/openapi-oauth2`
     - OpenAPI spec with oauth2 security scheme
     - SDK correctly resolves security scheme to authentication
     - Integration tests verify token obtained and used
   - **Dependencies**: #XXX (Client Credentials Example)
   - **Assignee**: TBD

5. **Issue #XXX: Basic OIDC Support in Core (Conditional)**
   - **Description**: Add basic OIDC/OAuth2 support to `core` if missing
   - **Scope**:
     - Only if Phase 0 examples reveal gaps in current SDK OAuth2 handling
     - Add to `core/runtime` (not separate module)
     - Conditional activation: only when `quarkus-oidc-client` present on classpath
     - Basic OAuth2 client credentials support
     - Integration with SW spec OAuth2 authentication definitions
     - No additional dependencies forced on users
   - **Acceptance Criteria**:
     - SW spec OAuth2 definitions work with `quarkus-oidc-client`
     - Feature only activates when OIDC client extension present
     - No new dependencies added to `core` POM
     - Integration tests verify OAuth2 flows
     - Documentation explains OIDC client requirement
   - **Dependencies**: #1-4 (examples reveal gaps)
   - **Assignee**: TBD

6. **Issue #XXX: Phase 0 Assessment & Go/No-Go Decision**
   - **Description**: Assess findings and document integration patterns
   - **Acceptance Criteria**:
     - Document findings from all Phase 0 examples
     - Identify integration points for Phase 1
     - Document any blockers or SDK limitations
     - If basic OIDC added to core (#5), validate it works
     - Go/No-Go decision for Phase 1
     - Update ADR with findings
   - **Dependencies**: #1-5 (all Phase 0 work)
   - **Assignee**: Tech Lead

---

### Phase 1: Advanced OIDC Features

**GitHub Issues**:

7. **Issue #XXX: Token Propagation**
   - **Description**: Implement token propagation feature
   - **Scope**:
     - `AuthenticationProvider` abstraction and `AuthenticationRegistry`
     - `SubjectTokenExtractor` (SecurityIdentity, workflow input, expressions)
     - `TokenPropagationProvider` implementation
     - `AuthenticationRequestDecorator` (ServiceLoader integration)
     - Configuration: `FlowOidcConfig` for propagation settings
     - Integration tests: E2E token propagation scenarios
     - Documentation: Propagation usage guide
   - **Acceptance Criteria**:
     - User tokens extracted from all sources (SecurityIdentity, input, DSL)
     - Tokens forwarded to downstream services via Authorization header
     - Integration tests with WireMock verify propagation
     - Documentation includes Java DSL + YAML examples
   - **Dependencies**: Phase 0 complete
   - **Assignee**: TBD

8. **Issue #XXX: Token Exchange**
   - **Description**: Implement token exchange with caching
   - **Scope**:
     - `OidcClientProvider` (named client resolution)
     - `TokenExchangeClient` (RFC 8693 exchange)
     - `TokenCacheRepository` interface
     - `InMemoryTokenCacheRepository` with instance linking
     - `TokenExchangeProvider` implementation
     - `ClientCredentialsProvider` (service-to-service)
     - `TokenLifecycleManager` (workflow event integration)
     - Configuration: Token exchange and cache settings
     - Integration tests: E2E exchange, caching, lifecycle
     - Documentation: Exchange usage guide, cache behavior
   - **Acceptance Criteria**:
     - Tokens exchanged via OIDC client with RFC 8693
     - Exchanged tokens cached per subject token
     - Multiple workflow instances share cached tokens
     - Orphaned tokens cleaned up on instance termination
     - Client credentials flow works without user context
     - Integration tests verify caching and lifecycle
     - Documentation covers all exchange scenarios
   - **Dependencies**: #7 (Token Propagation - shares core abstractions)
   - **Assignee**: TBD

9. **Issue #XXX: Proactive Token Refresh**
   - **Description**: Implement background token refresh
   - **Scope**:
     - `TokenRefreshMonitor` (background scheduler)
     - Refresh logic using OIDC refresh tokens
     - Configuration: Refresh threshold, monitor rate
     - Error handling for refresh failures
     - Integration tests: Token refresh before expiry
     - Documentation: Refresh behavior and configuration
   - **Acceptance Criteria**:
     - Background monitor runs at configurable rate
     - Tokens refreshed N seconds before expiry
     - Refresh failures logged, don't break workflows
     - Integration tests verify proactive refresh
     - Documentation explains refresh lifecycle
   - **Dependencies**: #8 (Token Exchange - uses cache and OIDC client)
   - **Assignee**: TBD

---

### Phase 2: Persistence

**GitHub Issues**:

10. **Issue #XXX: JPA Token Cache Persistence**
   - **Description**: Persistent token cache using JPA
   - **Scope**:
     - Module: `oidc/persistence/jpa`
     - `TokenCacheEntity` and `InstanceTokenEntity`
     - `JpaTokenCacheRepository` implementation
     - Database migration scripts
     - Integration tests with H2 and PostgreSQL
     - Documentation: JPA persistence configuration
   - **Acceptance Criteria**:
     - Tokens persisted across application restarts
     - Junction table links instances to tokens
     - Automatic CDI discovery when extension present
     - Integration tests verify persistence and cleanup
     - Documentation includes datasource configuration
   - **Dependencies**: Phase 1 complete
   - **Assignee**: TBD

11. **Issue #XXX: Redis Token Cache Persistence**
    - **Description**: Persistent token cache using Redis
    - **Scope**:
      - Module: `oidc/persistence/redis`
      - `RedisTokenCacheRepository` implementation
      - Redis data structures with TTL
      - Integration tests with Testcontainers
      - Documentation: Redis persistence configuration
    - **Acceptance Criteria**:
      - Tokens stored in Redis with automatic expiry
      - Distributed cache support for multi-instance deployments
      - Integration tests verify Redis persistence
      - Documentation includes Redis client configuration
     - **Dependencies**: Phase 1 complete
    - **Assignee**: TBD

12. **Issue #XXX: Infinispan Token Cache Persistence**
    - **Description**: Distributed token cache using Infinispan
    - **Scope**:
      - Module: `oidc/persistence/infinispan`
      - `InfinispanTokenCacheRepository` implementation
      - Distributed cache configuration
      - Integration tests
      - Documentation: Infinispan persistence configuration
    - **Acceptance Criteria**:
      - Tokens cached in Infinispan distributed cache
      - Cache shared across cluster nodes
      - Integration tests verify distributed behavior
      - Documentation includes cluster configuration
     - **Dependencies**: Phase 1 complete
    - **Assignee**: TBD

13. **Issue #XXX: MVStore Token Cache Persistence**
    - **Description**: Persistent token cache using MVStore
    - **Scope**:
      - Module: `oidc/persistence/mvstore`
      - `MVStoreTokenCacheRepository` implementation
      - File-based storage
      - Integration tests
      - Documentation: MVStore persistence configuration
    - **Acceptance Criteria**:
      - Tokens persisted to local file
      - Lightweight persistence for single-node deployments
      - Integration tests verify file-based persistence
      - Documentation includes file path configuration
     - **Dependencies**: Phase 1 complete
    - **Assignee**: TBD

14. **Issue #XXX: Enhanced Proactive Refresh & Performance Testing**
    - **Description**: Subject token storage for refresh and performance benchmarks
    - **Scope**:
      - Subject token encryption/decryption for storage
      - Enhanced refresh using stored subject tokens
      - Performance benchmarks (write throughput, read latency, concurrent stress)
      - Performance comparison: in-memory vs all persistence types
      - Documentation: Performance characteristics and recommendations
    - **Acceptance Criteria**:
      - Encrypted subject tokens stored with exchanged tokens
      - Proactive refresh works without re-authentication
      - Benchmark results documented per persistence type
      - Performance recommendations for different deployment scenarios
      - Updated documentation includes performance guidance
     - **Dependencies**: #9, #10, #11, #12 (all persistence implementations)
    - **Assignee**: TBD

**Phase 2 Total Estimate**: 22-28 days (issues #9-12 can be parallelized)

---

## Approval

- [ ] Architecture Review
- [ ] Security Review
- [ ] Phase 0 Plan Approved
- [ ] Phase 1 Plan Approved
- [ ] Phase 2 Plan Approved (Conditional on Phase 0/1 success)

---

**Next Steps**: 
1. Review and approve this ADR
2. Create GitHub issues for Phase 0 (Issues #1-6)
3. Start Phase 0 implementation
4. Phase 0 Go/No-Go decision before proceeding to Phase 1
