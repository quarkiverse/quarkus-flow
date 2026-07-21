package io.quarkiverse.flow.dsl;

import static io.quarkiverse.flow.dsl.FlowDSL.call;
import static io.quarkiverse.flow.dsl.FlowDSL.http;
import static io.quarkiverse.flow.dsl.FlowDSL.oauth2;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.serverlessworkflow.api.types.OAuth2AuthenticationData;
import io.serverlessworkflow.api.types.OAuth2AuthenticationDataClient;
import io.serverlessworkflow.api.types.OAuth2ConnectAuthenticationProperties;
import io.serverlessworkflow.api.types.Workflow;

class FlowDSLOAuth2Test {

    private static final String EXPR_ENDPOINT = "${ .endpoint }";

    private static OAuth2ConnectAuthenticationProperties oauth2PropertiesOf(Workflow wf) {
        var auth = wf.getDo()
                .get(0)
                .getTask()
                .getCallTask()
                .getCallHTTP()
                .getWith()
                .getEndpoint()
                .getEndpointConfiguration()
                .getAuthentication()
                .getAuthenticationPolicy();
        assertNotNull(auth.getOAuth2AuthenticationPolicy());
        return auth.getOAuth2AuthenticationPolicy()
                .getOauth2()
                .getOAuth2ConnectAuthenticationProperties();
    }

    @Test
    void convenience_overload_sets_token_endpoint() {
        Workflow wf = FlowWorkflowBuilder.workflow("oauth2-token")
                .tasks(
                        call(
                                http()
                                        .post()
                                        .endpoint(
                                                EXPR_ENDPOINT,
                                                oauth2(
                                                        "https://auth.example.com/",
                                                        OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS,
                                                        "client-id",
                                                        "client-secret",
                                                        e -> e.token("/custom/token")))))
                .build();

        var props = oauth2PropertiesOf(wf);
        assertEquals(URI.create("https://auth.example.com/"), props.getAuthority().getLiteralUri());
        assertEquals(
                OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS,
                props.getGrant());
        assertEquals("client-id", props.getClient().getId());
        assertEquals("client-secret", props.getClient().getSecret());
        assertEquals("/custom/token", props.getEndpoints().getToken());
    }

    @Test
    void builder_overload_supports_full_oauth2_section() {
        Workflow wf = FlowWorkflowBuilder.workflow("oauth2-full")
                .tasks(
                        call(
                                http()
                                        .get()
                                        .endpoint(
                                                EXPR_ENDPOINT,
                                                oauth2(
                                                        o -> o.endpoints(
                                                                e -> e.token("/oauth2/token")
                                                                        .revocation("/oauth2/revoke")
                                                                        .introspection("/oauth2/introspect"))
                                                                .authority("https://auth.example.com/")
                                                                .grant(
                                                                        OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS)
                                                                .scopes("read", "write")
                                                                .audiences("api://default")
                                                                .client(
                                                                        c -> c.id("client-id")
                                                                                .secret("client-secret")
                                                                                .authentication(
                                                                                        OAuth2AuthenticationDataClient.ClientAuthentication.CLIENT_SECRET_BASIC))))))
                .build();

        var props = oauth2PropertiesOf(wf);
        assertEquals(URI.create("https://auth.example.com/"), props.getAuthority().getLiteralUri());
        assertEquals(
                OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS,
                props.getGrant());
        assertEquals(List.of("read", "write"), props.getScopes());
        assertEquals(List.of("api://default"), props.getAudiences());
        assertEquals("client-id", props.getClient().getId());
        assertEquals(
                OAuth2AuthenticationDataClient.ClientAuthentication.CLIENT_SECRET_BASIC,
                props.getClient().getAuthentication());
        assertEquals("/oauth2/token", props.getEndpoints().getToken());
        assertEquals("/oauth2/revoke", props.getEndpoints().getRevocation());
        assertEquals("/oauth2/introspect", props.getEndpoints().getIntrospection());
    }
}
