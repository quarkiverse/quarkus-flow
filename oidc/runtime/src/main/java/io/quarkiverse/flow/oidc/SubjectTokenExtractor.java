package io.quarkiverse.flow.oidc;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.flow.oidc.config.FlowOidcConfig;
import io.quarkus.security.credential.TokenCredential;
import io.quarkus.security.identity.SecurityIdentity;
import io.serverlessworkflow.impl.WorkflowContext;

/**
 * Extracts the user's subject token for propagation/exchange.
 *
 * <p>
 * Priority order:
 * <ol>
 * <li><strong>Explicit workflow input:</strong> under {@code quarkus.flow.oidc.subject-token.input-key}
 * (useful for non-HTTP / programmatic triggers);</li>
 * <li><strong>{@code SecurityIdentity}:</strong> the
 * {@code quarkus.flow.oidc.subject-token.security-identity-attribute} attribute, for HTTP-triggered
 * workflows running on the request thread.</li>
 * </ol>
 * <p>
 * Inbound application security ({@code quarkus-oidc}) and outbound token acquisition
 * ({@code quarkus-oidc-client}) stay separate: this class only <em>reads</em> the inbound identity to obtain
 * a subject token, it never authenticates the caller.
 */
@ApplicationScoped
public class SubjectTokenExtractor {

    static final Logger log = LoggerFactory.getLogger(SubjectTokenExtractor.class);

    @Inject
    FlowOidcConfig config;

    @Inject
    Instance<SecurityIdentity> securityIdentity;

    public Optional<String> extract(AuthenticationContext context) {
        Optional<String> input = fromWorkflowInput(context.workflowContext());
        if (input.isPresent()) {
            log.debug("Flow OIDC: subject token resolved from workflow input");
            return input;
        }

        Optional<String> fromIdentity = fromSecurityIdentity();
        if (fromIdentity.isPresent()) {
            log.debug("Flow OIDC: subject token resolved from SecurityIdentity attribute '{}'",
                    config.subjectToken().securityIdentityAttribute());
            return fromIdentity;
        }

        return Optional.empty();
    }

    private Optional<String> fromWorkflowInput(WorkflowContext workflowContext) {
        String key = config.subjectToken().inputKey();
        return workflowContext.instance().input().asMap()
                .map(m -> m.get(key))
                .map(Object::toString)
                .filter(value -> !value.isBlank());
    }

    private Optional<String> fromSecurityIdentity() {
        try {
            // try to get from snapshot or from current CDI
            SecurityIdentity identity = propagatedIdentity().orElseGet(this::cdiIdentity);
            if (identity == null || identity.isAnonymous()) {
                return Optional.empty();
            }

            // try to get the token from SecurityIdentity's attribute given
            // the "quarkus.flow.oidc.subjectToken" configuration
            String attribute = config.subjectToken().securityIdentityAttribute();
            Object value = identity.getAttribute(attribute);
            if (value != null) {
                return Optional.of(value.toString());
            }

            TokenCredential credential = identity.getCredential(TokenCredential.class);
            if (credential != null) {
                return Optional.of(credential.getToken());
            }

            return Optional.empty();
        } catch (Exception e) {
            log.debug("Flow OIDC: SecurityIdentity not available on this thread: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<SecurityIdentity> propagatedIdentity() {
        PropagatedAuthContext.Snapshot snapshot = PropagatedAuthContext.current();
        return snapshot != null ? Optional.ofNullable(snapshot.identity()) : Optional.empty();
    }

    private SecurityIdentity cdiIdentity() {
        return securityIdentity.isResolvable() ? securityIdentity.get() : null;
    }
}
