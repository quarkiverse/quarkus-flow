package io.quarkiverse.flow.oidc.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.oidc.AuthenticationMode;
import io.quarkiverse.flow.oidc.config.AuthConfigResolver;
import io.quarkiverse.flow.oidc.config.FlowOidcConfig;
import io.quarkiverse.flow.oidc.config.FlowOidcConfig.AuthSchemeConfig;
import io.quarkiverse.flow.oidc.config.FlowOidcConfig.TokenExchangeConfig;

class AuthConfigResolverTest {

    private static final boolean SUBJECT_TOKEN_AVAILABLE = true;
    private static final boolean NO_SUBJECT_TOKEN = false;

    private static AuthConfigResolver resolver(boolean globalExchangeEnabled, AuthSchemeConfig scheme) {
        TokenExchangeConfig exchange = mock(TokenExchangeConfig.class);
        lenient().when(exchange.enabled()).thenReturn(globalExchangeEnabled);

        FlowOidcConfig config = mock(FlowOidcConfig.class);
        when(config.tokenExchange()).thenReturn(exchange);
        when(config.auth()).thenReturn(scheme == null ? Map.of() : Map.of("svc", scheme));

        return new AuthConfigResolver(config);
    }

    private static AuthSchemeConfig scheme(Optional<Boolean> propagation, Optional<Boolean> exchange) {
        AuthSchemeConfig scheme = mock(AuthSchemeConfig.class);
        lenient().when(scheme.tokenPropagationEnabled()).thenReturn(propagation);
        lenient().when(scheme.tokenExchangeEnabled()).thenReturn(exchange);
        return scheme;
    }

    @Test
    @DisplayName("Propagation enabled should wins over exchange")
    void propagation_enabled_wins_over_exchange() {
        AuthConfigResolver resolver = resolver(true, scheme(Optional.of(true), Optional.of(true)));
        assertThat(resolver.resolveMode("svc", SUBJECT_TOKEN_AVAILABLE)).isEqualTo(AuthenticationMode.TOKEN_PROPAGATION);
    }

    @Test
    @DisplayName("Subject token present defaults to exchange")
    void subject_token_present_defaults_to_exchange() {
        AuthConfigResolver resolver = resolver(true, scheme(Optional.empty(), Optional.empty()));
        assertThat(resolver.resolveMode("svc", SUBJECT_TOKEN_AVAILABLE)).isEqualTo(AuthenticationMode.TOKEN_EXCHANGE);
    }

    @Test
    @DisplayName("No subject token defaults to client credentials")
    void no_subject_token_defaults_to_client_credentials() {
        AuthConfigResolver resolver = resolver(true, scheme(Optional.empty(), Optional.empty()));
        assertThat(resolver.resolveMode("svc", NO_SUBJECT_TOKEN)).isEqualTo(AuthenticationMode.CLIENT_CREDENTIALS);
    }

    @Test
    @DisplayName("Global exchange disabled falls back to client credentials")
    void global_exchange_disabled_falls_back_to_client_credentials() {
        AuthConfigResolver resolver = resolver(false, scheme(Optional.empty(), Optional.empty()));
        assertThat(resolver.resolveMode("svc", SUBJECT_TOKEN_AVAILABLE)).isEqualTo(AuthenticationMode.CLIENT_CREDENTIALS);
    }

    @Test
    @DisplayName("Scheme exchange flag overrides global disabled")
    void scheme_exchange_flag_overrides_global_disabled() {
        AuthConfigResolver resolver = resolver(false, scheme(Optional.empty(), Optional.of(true)));
        assertThat(resolver.resolveMode("svc", NO_SUBJECT_TOKEN)).isEqualTo(AuthenticationMode.TOKEN_EXCHANGE);
    }

    @Test
    @DisplayName("Scheme specific propagation overrides global exchange enabled")
    void scheme_specific_propagation_overrides_global_exchange_enabled() {
        // Global: exchange enabled, Scheme: propagation enabled
        AuthConfigResolver resolver = resolver(true, scheme(Optional.of(true), Optional.empty()));
        assertThat(resolver.resolveMode("svc", SUBJECT_TOKEN_AVAILABLE)).isEqualTo(AuthenticationMode.TOKEN_PROPAGATION);
    }

    @Test
    @DisplayName("Scheme specific exchange disabled overrides global enabled")
    void scheme_specific_exchange_disabled_overrides_global_enabled() {
        // Global: exchange enabled, Scheme: exchange explicitly disabled
        AuthConfigResolver resolver = resolver(true, scheme(Optional.empty(), Optional.of(false)));
        assertThat(resolver.resolveMode("svc", SUBJECT_TOKEN_AVAILABLE)).isEqualTo(AuthenticationMode.CLIENT_CREDENTIALS);
    }

    @Test
    @DisplayName("Global exchange enabled used when scheme not specified")
    void global_exchange_enabled_used_when_scheme_not_specified() {
        // Global: exchange enabled, Scheme: no specific config, subject token available
        AuthConfigResolver resolver = resolver(true, scheme(Optional.empty(), Optional.empty()));
        assertThat(resolver.resolveMode("svc", SUBJECT_TOKEN_AVAILABLE)).isEqualTo(AuthenticationMode.TOKEN_EXCHANGE);
    }

    @Test
    @DisplayName("Global exchange disabled used when scheme not specified")
    void global_exchange_disabled_used_when_scheme_not_specified() {
        // Global: exchange disabled, Scheme: no specific config
        AuthConfigResolver resolver = resolver(false, scheme(Optional.empty(), Optional.empty()));
        assertThat(resolver.resolveMode("svc", SUBJECT_TOKEN_AVAILABLE)).isEqualTo(AuthenticationMode.CLIENT_CREDENTIALS);
    }

    @Test
    @DisplayName("Precedence scheme propagation over scheme exchange")
    void precedence_scheme_propagation_over_scheme_exchange() {
        // Both propagation and exchange enabled at scheme level - propagation wins
        AuthConfigResolver resolver = resolver(true, scheme(Optional.of(true), Optional.of(true)));
        assertThat(resolver.resolveMode("svc", SUBJECT_TOKEN_AVAILABLE)).isEqualTo(AuthenticationMode.TOKEN_PROPAGATION);
    }

    @Test
    @DisplayName("Precedence scheme exchange over global and smart default")
    void precedence_scheme_exchange_over_global_and_smart_default() {
        // Global disabled, no subject token (would default to CLIENT_CREDENTIALS)
        // But scheme explicitly enables exchange - scheme wins
        AuthConfigResolver resolver = resolver(false, scheme(Optional.empty(), Optional.of(true)));
        assertThat(resolver.resolveMode("svc", NO_SUBJECT_TOKEN)).isEqualTo(AuthenticationMode.TOKEN_EXCHANGE);
    }

    @Test
    @DisplayName("Smart default with subject token when no config")
    void smart_default_with_subject_token_when_no_config() {
        // Global enabled, no scheme config, subject token available
        // Smart default: use exchange
        AuthConfigResolver resolver = resolver(true, scheme(Optional.empty(), Optional.empty()));
        assertThat(resolver.resolveMode("svc", SUBJECT_TOKEN_AVAILABLE)).isEqualTo(AuthenticationMode.TOKEN_EXCHANGE);
    }

    @Test
    @DisplayName("Smart default without subject token when no config")
    void smart_default_without_subject_token_when_no_config() {
        // Global enabled, no scheme config, no subject token
        // Smart default: service-to-service (CLIENT_CREDENTIALS)
        AuthConfigResolver resolver = resolver(true, scheme(Optional.empty(), Optional.empty()));
        assertThat(resolver.resolveMode("svc", NO_SUBJECT_TOKEN)).isEqualTo(AuthenticationMode.CLIENT_CREDENTIALS);
    }
}
