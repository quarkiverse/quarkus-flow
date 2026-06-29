package io.quarkiverse.flow.oidc;

import io.quarkiverse.flow.oidc.config.FlowOidcConfig;

public record SchemeContext(String name, FlowOidcConfig.AuthSchemeConfig config) {
}
