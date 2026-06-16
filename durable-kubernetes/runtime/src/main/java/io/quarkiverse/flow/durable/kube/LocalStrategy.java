package io.quarkiverse.flow.durable.kube;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.flow.durable.kube.config.LocalConfig;
import io.quarkus.runtime.LaunchMode;

/**
 * Handles the strategy for dev mode, testing or local execution on user's space.
 * When enabled, means that the system will automatically skip querying the Kubernetes API.
 * <p/>
 * To override this behavior, user's can disable this strategy via configuration on
 * {@link LocalConfig#localStrategyEnabled()}.
 */
@ApplicationScoped
public class LocalStrategy {

    @Inject
    LocalConfig devConfig;
    @Inject
    LaunchMode launchMode;

    /**
     * @return Whether we are on dev mode or user forced configuration to query Kubernetes in dev.
     */
    boolean enabled() {
        return (launchMode.isDevOrTest() || !KubernetesAwareness.isRunningInKubernetes()) &&
                devConfig.localStrategyEnabled();
    }

}
