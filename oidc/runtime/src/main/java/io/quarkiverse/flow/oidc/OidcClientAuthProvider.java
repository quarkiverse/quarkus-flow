package io.quarkiverse.flow.oidc;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 * The workflow policy is mapped to an {@link OidcClientConfig} (client id/secret, grant type, authority/token endpoint,
 * scopes and audiences). Configuration overrides from {@code application.properties} are resolved by workflow+task
 * identity and applied on top of the DSL values. For the token-exchange grant the per-execution subject/actor tokens
 * are resolved from the workflow context and passed as dynamic grant parameters. The negotiated {@code access_token} is
 * returned and the SDK attaches it as {@code Authorization: Bearer <token>} to the downstream call.
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
        final OAuth2AuthenticationData data = policy.data();

        final WorkflowDefinitionId workflowId = workflow.definition().id();
        final String taskName = task.taskName();
        final OidcConfigResolver.ResolvedOverride override = configResolver.resolve(workflowId, taskName, authPolicyName);

        final ResolvedConfig resolved = resolveConfig(workflow, task, model, data, override);

        final OidcClient client = clientFactory.get(resolved.cacheKey, () -> buildConfig(resolved));

        final Map<String, String> dynamicParams = dynamicParams(workflow, task, model, data);
        try {
            final Tokens tokens = (dynamicParams.isEmpty() ? client.getTokens() : client.getTokens(dynamicParams))
                    .await().indefinitely();
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
            OAuth2AuthenticationData data, OidcConfigResolver.ResolvedOverride override) {
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
                clientSecret, username, password, override);
        return new ResolvedConfig(cacheKey, authority, tokenPath, clientId, clientSecret, secretMethod, grant, scopes,
                audiences, username, password, override);
    }

    private OidcClientConfig buildConfig(ResolvedConfig config) {
        final OidcConfigResolver.ResolvedOverride override = config.override();
        final OidcClientConfigBuilder builder = new OidcClientConfigBuilder().id(config.cacheKey.configId());

        final String effectiveAuthority = override.authServerUrl().orElse(config.authority());
        final String effectiveTokenPath = override.tokenPath().orElse(config.tokenPath());
        final boolean effectiveDiscovery = override.discoveryEnabled().orElse(false);

        builder.authServerUrl(effectiveAuthority)
                .discoveryEnabled(effectiveDiscovery)
                .tokenPath(effectiveTokenPath);

        final String effectiveClientId = override.clientId().orElse(config.clientId());
        if (effectiveClientId != null) {
            builder.clientId(effectiveClientId);
        }

        final String effectiveClientSecret = override.clientSecret().orElse(config.clientSecret());
        if (effectiveClientSecret != null) {
            Credentials.Secret.Method effectiveMethod = config.secretMethod();
            if (override.clientSecretMethod().isPresent()) {
                effectiveMethod = parseSecretMethod(override.clientSecretMethod().get());
            }
            if (effectiveMethod == null) {
                effectiveMethod = Credentials.Secret.Method.POST;
            }
            builder.credentials().clientSecret(effectiveClientSecret, effectiveMethod).end();
        }

        final OidcClientConfig.Grant.Type effectiveGrantType;
        if (override.grantType().isPresent()) {
            effectiveGrantType = parseGrantType(override.grantType().get());
        } else {
            effectiveGrantType = grantType(config.grant());
        }
        builder.grant(effectiveGrantType);

        final List<String> effectiveScopes = override.scopes().orElse(config.scopes());
        if (effectiveScopes != null && !effectiveScopes.isEmpty()) {
            builder.scopes(effectiveScopes);
        }

        final List<String> effectiveAudience = override.audience().orElse(config.audiences());
        if (effectiveAudience != null && !effectiveAudience.isEmpty()) {
            builder.audience(effectiveAudience);
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

        override.accessTokenExpiresIn().ifPresent(builder::accessTokenExpiresIn);
        override.accessTokenExpirySkew().ifPresent(builder::accessTokenExpirySkew);
        override.refreshTokenTimeSkew().ifPresent(builder::refreshTokenTimeSkew);
        override.absoluteExpiresIn().ifPresent(builder::absoluteExpiresIn);
        override.earlyTokensAcquisition().ifPresent(builder::earlyTokensAcquisition);
        override.refreshInterval().ifPresent(builder::refreshInterval);

        if (!override.headers().isEmpty()) {
            builder.headers(override.headers());
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
        return value;
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

    static Credentials.Secret.Method parseSecretMethod(String value) {
        return switch (value.toUpperCase()) {
            case "BASIC" -> Credentials.Secret.Method.BASIC;
            case "POST_JWT" -> Credentials.Secret.Method.POST_JWT;
            case "QUERY" -> Credentials.Secret.Method.QUERY;
            default -> Credentials.Secret.Method.POST;
        };
    }

    static OidcClientConfig.Grant.Type parseGrantType(String value) {
        return switch (value) {
            case "client-credentials", "client_credentials" -> OidcClientConfig.Grant.Type.CLIENT;
            case "password" -> OidcClientConfig.Grant.Type.PASSWORD;
            case "refresh-token", "refresh_token" -> OidcClientConfig.Grant.Type.REFRESH;
            case "authorization-code", "authorization_code" -> OidcClientConfig.Grant.Type.CODE;
            case "urn:ietf:params:oauth:grant-type:token-exchange", "exchange" -> OidcClientConfig.Grant.Type.EXCHANGE;
            default -> throw new IllegalStateException(
                    "Flow OIDC: unsupported grant type '" + value + "'; supported values are "
                            + "authorization_code, client_credentials, password, refresh_token "
                            + "and urn:ietf:params:oauth:grant-type:token-exchange");
        };
    }

    /**
     * The fully resolved view of a policy: every expression has been evaluated and the cache key computed, so the cached
     * {@link OidcClient} is built from immutable values rather than from the live workflow context.
     */
    private record ResolvedConfig(CacheKey cacheKey, String authority, String tokenPath, String clientId,
            String clientSecret, Credentials.Secret.Method secretMethod, OAuth2AuthenticationDataGrant grant,
            List<String> scopes, List<String> audiences, String username, String password,
            OidcConfigResolver.ResolvedOverride override) {
    }
}