package io.quarkiverse.flow.runner.security;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.flow.runner.FlowRunnerConfig;
import io.quarkus.arc.Unremovable;
import io.quarkus.runtime.StartupEvent;

@ApplicationScoped
@Unremovable
public class SecurityConfigValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityConfigValidator.class.getName());

    @Inject
    FlowRunnerConfig config;

    void logSecurityMode(@Observes StartupEvent event) {
        switch (config.security().type()) {
            case NONE ->
                LOGGER.warn(
                        "Flow Runner: SECURITY DISABLED - All requests are granted all roles (flow-admin, flow-invoker). Use ONLY in development!");
            case API_KEY -> LOGGER.info("Flow Runner: API Key authentication enabled");
            case OIDC -> LOGGER.info("Flow Runner: OIDC authentication enabled");
        }
    }

}
