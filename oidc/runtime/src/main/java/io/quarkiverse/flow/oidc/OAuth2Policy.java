package io.quarkiverse.flow.oidc;

import java.util.Optional;

import io.serverlessworkflow.api.types.AuthenticationPolicyUnion;
import io.serverlessworkflow.api.types.OAuth2AuthenticationData;
import io.serverlessworkflow.api.types.OAuth2AuthenticationPolicy;
import io.serverlessworkflow.api.types.OpenIdConnectAuthenticationPolicy;

/**
 * A normalized view of an OAuth2 or OpenID Connect authentication policy declared in a workflow.
 *
 * <p>
 * It carries the inline {@link OAuth2AuthenticationData} together with a flag indicating whether the policy was declared as
 * OpenID Connect ({@code oidc(...)}), in which case OIDC discovery is used to locate the token endpoint instead of an explicit
 * token path.
 */
public final class OAuth2Policy {

    private final OAuth2AuthenticationData data;
    private final boolean openIdConnect;

    private OAuth2Policy(OAuth2AuthenticationData data, boolean openIdConnect) {
        this.data = data;
        this.openIdConnect = openIdConnect;
    }

    public OAuth2AuthenticationData data() {
        return data;
    }

    /**
     * @return {@code true} when the policy is an OpenID Connect policy (discovery based), {@code false} for plain OAuth2.
     */
    public boolean isOpenIdConnect() {
        return openIdConnect;
    }

    /**
     * Reads an OAuth2/OIDC policy from an authentication policy union.
     *
     * <p>
     * Only inline policies are handled here. Secret-based policies (and non OAuth2/OIDC schemes) return
     * {@link Optional#empty()} so the caller can delegate to the default SDK provider.
     */
    public static Optional<OAuth2Policy> from(AuthenticationPolicyUnion union) {
        if (union == null) {
            return Optional.empty();
        }

        OAuth2AuthenticationPolicy oauth2 = union.getOAuth2AuthenticationPolicy();
        if (oauth2 != null && oauth2.getOauth2() != null) {
            OAuth2AuthenticationData data = oauth2.getOauth2().getOAuth2ConnectAuthenticationProperties();
            return data == null ? Optional.empty() : Optional.of(new OAuth2Policy(data, false));
        }

        OpenIdConnectAuthenticationPolicy oidc = union.getOpenIdConnectAuthenticationPolicy();
        if (oidc != null && oidc.getOidc() != null) {
            OAuth2AuthenticationData data = oidc.getOidc().getOpenIdConnectAuthenticationProperties();
            return data == null ? Optional.empty() : Optional.of(new OAuth2Policy(data, true));
        }

        return Optional.empty();
    }
}
