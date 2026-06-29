package io.quarkiverse.flow.oidc;

import java.util.Optional;

/**
 * Strategy that resolves the bearer token to attach to a downstream HTTP request.
 *
 * <p>
 * Implementations are CDI beans discovered by {@link AuthenticationRegistry}. Each strategy declares the
 * {@link AuthenticationMode} it handles via {@link #supports(AuthenticationMode)}.
 */
public interface AuthenticationProvider {

    /**
     * Whether this provider handles the given mode.
     */
    boolean supports(AuthenticationMode mode);

    /**
     * Resolve the raw access token to attach (without the {@code Bearer } prefix), or empty if no token
     * could be produced. Implementations should not throw on a missing subject token; returning empty lets
     * the downstream service own the 401.
     */
    Optional<String> resolveToken(AuthenticationContext context);
}
