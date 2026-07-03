package io.quarkiverse.flow.oidc;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.quarkus.oidc.client.OidcClient;
import io.quarkus.oidc.client.OidcClientConfigBuilder;
import io.quarkus.oidc.client.Tokens;
import io.quarkus.oidc.client.runtime.OidcClientConfig;
import io.quarkus.oidc.common.runtime.config.OidcClientCommonConfig.Credentials;
import io.serverlessworkflow.api.types.OAuth2AuthenticationData;
import io.serverlessworkflow.api.types.OAuth2AuthenticationData.OAuth2AuthenticationDataGrant;
import io.serverlessworkflow.api.types.OAuth2AuthenticationDataClient;
import io.serverlessworkflow.api.types.OAuth2ConnectAuthenticationProperties;
import io.serverlessworkflow.api.types.OAuth2TokenDefinition;
import io.serverlessworkflow.api.types.UriTemplate;
import io.serverlessworkflow.impl.TaskContext;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowContext;
import io.serverlessworkflow.impl.WorkflowDefinitionId;
import io.serverlessworkflow.impl.WorkflowModel;
import io.serverlessworkflow.impl.WorkflowUtils;
import io.serverlessworkflow.impl.auth.AuthProvider;

/**
 * An {@link AuthProvider} that negotiates an OAuth2/OIDC access token using a Quarkus {@link OidcClient}.
 *
 * <p>
 * When a named Quarkus OIDC client is configured for the current workflow/task (via
 * {@code quarkus.flow.oidc.client."...".name}), that pre-configured client is used directly and is fully config-driven:
 * its grant, credentials and request timing all come from {@code quarkus.oidc-client.<name>} (the blocking await mirrors
 * that client's {@code connection-timeout}), and no per-execution grant parameters are derived from the workflow DSL
 * policy. Otherwise, the workflow policy is mapped to an {@link OidcClientConfig} built from the DSL values; for the
 * token-exchange grant on that DSL path the per-execution subject/actor tokens are resolved from the workflow context and
 * passed as dynamic grant parameters. The negotiated {@code access_token} is returned and the SDK attaches it as
 * {@code Authorization: Bearer <token>} to the downstream call.
 */
public final class OidcClientAuthProvider implements AuthProvider {

    private static final String DEFAULT_TOKEN_PATH = "/oauth2/token";

    private final WorkflowApplication application;
    private final OAuth2Policy policy;
    private final OidcClientFactory clientFactory;
    private final OidcConfigResolver configResolver;
    private final String authPolicyName;

    public OidcClientAuthProvider(WorkflowApplication application, OAuth2Policy policy, OidcClientFactory clientFactory,
            OidcConfigResolver configResolver, String authPolicyName) {
        this.application = application;
        this.policy = policy;
        this.clientFactory = clientFactory;
        this.configResolver = configResolver;
        this.authPolicyName = authPolicyName;
    }

    @Override
    public String scheme() {
        return "Bearer";
    }

    @Override
    public String content(WorkflowContext workflow, TaskContext task, WorkflowModel model, URI uri) {
        final WorkflowDefinitionId workflowId = workflow.definition().id();
        final String taskName = task.taskName();
        final Optional<String> namedClient = configResolver.resolve(workflowId, taskName, authPolicyName);

        if (namedClient.isPresent()) {
            return negotiateWithNamedClient(namedClient.get());
        }

        return negotiateWithDslClient(workflow, task, model, clientFactory.clientCreationTimeout());
    }

    private String negotiateWithNamedClient(String clientName) {
        final OidcClient client = clientFactory.getNamedClient(clientName);
        if (client == null) {
            throw new IllegalStateException(
                    "Flow OIDC: named client '" + clientName + "' not found. Ensure it is configured under quarkus.oidc-client."
                            + clientName);
        }
        final Duration timeout = clientFactory.namedConnectionTimeout(clientName);
        try {
            final Tokens tokens = client.getTokens().await().atMost(timeout);
            return tokens.getAccessToken();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Flow OIDC: failed to negotiate an access token from named client '" + clientName + "': "
                            + e.getMessage(),
                    e);
        }
    }

    private String negotiateWithDslClient(WorkflowContext workflow, TaskContext task, WorkflowModel model, Duration timeout) {
        final OAuth2AuthenticationData data = policy.data();
        final ResolvedConfig resolved = resolveConfig(workflow, task, model, data);
        final Duration creationTimeout = configResolver.resolveCreationTimeout(workflow.definition().id(), task.taskName(),
                authPolicyName);
        final OidcClient client = clientFactory.get(resolved.cacheKey, () -> buildConfig(resolved), creationTimeout);
        final Map<String, String> dynamicParams = dynamicParams(workflow, task, model, data);
        try {
            final Tokens tokens = (dynamicParams.isEmpty() ? client.getTokens() : client.getTokens(dynamicParams))
                    .await().atMost(timeout);
            return tokens.getAccessToken();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Flow OIDC: failed to negotiate an access token from '" + resolved.authority() + "': "
                            + e.getMessage(),
                    e);
        }
    }

    /**
     * Resolves every policy field that influences the {@link OidcClientConfig} once, and derives the cache key from the
     * complete set of resolved values. Resolving in a single place avoids re-evaluating the same expression filters twice
     * (the cache key and the config build) and guarantees the key covers every value baked into the cached client.
     */
    private ResolvedConfig resolveConfig(WorkflowContext workflow, TaskContext task, WorkflowModel model,
            OAuth2AuthenticationData data) {
        final boolean oidc = policy.isOpenIdConnect();
        final String authority = resolve(workflow, task, model, authorityTemplate(data));
        final String tokenPath = oidc ? authority : tokenPath(data);
        final String clientId = resolve(workflow, task, model, clientId(data));
        final String clientSecret = resolve(workflow, task, model, clientSecret(data));
        final Credentials.Secret.Method secretMethod = clientSecret == null ? null : clientSecretMethod(data);
        final OAuth2AuthenticationDataGrant grant = data.getGrant();
        final List<String> scopes = data.getScopes();
        final List<String> audiences = data.getAudiences();

        String username = null;
        String password = null;
        if (grant == OAuth2AuthenticationDataGrant.PASSWORD) {
            username = resolve(workflow, task, model, data.getUsername());
            password = resolve(workflow, task, model, data.getPassword());
        }

        final CacheKey cacheKey = new CacheKey(authority, tokenPath, oidc, clientId, secretMethod, grant, scopes, audiences,
                clientSecret, username, password);
        return new ResolvedConfig(cacheKey, authority, tokenPath, clientId, clientSecret, secretMethod, grant, scopes,
                audiences, username, password);
    }

    private OidcClientConfig buildConfig(ResolvedConfig config) {
        final OidcClientConfigBuilder builder = new OidcClientConfigBuilder().id(config.cacheKey.configId());

        builder.authServerUrl(config.authority()).discoveryEnabled(false).tokenPath(config.tokenPath())
                .connectionTimeout(clientFactory.connectionTimeout());

        if (config.clientId() != null) {
            builder.clientId(config.clientId());
        }
        if (config.clientSecret() != null) {
            builder.credentials().clientSecret(config.clientSecret(), config.secretMethod()).end();
        }

        builder.grant(grantType(config.grant()));

        if (config.scopes() != null && !config.scopes().isEmpty()) {
            builder.scopes(config.scopes());
        }
        if (config.audiences() != null && !config.audiences().isEmpty()) {
            builder.audience(config.audiences());
        }

        if (config.grant() == OAuth2AuthenticationDataGrant.PASSWORD) {
            final Map<String, String> passwordOptions = new HashMap<>();
            if (config.username() != null) {
                passwordOptions.put("username", config.username());
            }
            if (config.password() != null) {
                passwordOptions.put("password", config.password());
            }
            builder.grantOptions("password", passwordOptions);
        }

        return builder.build();
    }

    private Map<String, String> dynamicParams(WorkflowContext workflow, TaskContext task, WorkflowModel model,
            OAuth2AuthenticationData data) {
        if (data.getGrant() != OAuth2AuthenticationDataGrant.URN_IETF_PARAMS_OAUTH_GRANT_TYPE_TOKEN_EXCHANGE) {
            return Map.of();
        }
        final Map<String, String> params = new HashMap<>();
        addTokenParams(workflow, task, model, data.getSubject(), "subject_token", "subject_token_type", params);
        addTokenParams(workflow, task, model, data.getActor(), "actor_token", "actor_token_type", params);
        return params;
    }

    private void addTokenParams(WorkflowContext workflow, TaskContext task, WorkflowModel model,
            OAuth2TokenDefinition definition, String tokenKey, String typeKey, Map<String, String> params) {
        if (definition == null) {
            return;
        }
        final String token = resolve(workflow, task, model, definition.getToken());
        if (token != null) {
            params.put(tokenKey, token);
        }
        final String type = resolve(workflow, task, model, definition.getType());
        if (type != null) {
            params.put(typeKey, type);
        }
    }

    private String resolve(WorkflowContext workflow, TaskContext task, WorkflowModel model, String template) {
        if (template == null) {
            return null;
        }
        return WorkflowUtils.buildStringFilter(application, template).apply(workflow, task, model);
    }

    private static String authorityTemplate(OAuth2AuthenticationData data) {
        final UriTemplate authority = data.getAuthority();
        final String value = authority == null ? null
                : (authority.getLiteralUri() != null ? authority.getLiteralUri().toString()
                        : authority.getLiteralUriTemplate());
        if (value == null) {
            throw new IllegalStateException("OAuth2/OIDC authentication policy is missing the required 'authority'");
        }
        validateAuthority(value);
        return value;
    }

    private static void validateAuthority(String authority) {
        if (authority == null || authority.isBlank()) {
            throw new IllegalStateException("OAuth2/OIDC authority cannot be null or blank");
        }
        try {
            URI uri = URI.create(authority);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("https") && !scheme.equalsIgnoreCase("http"))) {
                throw new IllegalStateException("OAuth2/OIDC authority must use http or https scheme: " + authority);
            }
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                throw new IllegalStateException("OAuth2/OIDC authority must have a valid host: " + authority);
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("OAuth2/OIDC authority is not a valid URI: " + authority, e);
        }
    }

    private static String tokenPath(OAuth2AuthenticationData data) {
        if (data instanceof OAuth2ConnectAuthenticationProperties props && props.getEndpoints() != null
                && props.getEndpoints().getToken() != null) {
            return props.getEndpoints().getToken();
        }
        return DEFAULT_TOKEN_PATH;
    }

    private static String clientId(OAuth2AuthenticationData data) {
        final OAuth2AuthenticationDataClient client = data.getClient();
        return client == null ? null : client.getId();
    }

    private static String clientSecret(OAuth2AuthenticationData data) {
        final OAuth2AuthenticationDataClient client = data.getClient();
        return client == null ? null : client.getSecret();
    }

    /**
     * Maps the policy's client authentication scheme to the way Quarkus sends the client credentials. The Serverless
     * Workflow default is {@code client_secret_post} (credentials in the request body), so we default to {@code POST}
     * rather than Quarkus' own {@code client_secret_basic} default. {@code PRIVATE_KEY_JWT} relies on an asymmetric signing
     * key (not a shared client secret) and cannot be honoured from a policy that only carries a {@code secret}, so it is
     * rejected explicitly instead of being silently downgraded to a {@code client_secret_jwt} (HMAC) assertion.
     */
    private static Credentials.Secret.Method clientSecretMethod(OAuth2AuthenticationData data) {
        final OAuth2AuthenticationDataClient client = data.getClient();
        if (client == null || client.getAuthentication() == null) {
            return Credentials.Secret.Method.POST;
        }
        return switch (client.getAuthentication()) {
            case CLIENT_SECRET_BASIC -> Credentials.Secret.Method.BASIC;
            case CLIENT_SECRET_JWT -> Credentials.Secret.Method.POST_JWT;
            case PRIVATE_KEY_JWT -> throw new IllegalStateException(
                    "Flow OIDC: PRIVATE_KEY_JWT client authentication is not supported; use CLIENT_SECRET_BASIC, "
                            + "CLIENT_SECRET_POST or CLIENT_SECRET_JWT");
            default -> Credentials.Secret.Method.POST;
        };
    }

    private static OidcClientConfig.Grant.Type grantType(OAuth2AuthenticationDataGrant grant) {
        if (grant == null) {
            return OidcClientConfig.Grant.Type.CLIENT;
        }
        return switch (grant) {
            case CLIENT_CREDENTIALS -> OidcClientConfig.Grant.Type.CLIENT;
            case PASSWORD -> OidcClientConfig.Grant.Type.PASSWORD;
            case REFRESH_TOKEN -> OidcClientConfig.Grant.Type.REFRESH;
            case AUTHORIZATION_CODE -> OidcClientConfig.Grant.Type.CODE;
            case URN_IETF_PARAMS_OAUTH_GRANT_TYPE_TOKEN_EXCHANGE -> OidcClientConfig.Grant.Type.EXCHANGE;
        };
    }

    /**
     * The fully resolved view of a policy: every expression has been evaluated and the cache key computed, so the cached
     * {@link OidcClient} is built from immutable values rather than from the live workflow context.
     */
    private record ResolvedConfig(CacheKey cacheKey, String authority, String tokenPath, String clientId,
            String clientSecret, Credentials.Secret.Method secretMethod, OAuth2AuthenticationDataGrant grant,
            List<String> scopes, List<String> audiences, String username, String password) {
    }
}
