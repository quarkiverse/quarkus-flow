package io.quarkiverse.flow.oidc;

import java.util.List;

import io.quarkus.oidc.common.runtime.config.OidcClientCommonConfig.Credentials;
import io.serverlessworkflow.api.types.OAuth2AuthenticationData.OAuth2AuthenticationDataGrant;

/**
 * Identifies an {@link io.quarkus.oidc.client.OidcClient} by every value that influences its configuration. Two policies
 * reuse a single client only when all fields match.
 */
record CacheKey(String authority, String tokenPath, boolean openIdConnect, String clientId,
        Credentials.Secret.Method secretMethod, OAuth2AuthenticationDataGrant grant,
        List<String> scopes, List<String> audiences, String clientSecret,
        String username, String password) {

    String configId() {
        return "flow-oidc-" + (clientId != null ? clientId : authority);
    }
}
