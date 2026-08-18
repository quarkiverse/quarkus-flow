package io.quarkiverse.flow.runner.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.runner.FlowRunnerConfig;
import io.quarkus.security.identity.SecurityIdentity;
import io.vertx.ext.web.RoutingContext;

@DisplayName("Permit-all authentication mechanism tests")
class PermitAllAuthenticationMechanismTest {

    private PermitAllAuthenticationMechanism mechanism;
    private FlowRunnerConfig config;
    private FlowRunnerConfig.Security security;
    private RoutingContext routingContext;

    @BeforeEach
    void setUp() {
        mechanism = new PermitAllAuthenticationMechanism();

        config = mock(FlowRunnerConfig.class);
        security = mock(FlowRunnerConfig.Security.class);
        routingContext = mock(RoutingContext.class);

        mechanism.config = config;

        when(config.security()).thenReturn(security);
    }

    @Test
    @DisplayName("type_none_must_grant_admin_and_invoker_roles")
    void type_none_must_grant_admin_and_invoker_roles() {
        when(security.type()).thenReturn(FlowRunnerConfig.Security.Type.NONE);

        SecurityIdentity identity = mechanism.authenticate(routingContext, null)
                .await().indefinitely();

        assertThat(identity).isNotNull();
        assertThat(identity.isAnonymous()).isTrue();
        assertThat(identity.getPrincipal().getName()).isEqualTo(AuthzConsts.USER_ANONYMOUS);
        assertThat(identity.getRoles())
                .containsExactlyInAnyOrder(AuthzConsts.ROLE_ADMIN, AuthzConsts.ROLE_INVOKER);
    }

    @Test
    @DisplayName("type_api_key_must_not_activate_permit_all")
    void type_api_key_must_not_activate_permit_all() {
        when(security.type()).thenReturn(FlowRunnerConfig.Security.Type.API_KEY);

        SecurityIdentity identity = mechanism.authenticate(routingContext, null)
                .await().indefinitely();

        assertThat(identity)
                .as("API_KEY mode must not activate permit-all mechanism")
                .isNull();
    }

    @Test
    @DisplayName("type_oidc_must_not_activate_permit_all")
    void type_oidc_must_not_activate_permit_all() {
        when(security.type()).thenReturn(FlowRunnerConfig.Security.Type.OIDC);

        SecurityIdentity identity = mechanism.authenticate(routingContext, null)
                .await().indefinitely();

        assertThat(identity)
                .as("OIDC mode must not activate permit-all mechanism")
                .isNull();
    }
}
