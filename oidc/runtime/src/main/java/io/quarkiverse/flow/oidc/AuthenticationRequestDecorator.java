package io.quarkiverse.flow.oidc;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import jakarta.ws.rs.client.Invocation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.flow.oidc.config.FlowOidcConfig;
import io.quarkiverse.flow.oidc.config.FlowOidcConfig.AuthSchemeConfig;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import io.serverlessworkflow.api.types.CallHTTP;
import io.serverlessworkflow.api.types.CallOpenAPI;
import io.serverlessworkflow.api.types.Endpoint;
import io.serverlessworkflow.api.types.ReferenceableAuthenticationPolicy;
import io.serverlessworkflow.impl.TaskContext;
import io.serverlessworkflow.impl.WorkflowContext;
import io.serverlessworkflow.impl.executors.http.HttpRequestDecorator;

/**
 * Attaches an {@code Authorization} header to downstream HTTP requests based on the
 * {@code quarkus.flow.oidc.auth.*} configuration.
 */
public class AuthenticationRequestDecorator implements HttpRequestDecorator {

    public static final int PRIORITY = 100;

    private static final Logger LOG = LoggerFactory.getLogger(AuthenticationRequestDecorator.class);
    private final Optional<FlowOidcConfig> flowOidcConfig;
    private final Optional<AuthenticationRegistry> authenticationRegistry;
    private final Optional<SubjectTokenExtractor> subjectTokenExtractor;

    public AuthenticationRequestDecorator() {
        this.flowOidcConfig = lookup(FlowOidcConfig.class);
        this.authenticationRegistry = lookup(AuthenticationRegistry.class);
        this.subjectTokenExtractor = lookup(SubjectTokenExtractor.class);
    }

    @Override
    public void decorate(Invocation.Builder request, WorkflowContext workflowContext, TaskContext taskContext) {
        if (flowOidcConfig.isEmpty()) {
            return;
        }
        FlowOidcConfig config = flowOidcConfig.get();

        Optional<Entry<String, AuthSchemeConfig>> scheme = selectScheme(config, taskContext);
        if (scheme.isEmpty()) {
            return;
        }
        if (authenticationRegistry.isEmpty() || subjectTokenExtractor.isEmpty()) {
            return;
        }

        AuthenticationContext ctx = new AuthenticationContext(
                scheme.get().getKey(), scheme.get().getValue(), workflowContext, taskContext);

        authenticationRegistry.get().authenticate(ctx)
                .filter(token -> !token.isBlank())
                .ifPresent(token -> request.header("Authorization", "Bearer " + token));
    }

    /**
     * Select the scheme the task itself declares: read the named-authentication reference from the HTTP
     * endpoint ({@code FuncDSL.use("<scheme>")}) and match it against {@code quarkus.flow.oidc.auth.<scheme>}.
     * Returns empty when the task declares no reference or no matching scheme is configured.
     */
    static Optional<Entry<String, AuthSchemeConfig>> selectScheme(
            FlowOidcConfig config, TaskContext taskContext) {
        Optional<String> schemeName = referencedSchemeName(taskContext);
        return schemeName
                .filter(name -> config.auth().containsKey(name))
                .map(name -> Map.entry(name, config.auth().get(name)));
    }

    /**
     * Extract the {@code use:} authentication-policy reference from an HTTP call task's endpoint, if any.
     */
    static Optional<String> referencedSchemeName(TaskContext taskContext) {
        try {

            if (!(taskContext.task() instanceof CallHTTP) && !(taskContext.task() instanceof CallOpenAPI)) {
                return Optional.empty();
            }

            if (taskContext.task() instanceof CallHTTP http) {
                if (http.getWith() == null) {
                    return Optional.empty();
                }
                Endpoint endpoint = http.getWith().getEndpoint();
                if (endpoint == null || endpoint.getEndpointConfiguration() == null) {
                    return Optional.empty();
                }
                ReferenceableAuthenticationPolicy auth = endpoint.getEndpointConfiguration().getAuthentication();
                return getSchemeName(auth);
            }

            if (taskContext.task() instanceof CallOpenAPI openapi) {
                if (openapi.getWith() == null) {
                    return Optional.empty();
                }
                ReferenceableAuthenticationPolicy authentication = openapi.getWith().getAuthentication();
                if (authentication == null || authentication.getAuthenticationPolicyReference() == null) {
                    return Optional.empty();
                }
                return getSchemeName(authentication);
            }
            return Optional.empty();

        } catch (RuntimeException e) {
            LOG.debug("Flow OIDC: unable to read authentication reference from task: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static Optional<String> getSchemeName(ReferenceableAuthenticationPolicy auth) {
        if (auth == null || auth.getAuthenticationPolicyReference() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(auth.getAuthenticationPolicyReference().getUse())
                .filter(s -> !s.isBlank());
    }

    private static <T> Optional<T> lookup(Class<T> type) {
        if (!Arc.container().isRunning()) {
            return Optional.empty();
        }
        try (InstanceHandle<T> handle = Arc.container().instance(type)) {
            return handle.isAvailable() ? Optional.ofNullable(handle.get()) : Optional.empty();
        } catch (RuntimeException e) {
            LOG.debug("Flow OIDC: unable to resolve {} from Arc: {}", type.getName(), e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public int priority() {
        return PRIORITY;
    }
}
