package io.quarkiverse.flow.durable.kube;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.flow.durable.kube.config.DevModeConfig;
import io.quarkus.runtime.LaunchMode;

/**
 * Handles the strategy for dev mode or testing on user's space.
 * When enabled, means that the system will automatically skip querying the Kubernetes API in dev mode or testing.
 * <p/>
 * To override this behavior, user's can disable this strategy via configuration on
 * {@link DevModeConfig#devModeStrategyEnabled()}.
 */
@ApplicationScoped
public class DevModeStrategy {

    @Inject
    DevModeConfig devConfig;

    @Inject
    LaunchMode launchMode;

    /**
     * @return Whether we are on dev mode or user forced configuration to query Kubernetes in dev.
     */
    boolean enabled() {
        return launchMode.isDevOrTest() && devConfig.devModeStrategyEnabled();
    }

}
