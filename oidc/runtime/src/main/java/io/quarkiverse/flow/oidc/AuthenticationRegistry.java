package io.quarkiverse.flow.oidc;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.flow.oidc.config.AuthConfigResolver;

/**
 * Resolves the bearer token for a downstream call. The scheme carries no explicit mode, so the registry
 * derives it via {@link AuthConfigResolver} (using whether a subject token is available) and routes to the
 * {@link AuthenticationProvider} that supports the derived {@link AuthenticationMode}.
 */
@ApplicationScoped
public class AuthenticationRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(AuthenticationRegistry.class);

    @Inject
    Instance<AuthenticationProvider> providers;

    @Inject
    AuthConfigResolver authConfigResolver;

    @Inject
    SubjectTokenExtractor subjectTokenExtractor;

    public Optional<String> authenticate(AuthenticationContext context) {
        // Extract the subject token exactly once: it drives mode resolution and is read by the providers.
        String subjectToken = subjectTokenExtractor.extract(context).orElse(null);
        AuthenticationMode mode = authConfigResolver.resolveMode(context.schemeName(), subjectToken != null);
        AuthenticationContext enriched = context.withSubjectToken(subjectToken);
        return providers.stream()
                .filter(p -> p.supports(mode))
                .findFirst()
                .flatMap(p -> {
                    try {
                        return p.resolveToken(enriched);
                    } catch (RuntimeException e) {
                        LOG.warn("Flow OIDC: provider {} failed to resolve token for scheme '{}': {}",
                                p.getClass().getName(), context.schemeName(), e.getMessage());
                        throw e;
                    }
                });
    }
}
