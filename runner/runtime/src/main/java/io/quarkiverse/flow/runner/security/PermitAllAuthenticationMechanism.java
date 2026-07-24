package io.quarkiverse.flow.runner.security;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;

import io.quarkiverse.flow.runner.FlowRunnerConfig;
import io.quarkus.arc.Unremovable;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;

@Priority(Priorities.AUTHENTICATION)
@ApplicationScoped
@Unremovable
public class PermitAllAuthenticationMechanism implements HttpAuthenticationMechanism {

    @Inject
    FlowRunnerConfig config;

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext context, IdentityProviderManager identityProviderManager) {
        if (config.security().type() != FlowRunnerConfig.Security.Type.NONE) {
            return Uni.createFrom().nullItem();
        }

        SecurityIdentity identity = QuarkusSecurityIdentity.builder()
                .setPrincipal(new QuarkusPrincipal(AuthzConsts.USER_ANONYMOUS))
                .addRole(AuthzConsts.ROLE_ADMIN)
                .addRole(AuthzConsts.ROLE_INVOKER)
                .setAnonymous(true)
                .build();

        return Uni.createFrom().item(identity);
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context) {
        return Uni.createFrom().nullItem();
    }
}
