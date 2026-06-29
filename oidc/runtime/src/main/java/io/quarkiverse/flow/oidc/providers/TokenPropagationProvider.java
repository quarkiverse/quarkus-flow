package io.quarkiverse.flow.oidc.providers;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.oidc.AuthenticationContext;
import io.quarkiverse.flow.oidc.AuthenticationMode;
import io.quarkiverse.flow.oidc.AuthenticationProvider;

@ApplicationScoped
public class TokenPropagationProvider implements AuthenticationProvider {

    @Override
    public boolean supports(AuthenticationMode mode) {
        return mode == AuthenticationMode.TOKEN_PROPAGATION;
    }

    @Override
    public Optional<String> resolveToken(AuthenticationContext context) {
        return context.subjectToken();
    }
}
