package io.quarkiverse.flow.oidc.deployment;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.flow.deployment.DiscoveredWorkflowBuildItem;
import io.quarkiverse.flow.oidc.FlowOidcAuthCustomizer;
import io.quarkiverse.flow.oidc.TokenAuthPolicy;
import io.quarkiverse.flow.oidc.TokenAuthPolicyExtractor;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.RunTimeConfigurationDefaultBuildItem;

public class FlowOidcProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(FlowOidcProcessor.class);
    private static final String FEATURE = "flow-oidc";

    // Config property prefixes and keys
    private static final String OIDC_CLIENT_PREFIX = "quarkus.oidc-client.";
    private static final String PROPERTY_ID = ".id";
    private static final String PROPERTY_DISCOVERY_ENABLED = ".discovery-enabled";
    private static final String PROPERTY_TOKEN_PATH = ".token-path";
    private static final String PROPERTY_REVOKE_PATH = ".revoke-path";
    private static final String PROPERTY_AUTH_SERVER_URL = ".auth-server-url";
    private static final String PROPERTY_CLIENT_ID = ".client-id";
    private static final String PROPERTY_CREDENTIALS_SECRET = ".credentials.secret";
    private static final String PROPERTY_GRANT_TYPE = ".grant.type";
    private static final String PROPERTY_SCOPES = ".scopes";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void registerBeans(BuildProducer<AdditionalBeanBuildItem> beans) {
        beans.produce(AdditionalBeanBuildItem.builder()
                .addBeanClass(FlowOidcAuthCustomizer.class)
                .setUnremovable()
                .build());

        // Register runtime beans by name (they're in runtime module, not available here at build time)
        beans.produce(AdditionalBeanBuildItem.builder()
                .addBeanClass("io.quarkiverse.flow.oidc.registry.OidcClientRegistry")
                .addBeanClass("io.quarkiverse.flow.oidc.registry.OidcClientWorkflowRegistrar")
                .addBeanClass("io.quarkiverse.flow.oidc.registry.OidcConfigResolver")
                .addBeanClass("io.quarkiverse.flow.oidc.impl.RuntimeExpressionResolver")
                .setUnremovable()
                .build());
    }

    @BuildStep
    void createOidcClientFromWorkflowDef(
            BuildProducer<RunTimeConfigurationDefaultBuildItem> runtimeConfig,
            List<DiscoveredWorkflowBuildItem> discoveredWorkflows) {

        for (DiscoveredWorkflowBuildItem discoveredWorkflow : discoveredWorkflows) {
            if (discoveredWorkflow.fromSpec()) {
                final List<TokenAuthPolicy> policies = TokenAuthPolicyExtractor
                        .extractStaticTokenAuthPolicies(discoveredWorkflow.workflowFromSpec());

                LOG.debug("Discovered {} token auth policies in workflow {}", policies.size(),
                        discoveredWorkflow.workflowDefinitionId());

                for (TokenAuthPolicy policy : policies) {
                    // Generate OIDC client config from workflow policy
                    // To override with your own pre-configured client, use routing config:
                    //   quarkus.flow.oidc.client.<policyName>.name=<yourClientName>
                    // See: https://docs.quarkiverse.io/quarkus-flow/dev/oauth2-oidc-authentication.html#route-to-named-client
                    if (policy.oauth2().isPresent()) {
                        generateOAuth2Config(runtimeConfig, policy);
                    } else if (policy.oidc().isPresent()) {
                        generateOidcConfig(runtimeConfig, policy);
                    }
                }
            }
        }
    }

    /**
     * See
     * <a href="https://quarkus.io/version/3.33/guides/security-openid-connect-client-reference#configuration-reference">OIDC
     * Client - Configuration reference</a>
     */
    private void generateOAuth2Config(BuildProducer<RunTimeConfigurationDefaultBuildItem> runtimeConfig,
            TokenAuthPolicy policy) {
        var oauth2Props = policy.oauth2().get();
        String prefix = getConfigPrefix(policy);

        LOG.debug("Generating OAuth2 config for client: {}", policy.name());

        produceConfig(runtimeConfig, prefix + PROPERTY_DISCOVERY_ENABLED, "false");

        if (oauth2Props.getEndpoints() != null && oauth2Props.getEndpoints().getToken() != null) {
            produceConfig(runtimeConfig, prefix + PROPERTY_TOKEN_PATH, oauth2Props.getEndpoints().getToken());
        }

        if (oauth2Props.getEndpoints() != null) {
            if (oauth2Props.getEndpoints().getRevocation() != null) {
                produceConfig(runtimeConfig, prefix + PROPERTY_REVOKE_PATH, oauth2Props.getEndpoints().getRevocation());
            }
        }

        generateCommonAuthConfig(runtimeConfig, policy);
    }

    /**
     * See
     * <a href="https://quarkus.io/version/3.33/guides/security-openid-connect-client-reference#configuration-reference">OIDC
     * Client - Configuration reference</a>
     */
    private void generateOidcConfig(BuildProducer<RunTimeConfigurationDefaultBuildItem> runtimeConfig,
            TokenAuthPolicy policy) {
        String prefix = getConfigPrefix(policy);

        LOG.debug("Generating OIDC config for client: {}", policy.name());

        produceConfig(runtimeConfig, prefix + PROPERTY_DISCOVERY_ENABLED, "true");

        generateCommonAuthConfig(runtimeConfig, policy);
    }

    /**
     * See
     * <a href="https://quarkus.io/version/3.33/guides/security-openid-connect-client-reference#configuration-reference">OIDC
     * Client - Configuration reference</a>
     */
    private void generateCommonAuthConfig(BuildProducer<RunTimeConfigurationDefaultBuildItem> runtimeConfig,
            TokenAuthPolicy policy) {
        String prefix = getConfigPrefix(policy);

        produceConfig(runtimeConfig, prefix + PROPERTY_ID, policy.name());

        var authData = policy.commonAuth();

        if (authData.getAuthority() != null && authData.getAuthority().getLiteralUri() != null) {
            produceConfig(runtimeConfig, prefix + PROPERTY_AUTH_SERVER_URL,
                    authData.getAuthority().getLiteralUri().toString());
        } else {
            throw new IllegalArgumentException("Authority (auth-server-url) is required for policy: " + policy.name());
        }

        if (authData.getClient() != null) {
            String clientId = authData.getClient().getId();
            // Use client name as fallback if client-id is not specified in the workflow
            if (clientId == null || clientId.isEmpty()) {
                clientId = policy.name();
            }
            produceConfig(runtimeConfig, prefix + PROPERTY_CLIENT_ID, clientId);

            if (authData.getClient().getSecret() != null) {
                produceConfig(runtimeConfig, prefix + PROPERTY_CREDENTIALS_SECRET, authData.getClient().getSecret());
            }
        } else {
            // If no client object is specified, use client name as client-id
            produceConfig(runtimeConfig, prefix + PROPERTY_CLIENT_ID, policy.name());
        }

        if (authData.getGrant() != null) {
            String grantType = mapGrantType(authData.getGrant().name());
            if (grantType.isEmpty()) {
                throw new IllegalArgumentException(
                        "Invalid grant type '" + authData.getGrant().name() + "' in policy: " + policy.name());
            }
            produceConfig(runtimeConfig, prefix + PROPERTY_GRANT_TYPE, grantType);
        }

        if (authData.getScopes() != null && !authData.getScopes().isEmpty()) {
            produceConfig(runtimeConfig, prefix + PROPERTY_SCOPES, String.join(",", authData.getScopes()));
        }
    }

    private String getConfigPrefix(TokenAuthPolicy policy) {
        return OIDC_CLIENT_PREFIX + policy.namePropertySafe();
    }

    private void produceConfig(BuildProducer<RunTimeConfigurationDefaultBuildItem> runtimeConfig,
            String key, String value) {
        LOG.debug("Generated OIDC client config: {} = {}", key, value);
        runtimeConfig.produce(new RunTimeConfigurationDefaultBuildItem(key, value));
    }

    /**
     * Maps Open Workflow Specification grant types to Quarkus OIDC client config property values.
     * Returns lowercase strings for build-time configuration properties.
     */
    private static String mapGrantType(String swfGrant) {
        return switch (swfGrant) {
            case "CLIENT_CREDENTIALS" -> "client";
            case "PASSWORD" -> "password";
            case "AUTHORIZATION_CODE" -> "code";
            case "REFRESH_TOKEN" -> "refresh";
            case "URN_IETF_PARAMS_OAUTH_GRANT_TYPE_TOKEN_EXCHANGE" -> "exchange";
            default -> throw new IllegalArgumentException("Unsupported grant type for build-time config: " + swfGrant);
        };
    }

}
