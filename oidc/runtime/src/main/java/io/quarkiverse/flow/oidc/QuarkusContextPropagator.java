package io.quarkiverse.flow.oidc;

import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.arc.Unremovable;
import io.quarkus.security.identity.CurrentIdentityAssociation;
import io.quarkus.security.identity.SecurityIdentity;
import io.serverlessworkflow.impl.ContextPropagator;
import io.serverlessworkflow.impl.ContextSnapshot;

@ApplicationScoped
@Unremovable
public class QuarkusContextPropagator implements ContextPropagator {

    private static final Logger LOG = LoggerFactory.getLogger(QuarkusContextPropagator.class);

    @Inject
    Instance<CurrentIdentityAssociation> identityAssociation;

    @Override
    public ContextSnapshot capture() {
        // Runs on the thread that calls start(). CDI request scope is only available here, so resolve the
        // caller's identity and raw token now and carry them as plain values.
        ManagedContext requestContext = Arc.container().requestContext();
        if (!requestContext.isActive()) {
            return ContextSnapshot.NOOP;
        }
        SecurityIdentity identity = currentIdentity();

        if ((identity == null || identity.isAnonymous())) {
            return ContextSnapshot.NOOP;
        }
        LOG.debug("Flow OIDC: captured caller authentication to propagate to task execution (principal='{}')",
                identity.getPrincipal() != null ? identity.getPrincipal().getName() : "<none>");

        return new AuthSnapshot(new PropagatedAuthContext.Snapshot(identity));
    }

    private SecurityIdentity currentIdentity() {
        try {
            return identityAssociation.isResolvable() ? identityAssociation.get().getIdentity() : null;
        } catch (RuntimeException e) {
            LOG.debug("Flow OIDC: unable to read SecurityIdentity at start: {}", e.getMessage());
            return null;
        }
    }

    private record AuthSnapshot(PropagatedAuthContext.Snapshot snapshot) implements ContextSnapshot {

        @Override
        public <T> Supplier<T> wrap(Supplier<T> supplier) {
            return () -> {
                PropagatedAuthContext.set(snapshot);
                try {
                    return supplier.get();
                } finally {
                    PropagatedAuthContext.clear();
                }
            };
        }
    }
}
