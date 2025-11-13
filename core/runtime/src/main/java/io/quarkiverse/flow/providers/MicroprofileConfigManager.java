package io.quarkiverse.flow.providers;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.Config;

import io.serverlessworkflow.impl.config.ConfigManager;

/**
 * ConfigManager backed by MicroProfile Config.
 * Example:
 * <br/>
 * {@code mySecret.username=alice}
 * <br/>
 * {@code mySecret.password=s3cre3t!}
 */
@ApplicationScoped
public class MicroprofileConfigManager implements ConfigManager {

    @Inject
    Config config;

    @Override
    public <T> Optional<T> config(String propName, Class<T> propClass) {
        return config.getOptionalValue(propName, propClass);
    }

    @Override
    public Iterable<String> names() {
        return config.getPropertyNames();
    }

    @Override
    public int priority() {
        return 100;
    }
}
