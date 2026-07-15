package io.quarkiverse.flow.oidc;

import static io.serverlessworkflow.api.types.OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.junit.jupiter.api.Test;

import io.serverlessworkflow.api.types.AuthenticationPolicyUnion;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;
import io.serverlessworkflow.fluent.func.dsl.FuncDSL;

class TokenAuthPolicyTest {

    @Test
    void from_named_oauth2_auth_creates_oauth2() {
        // Named auth using r.oauth2() - OAuth2 policy without explicit endpoints (uses well-known URLs)
        Workflow workflow = FuncWorkflowBuilder.workflow("test")
                .use(use -> use.authentications(auth -> auth.authentication("keycloak", r -> r.oauth2(
                        oauth2 -> oauth2.authority("https://auth.example.com")
                                .grant(CLIENT_CREDENTIALS)))))
                .build();

        AuthenticationPolicyUnion union = workflow.getUse()
                .getAuthentications()
                .getAdditionalProperties()
                .get("keycloak");

        var result = TokenAuthPolicy.from("keycloak", union);

        assertThat(result).isPresent();
        assertThat(result.get().name()).isEqualTo("keycloak");
        assertThat(result.get().oauth2()).isPresent();
        assertThat(result.get().oidc()).isEmpty();
    }

    @Test
    void from_inline_oidc_creates_oidc() {
        // Inline OIDC using FuncDSL.oidc() - OpenIdConnectAuthenticationPolicy with discovery
        Workflow workflow = FuncWorkflowBuilder.workflow("test")
                .tasks(FuncDSL.call(
                        FuncDSL.http("api")
                                .uri(URI.create("https://api.example.com"),
                                        FuncDSL.oidc("https://oidc.example.com", CLIENT_CREDENTIALS, "client", "secret"))))
                .build();

        var endpoint = workflow.getDo().get(0)
                .getTask().getCallTask().getCallHTTP()
                .getWith().getEndpoint()
                .getEndpointConfiguration();

        var result = TokenAuthPolicy.from("test", endpoint);

        assertThat(result).isPresent();
        assertThat(result.get().oidc()).isPresent();
        assertThat(result.get().oauth2()).isEmpty();
    }

    @Test
    void from_basic_auth_returns_empty() {
        Workflow workflow = FuncWorkflowBuilder.workflow("test")
                .use(use -> use.authentications(auth -> auth.authentication("basic", r -> r.basic(
                        basic -> basic.username("user").password("pass")))))
                .build();

        AuthenticationPolicyUnion union = workflow.getUse()
                .getAuthentications()
                .getAdditionalProperties()
                .get("basic");

        var result = TokenAuthPolicy.from("basic", union);

        assertThat(result).isEmpty();
    }

    @Test
    void from_inline_oauth2_with_explicit_endpoint_creates_oauth2() {
        // Inline OAuth2 using FuncDSL.oauth2() with explicit token endpoint
        Workflow workflow = FuncWorkflowBuilder.workflow("test")
                .tasks(FuncDSL.call(
                        FuncDSL.http("api")
                                .uri(URI.create("https://api.example.com"),
                                        FuncDSL.oauth2("https://auth.example.com", CLIENT_CREDENTIALS, "client", "secret",
                                                e -> e.token("/oauth2/token")))))
                .build();

        var endpoint = workflow.getDo().get(0)
                .getTask().getCallTask().getCallHTTP()
                .getWith().getEndpoint()
                .getEndpointConfiguration();

        var result = TokenAuthPolicy.from("test", endpoint);

        assertThat(result).isPresent();
        assertThat(result.get().oauth2()).isPresent();
        assertThat(result.get().oidc()).isEmpty();
    }

    @Test
    void from_null_union_throws() {
        assertThat(TokenAuthPolicy.from("test", (AuthenticationPolicyUnion) null)).isEmpty();
    }
}
