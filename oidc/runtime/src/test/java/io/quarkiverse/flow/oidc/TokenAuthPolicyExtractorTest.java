package io.quarkiverse.flow.oidc;

import static io.serverlessworkflow.api.types.OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.serverlessworkflow.api.types.OAuth2AuthenticationData;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;
import io.serverlessworkflow.fluent.func.dsl.FuncDSL;

class TokenAuthPolicyExtractorTest {

    @Test
    void extract_named_oauth2_policy() {
        Workflow workflow = FuncWorkflowBuilder.workflow("orders")
                .use(use -> use.authentications(auth -> auth.authentication("keycloak", r -> r.oauth2(
                        oauth2 -> oauth2.authority("https://auth.example.com")
                                .grant(CLIENT_CREDENTIALS)))))
                .build();

        List<TokenAuthPolicy> result = TokenAuthPolicyExtractor.extractStaticTokenAuthPolicies(workflow);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("keycloak");
        assertThat(result.get(0).oauth2()).isPresent();
    }

    @Test
    void extract_named_oidc_policy() {
        Workflow workflow = FuncWorkflowBuilder.workflow("orders")
                .use(use -> use.authentications(auth -> auth.authentication("auth-server", r -> r.oauth2(
                        oauth2 -> oauth2.authority("https://oidc.example.com")
                                .grant(CLIENT_CREDENTIALS)))))
                .build();

        List<TokenAuthPolicy> result = TokenAuthPolicyExtractor.extractStaticTokenAuthPolicies(workflow);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("auth-server");
        assertThat(result.get(0).oauth2()).isPresent();
    }

    @Test
    void extract_inline_http_oauth2() {
        Workflow workflow = FuncWorkflowBuilder.workflow("orders")
                .tasks(FuncDSL.call(
                        FuncDSL.http("payment")
                                .POST()
                                .uri(URI.create("https://api.example.com/payment"),
                                        FuncDSL.oauth2("https://auth.example.com", CLIENT_CREDENTIALS, "client", "secret",
                                                e -> e.token("/oauth2/token")))))
                .build();

        List<TokenAuthPolicy> result = TokenAuthPolicyExtractor.extractStaticTokenAuthPolicies(workflow);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).startsWith("org-acme:orders:0.0.1.task.");
        assertThat(result.get(0).oauth2()).isPresent();
    }

    @Test
    void extract_inline_http_oidc() {
        Workflow workflow = FuncWorkflowBuilder.workflow("orders")
                .tasks(FuncDSL.call(
                        FuncDSL.http("payment")
                                .POST()
                                .uri(URI.create("https://api.example.com/payment"),
                                        FuncDSL.oidc("https://oidc.example.com", CLIENT_CREDENTIALS, "client", "secret"))))
                .build();

        List<TokenAuthPolicy> result = TokenAuthPolicyExtractor.extractStaticTokenAuthPolicies(workflow);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).startsWith("org-acme:orders:0.0.1.task.");
        assertThat(result.get(0).oidc()).isPresent();
    }

    @Test
    void extract_http_with_named_policy() {
        Workflow workflow = FuncWorkflowBuilder.workflow("orders")
                .use(use -> use.authentications(auth -> auth.authentication("keycloak", r -> r.oauth2(
                        oauth2 -> oauth2.authority("https://auth.example.com")
                                .grant(CLIENT_CREDENTIALS)))))
                .tasks(FuncDSL.call(
                        FuncDSL.http("payment")
                                .GET()
                                .uri(URI.create("https://api.example.com"), FuncDSL.use("keycloak"))))
                .build();

        List<TokenAuthPolicy> result = TokenAuthPolicyExtractor.extractStaticTokenAuthPolicies(workflow);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("keycloak");
    }

    @Test
    void extract_multiple_policies() {
        Workflow workflow = FuncWorkflowBuilder.workflow("orders")
                .use(use -> use.authentications(auth -> auth
                        .authentication("keycloak", r -> r.oauth2(
                                oauth2 -> oauth2.authority("https://auth.example.com")
                                        .grant(CLIENT_CREDENTIALS)))
                        .authentication("auth0", r -> r.oauth2(
                                oauth2 -> oauth2.authority("https://auth0.example.com")
                                        .grant(CLIENT_CREDENTIALS)))))
                .tasks(
                        FuncDSL.call(FuncDSL.http("payment")
                                .uri(URI.create("https://api1.example.com"), FuncDSL.use("keycloak"))),
                        FuncDSL.call(FuncDSL.http("shipping")
                                .uri(URI.create("https://api2.example.com"), FuncDSL.use("auth0"))))
                .build();

        List<TokenAuthPolicy> result = TokenAuthPolicyExtractor.extractStaticTokenAuthPolicies(workflow);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(TokenAuthPolicy::name).containsExactlyInAnyOrder("keycloak", "auth0");
    }

    @Test
    void extract_empty_workflow() {
        Workflow workflow = FuncWorkflowBuilder.workflow("empty").build();

        List<TokenAuthPolicy> result = TokenAuthPolicyExtractor.extractStaticTokenAuthPolicies(workflow);

        assertThat(result).isEmpty();
    }

    @Test
    void extract_workflow_without_token_auth() {
        Workflow workflow = FuncWorkflowBuilder.workflow("basic-auth")
                .use(use -> use.authentications(auth -> auth.authentication("basic", r -> r.basic(
                        basic -> basic.username("user").password("pass")))))
                .tasks(FuncDSL.call(
                        FuncDSL.http("api")
                                .uri(URI.create("https://api.example.com"), FuncDSL.use("basic"))))
                .build();

        List<TokenAuthPolicy> result = TokenAuthPolicyExtractor.extractStaticTokenAuthPolicies(workflow);

        assertThat(result).isEmpty();
    }

    @Test
    void skip_named_policy_with_expression_in_username() {
        Workflow workflow = FuncWorkflowBuilder.workflow("orders")
                .use(use -> use.authentications(auth -> auth.authentication("dynamic-auth", r -> r.oauth2(
                        oauth2 -> oauth2.authority("https://auth.example.com")
                                .grant(OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.PASSWORD)
                                .username("${ $secret.username }")
                                .password("${ $secret.password }")))))
                .build();

        List<TokenAuthPolicy> result = TokenAuthPolicyExtractor.extractStaticTokenAuthPolicies(workflow);

        assertThat(result).isEmpty();
    }

    @Test
    void skip_named_policy_with_expression_in_client_id() {
        Workflow workflow = FuncWorkflowBuilder.workflow("orders")
                .use(use -> use.authentications(auth -> auth.authentication("dynamic-auth", r -> r.oauth2(
                        oauth2 -> oauth2.authority("https://auth.example.com")
                                .grant(CLIENT_CREDENTIALS)
                                .client(c -> c.id("${ $secret.client_id }").secret("client-secret"))))))
                .build();

        List<TokenAuthPolicy> result = TokenAuthPolicyExtractor.extractStaticTokenAuthPolicies(workflow);

        assertThat(result).isEmpty();
    }

    @Test
    void skip_named_policy_with_expression_in_client_secret() {
        Workflow workflow = FuncWorkflowBuilder.workflow("orders")
                .use(use -> use.authentications(auth -> auth.authentication("dynamic-auth", r -> r.oauth2(
                        oauth2 -> oauth2.authority("https://auth.example.com")
                                .grant(CLIENT_CREDENTIALS)
                                .client(c -> c.id("client-id").secret("${ $secret.client_secret }"))))))
                .build();

        List<TokenAuthPolicy> result = TokenAuthPolicyExtractor.extractStaticTokenAuthPolicies(workflow);

        assertThat(result).isEmpty();
    }

    @Test
    void skip_inline_oauth2_with_expression_in_authority() {
        Workflow workflow = FuncWorkflowBuilder.workflow("orders")
                .tasks(FuncDSL.call(
                        FuncDSL.http("payment")
                                .POST()
                                .uri(URI.create("https://api.example.com/payment"),
                                        FuncDSL.oauth2("https://auth.example.com", CLIENT_CREDENTIALS, "${ $secret.client_id }",
                                                "secret",
                                                e -> e.token("/oauth2/token")))))
                .build();

        List<TokenAuthPolicy> result = TokenAuthPolicyExtractor.extractStaticTokenAuthPolicies(workflow);

        assertThat(result).isEmpty();
    }

    @Test
    void skip_inline_oauth2_with_expression_in_client_credentials() {
        Workflow workflow = FuncWorkflowBuilder.workflow("orders")
                .tasks(FuncDSL.call(
                        FuncDSL.http("payment")
                                .POST()
                                .uri(URI.create("https://api.example.com/payment"),
                                        FuncDSL.oauth2("https://auth.example.com", CLIENT_CREDENTIALS, "${ $secret.client_id }",
                                                "${ $secret.client_secret }",
                                                e -> e.token("/oauth2/token")))))
                .build();

        List<TokenAuthPolicy> result = TokenAuthPolicyExtractor.extractStaticTokenAuthPolicies(workflow);

        assertThat(result).isEmpty();
    }

    @Test
    void extract_mixed_static_and_dynamic_policies() {
        Workflow workflow = FuncWorkflowBuilder.workflow("orders")
                .use(use -> use.authentications(auth -> auth
                        .authentication("static-auth", r -> r.oauth2(
                                oauth2 -> oauth2.authority("https://auth.example.com")
                                        .grant(CLIENT_CREDENTIALS)
                                        .client(c -> c.id("static-client").secret("static-secret"))))
                        .authentication("dynamic-auth", r -> r.oauth2(
                                oauth2 -> oauth2.authority("https://auth.example.com")
                                        .grant(CLIENT_CREDENTIALS)
                                        .client(c -> c.id("${ $secret.client_id }").secret("${ $secret.client_secret }"))))))
                .build();

        List<TokenAuthPolicy> result = TokenAuthPolicyExtractor.extractStaticTokenAuthPolicies(workflow);

        // Only the static policy should be extracted
        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("static-auth");
    }
}
